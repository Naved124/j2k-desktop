package dev.naved.j2kdesktop.reader

import dev.naved.j2kdesktop.library.ChapterCache
import dev.naved.j2kdesktop.library.ChapterFilters
import dev.naved.j2kdesktop.library.Library
import dev.naved.j2kdesktop.library.ReadProgress
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Opens the reader straight from History/Updates, without going through the manga page. */
object ReaderOpener {
    /** The chapter list (newest first); the saved list if the source can't be reached. */
    suspend fun chapters(source: Source, manga: SManga): List<SChapter> {
        val chapters = try {
            withContext(Dispatchers.IO) {
                source.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true).chapters
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ChapterCache.get(source.id, manga.url)?.map { it.toSChapter() } ?: throw e
        }
        if (Library.contains(source.id, manga.url)) ChapterCache.put(source.id, manga.url, chapters)
        return chapters
    }

    /** Opens [chapterUrl], or where you left off ("continue reading") when it's null. */
    suspend fun open(source: Source, manga: SManga, chapterUrl: String? = null) {
        val ordered = ChapterFilters.visible(source.id, manga.url, chapters(source, manga)).reversed()
        if (ordered.isEmpty()) throw IOException("No readable chapters")
        val index = chapterUrl?.let { url -> ordered.indexOfFirst { it.url == url } }?.takeIf { it >= 0 }
            ?: ReadProgress.continueIndex(source.id, manga.url, ordered).coerceAtLeast(0)
        ReaderLauncher.open(ReaderRequest(source, manga, ordered, index))
    }
}
