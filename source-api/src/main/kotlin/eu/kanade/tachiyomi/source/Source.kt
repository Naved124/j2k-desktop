package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.awaitSingle
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable

/** A basic interface for creating a source. It could be an online source, a local source, etc. */
interface Source {
    /** ID for the source. Must be unique. */
    val id: Long

    val name: String

    val lang: String
        get() = ""

    /** @since extensions-lib 1.5 */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga = fetchMangaDetails(manga).awaitSingle()

    /** @since extensions-lib 1.5 */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> = fetchChapterList(manga).awaitSingle()

    /** @since extensions-lib 1.5 */
    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> = fetchPageList(chapter).awaitSingle()

    /** @since extensions-lib 1.6 */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = supervisorScope {
        val asyncManga = if (fetchDetails) async { getMangaDetails(manga) } else null
        val asyncChapters = if (fetchChapters) async { getChapterList(manga) } else null
        SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
    }

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getMangaDetails"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getChapterList"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw IllegalStateException("Not used")
}

fun Source.preferenceKey(): String = "source_$id"