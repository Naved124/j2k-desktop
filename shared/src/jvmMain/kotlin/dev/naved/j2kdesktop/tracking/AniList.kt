package dev.naved.j2kdesktop.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.naved.j2kdesktop.compat.AndroidCompat
import dev.naved.j2kdesktop.library.JsonStore
import dev.naved.j2kdesktop.library.mangaKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A manga linked to an AniList entry. */
@Serializable
data class TrackEntry(
    val mediaId: Long,
    val title: String,
    val totalChapters: Int? = null,
    val progress: Int = 0,
    val status: String = "CURRENT",
    val coverUrl: String? = null,
    val siteUrl: String? = null,
)

class MediaResult(
    val id: Long,
    val title: String,
    val format: String?,
    val chapters: Int?,
    val coverUrl: String?,
    val siteUrl: String?,
    val year: Int?,
)

/** Which manga are linked to which AniList entries (tracking.json). */
object Tracking {
    private val store = JsonStore("tracking.json", MapSerializer(String.serializer(), TrackEntry.serializer()))
    private val map = mutableStateMapOf<String, TrackEntry>().apply { store.load()?.let { putAll(it) } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val statuses = listOf("CURRENT", "PLANNING", "COMPLETED", "PAUSED", "DROPPED", "REPEATING")

    fun statusLabel(status: String) = when (status) {
        "CURRENT" -> "Reading"
        "PLANNING" -> "Plan to read"
        "COMPLETED" -> "Completed"
        "PAUSED" -> "Paused"
        "DROPPED" -> "Dropped"
        "REPEATING" -> "Rereading"
        else -> status
    }

    fun get(sourceId: Long, url: String): TrackEntry? = map[mangaKey(sourceId, url)]

    fun set(sourceId: Long, url: String, entry: TrackEntry) {
        map[mangaKey(sourceId, url)] = entry
        store.save(map.toMap())
    }

    fun remove(sourceId: Long, url: String) {
        map.remove(mangaKey(sourceId, url))
        store.save(map.toMap())
    }

    /** Called when a chapter becomes read: bumps AniList progress if this chapter is further along. */
    fun onChapterRead(sourceId: Long, url: String, chapterNumber: Float) {
        val entry = get(sourceId, url) ?: return
        if (!AniList.isLoggedIn) return
        val number = chapterNumber.toInt()
        if (number <= entry.progress) return
        val completed = entry.totalChapters != null && number >= entry.totalChapters
        val status = when {
            completed -> "COMPLETED"
            entry.status == "PLANNING" || entry.status == "PAUSED" -> "CURRENT"
            else -> entry.status
        }
        val updated = entry.copy(progress = number, status = status)
        set(sourceId, url, updated)
        scope.launch {
            runCatching { AniList.save(updated.mediaId, updated.progress, updated.status) }
                .onFailure { AndroidCompat.toastHandler("AniList update failed: ${it.message}") }
        }
    }
}

/**
 * AniList over its GraphQL API. Login: you register a (free) API client on AniList with the
 * redirect URL https://anilist.co/api/v2/oauth/pin, open the login page, and paste the token shown.
 */
object AniList {
    private val prefs get() = AndroidCompat.sharedPreferences("tracking")
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    var clientId by mutableStateOf(prefs.getString("anilist_client_id", "") ?: "")
        private set
    var userName by mutableStateOf(prefs.getString("anilist_user", null))
        private set
    private var token: String? = prefs.getString("anilist_token", null)

    val isLoggedIn: Boolean get() = !token.isNullOrBlank() && userName != null

    const val PIN_REDIRECT = "https://anilist.co/api/v2/oauth/pin"

    fun saveClientId(id: String) {
        clientId = id.trim()
        prefs.edit().putString("anilist_client_id", clientId).apply()
    }

    fun loginPageUrl(): String = "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token"

    /** Checks the pasted token and remembers it. Returns the AniList user name. */
    suspend fun login(pastedToken: String): String {
        val clean = pastedToken.trim().removePrefix("Bearer ").trim()
        val data = query("query { Viewer { id name } }", JsonObject(emptyMap()), clean)
        val name = data.obj("Viewer")?.str("name") ?: throw IOException("AniList didn't accept that token")
        token = clean
        userName = name
        prefs.edit().putString("anilist_token", clean).putString("anilist_user", name).apply()
        return name
    }

    fun logout() {
        token = null
        userName = null
        prefs.edit().remove("anilist_token").remove("anilist_user").apply()
    }

    suspend fun search(text: String): List<MediaResult> {
        val q = """
            query (${'$'}search: String) {
              Page(perPage: 20) {
                media(search: ${'$'}search, type: MANGA) {
                  id format chapters siteUrl startDate { year }
                  title { userPreferred english romaji }
                  coverImage { medium }
                }
              }
            }
        """.trimIndent()
        val data = query(q, buildJsonObject { put("search", text) }, token)
        val media = data.obj("Page")?.get("media") as? JsonArray ?: return emptyList()
        return media.mapNotNull { el ->
            val m = el as? JsonObject ?: return@mapNotNull null
            val title = m.obj("title")
            MediaResult(
                id = m["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                title = title?.str("userPreferred") ?: title?.str("english") ?: title?.str("romaji") ?: "?",
                format = m.str("format"),
                chapters = m["chapters"]?.jsonPrimitive?.intOrNull,
                coverUrl = m.obj("coverImage")?.str("medium"),
                siteUrl = m.str("siteUrl"),
                year = m.obj("startDate")?.get("year")?.jsonPrimitive?.intOrNull,
            )
        }
    }

    /** Your current list entry for a manga: (progress, status), or null if it isn't on your list. */
    suspend fun entry(mediaId: Long): Pair<Int, String>? {
        val q = "query (${'$'}id: Int) { Media(id: ${'$'}id) { mediaListEntry { progress status } } }"
        val data = query(q, buildJsonObject { put("id", mediaId) }, requireToken())
        val e = data.obj("Media")?.obj("mediaListEntry") ?: return null
        return (e["progress"]?.jsonPrimitive?.intOrNull ?: 0) to (e.str("status") ?: "CURRENT")
    }

    suspend fun save(mediaId: Long, progress: Int, status: String) {
        val q = """
            mutation (${'$'}id: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}id, progress: ${'$'}progress, status: ${'$'}status) { id progress status }
            }
        """.trimIndent()
        query(
            q,
            buildJsonObject {
                put("id", mediaId)
                put("progress", progress)
                put("status", status)
            },
            requireToken(),
        )
    }

    private fun requireToken(): String = token ?: throw IOException("Not logged in to AniList (More → Tracking)")

    private suspend fun query(query: String, variables: JsonObject, auth: String?): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body)
            .header("Accept", "application/json")
            .apply { if (auth != null) header("Authorization", "Bearer $auth") }
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IOException("AniList: HTTP ${response.code}")
            (root["errors"] as? JsonArray)?.firstOrNull()?.let { err ->
                val message = (err as? JsonObject)?.str("message") ?: err.toString()
                if (response.code == 401 || message.contains("Invalid token", ignoreCase = true)) logout()
                throw IOException("AniList: $message")
            }
            root.obj("data") ?: throw IOException("AniList: empty answer (HTTP ${response.code})")
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
