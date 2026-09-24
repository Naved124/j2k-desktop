package dev.naved.j2kdesktop.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * getMangaUpdate, one call at a time per manga: Keiyoushi sources refuse concurrent calls for the
 * same manga ("must not be called concurrently"), e.g. the manga page + a library update at once.
 */
object MangaCalls {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun update(source: Source, manga: SManga, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val lock = locks.getOrPut("${source.id}|${manga.url}") { Mutex() }
        return lock.withLock {
            withContext(Dispatchers.IO) { source.getMangaUpdate(manga, emptyList(), fetchDetails, fetchChapters) }
        }
    }
}
