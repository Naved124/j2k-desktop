package dev.naved.j2kdesktop.source

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds

class MangaDex : HttpSource() {
    override val name = "MangaDex"
    override val lang = "en"
    override val baseUrl = "https://mangadex.org"
    override val supportsLatest = true

    // Different id from the Keiyoushi MangaDex extension, so both can be listed side by side
    override val versionId = 101

    private val apiUrl = "https://api.mangadex.org"
    private val json = Json { ignoreUnknownKeys = true }

    // MangaDex allows ~5 requests/second; stay well under it.
    override val client: OkHttpClient by lazy {
        network.client.newBuilder().rateLimit(3, 1.seconds).build()
    }

    // MangaDex asks apps to identify themselves rather than pretend to be a browser.
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "J2KDesktop/0.1 (personal project)")

    // ----- Lists -----

    private fun listUrl(page: Int, limit: Int = 20): HttpUrl.Builder =
        "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "$limit")
            .addQueryParameter("offset", "${(page - 1) * limit}")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("availableTranslatedLanguage[]", lang)

    override fun popularMangaRequest(page: Int): Request =
        GET(listUrl(page).addQueryParameter("order[followedCount]", "desc").build(), headers)

    override fun popularMangaParse(response: Response) = mangaListParse(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET(listUrl(page).addQueryParameter("order[latestUploadedChapter]", "desc").build(), headers)

    override fun latestUpdatesParse(response: Response) = mangaListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            listUrl(page)
                .addQueryParameter("title", query)
                .addQueryParameter("order[relevance]", "desc")
                .build(),
            headers,
        )

    override fun searchMangaParse(response: Response) = mangaListParse(response)

    private fun mangaListParse(response: Response): MangasPage {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val mangas = root["data"]!!.jsonArray.map { it.jsonObject.toSManga() }
        val offset = root["offset"]!!.jsonPrimitive.int
        val limit = root["limit"]!!.jsonPrimitive.int
        val total = root["total"]!!.jsonPrimitive.int
        return MangasPage(mangas, hasNextPage = offset + limit < total)
    }

    // ----- Details -----

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/manga/${manga.url.substringAfterLast('/')}".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        json.parseToJsonElement(response.body.string()).jsonObject["data"]!!.jsonObject.toSManga()

    override fun getMangaUrl(manga: SManga): String =
        "$baseUrl/title/${manga.url.substringAfterLast('/')}"

    // ----- Chapters -----

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/manga/${manga.url.substringAfterLast('/')}/feed".toHttpUrl().newBuilder()
            .addQueryParameter("translatedLanguage[]", lang)
            .addQueryParameter("order[chapter]", "desc")
            .addQueryParameter("limit", "500")
            .addQueryParameter("includes[]", "scanlation_group")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        return root["data"]!!.jsonArray
            .map { it.jsonObject }
            // Chapters hosted on official sites (externalUrl) can't be read here, skip them
            .filter { it["attributes"]?.jsonObject?.get("externalUrl")?.jsonPrimitive?.contentOrNull == null }
            .map { it.toSChapter() }
    }

    // ----- Pages -----

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$apiUrl/at-home/server/${chapter.url.substringAfterLast('/')}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val host = root["baseUrl"]!!.jsonPrimitive.content
        val chapter = root["chapter"]!!.jsonObject
        val hash = chapter["hash"]!!.jsonPrimitive.content
        return chapter["data"]!!.jsonArray.mapIndexed { i, file ->
            Page(i, imageUrl = "$host/data/$hash/${file.jsonPrimitive.content}")
        }
    }

    // ----- JSON → models -----

    private fun JsonObject.toSManga(): SManga {
        val obj = this
        val id = obj["id"]!!.jsonPrimitive.content
        val attrs = obj["attributes"]!!.jsonObject
        val coverFile = (obj["relationships"] as? JsonArray)
            ?.map { it.jsonObject }
            ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "cover_art" }
            ?.let { it["attributes"] as? JsonObject }
            ?.get("fileName")?.jsonPrimitive?.contentOrNull

        return SManga.create().apply {
            url = "/manga/$id"
            // MangaDex sends [] instead of {} when a text field is empty, hence the `as?`
            title = (attrs["title"] as? JsonObject)?.localized() ?: "Untitled"
            description = (attrs["description"] as? JsonObject)?.localized()
            genre = (attrs["tags"] as? JsonArray)
                ?.mapNotNull { tag -> (tag.jsonObject["attributes"]?.jsonObject?.get("name") as? JsonObject)?.localized() }
                ?.joinToString()
            status = when (attrs["status"]?.jsonPrimitive?.contentOrNull) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = coverFile?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }
            initialized = true
        }
    }

    private fun JsonObject.toSChapter(): SChapter {
        val obj = this
        val attrs = obj["attributes"]!!.jsonObject
        val number = attrs["chapter"]?.jsonPrimitive?.contentOrNull
        val chapterTitle = attrs["title"]?.jsonPrimitive?.contentOrNull

        return SChapter.create().apply {
            url = "/chapter/${obj["id"]!!.jsonPrimitive.content}"
            name = buildString {
                if (number != null) append("Ch. $number")
                if (!chapterTitle.isNullOrBlank()) {
                    if (isNotEmpty()) append(" - ")
                    append(chapterTitle)
                }
                if (isEmpty()) append("Oneshot")
            }
            chapter_number = number?.toFloatOrNull() ?: -1f
            date_upload = attrs["publishAt"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                ?: 0L
            scanlator = (obj["relationships"] as? JsonArray)
                ?.map { it.jsonObject }
                ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "scanlation_group" }
                ?.let { it["attributes"] as? JsonObject }
                ?.get("name")?.jsonPrimitive?.contentOrNull
        }
    }

    private fun JsonObject.localized(): String? =
        (this["en"] ?: values.firstOrNull())?.jsonPrimitive?.contentOrNull
}