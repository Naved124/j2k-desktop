package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.awaitSingle
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** A simple implementation for sources from a website. */
@Suppress("unused", "DEPRECATION")
abstract class HttpSource : CatalogueSource {

    /** J2K gets this from Injekt. We switch to Injekt in phase 7. */
    protected val network: NetworkHelper
        get() = NetworkHelper.default

    /** Base url of the website without the trailing slash, like: https://mysite.com */
    abstract val baseUrl: String

    /** @since extensions-lib 1.6 */
    open fun getHomeUrl(): String = baseUrl

    /** Bump this if the site changes so much that old urls stop working. */
    open val versionId = 1

    override val id by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient
        get() = network.client

    /** First 64 bits of MD5("name/lang/versionId"), with the sign bit cleared. @since extensions-lib 1.5 */
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgent)
    }

    override fun toString() = "$name (${lang.uppercase()})"

    // ----- Popular -----

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess()
            .map { response -> popularMangaParse(response) }

    protected open fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ----- Search -----

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.defer {
            try {
                client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            } catch (e: NoClassDefFoundError) {
                // RxJava doesn't handle Errors; happens with outdated extensions
                throw RuntimeException(e)
            }
        }.map { response -> searchMangaParse(response) }

    protected open fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()
    protected open fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ----- Latest -----

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess()
            .map { response -> latestUpdatesParse(response) }

    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ----- Manga details -----

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess()
            .map { response -> mangaDetailsParse(response).apply { initialized = true } }

    open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    protected open fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ----- Chapters -----

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        if (manga.status == SManga.LICENSED) throw LicensedMangaChaptersException()
        return fetchChapterList(manga).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getChapterList"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        if (manga.status != SManga.LICENSED) {
            client.newCall(chapterListRequest(manga)).asObservableSuccess()
                .map { response -> chapterListParse(response) }
        } else {
            Observable.error(LicensedMangaChaptersException())
        }

    protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    protected open fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // ----- Pages -----

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess()
            .map { response -> pageListParse(response) }

    protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)
    protected open fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    // ----- Images -----

    /** @since extensions-lib 1.5 */
    open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingle()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getImageUrl"))
    open fun fetchImageUrl(page: Page): Observable<String> =
        client.newCall(imageUrlRequest(page)).asObservableSuccess().map { imageUrlParse(it) }

    protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)
    protected open fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    /** @since extensions-lib 1.5 */
    open suspend fun getImage(page: Page): Response =
        client.newCachelessCallWithProgress(imageRequest(page), page).awaitSuccess()

    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    // ----- Url helpers -----

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String = try {
        val uri = URI(orig.replace(" ", "%20"))
        var out = uri.path
        if (uri.query != null) out += "?" + uri.query
        if (uri.fragment != null) out += "#" + uri.fragment
        out
    } catch (e: URISyntaxException) {
        orig
    }

    /** @since extensions-lib 1.4 */
    open fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()

    /** @since extensions-lib 1.4 */
    open fun getChapterUrl(chapter: SChapter): String = ""

    @Deprecated("All modifications should be done when constructing the chapter")
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}

    override fun getFilterList() = FilterList()
}

class LicensedMangaChaptersException : Exception("Licensed - No chapters to show")