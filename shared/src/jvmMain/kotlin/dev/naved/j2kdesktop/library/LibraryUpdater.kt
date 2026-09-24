package dev.naved.j2kdesktop.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.naved.j2kdesktop.source.MangaCalls
import dev.naved.j2kdesktop.source.SourceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Checks library manga for new chapters (Library → "Update"; also once after start-up). */
object LibraryUpdater {
    var isRunning by mutableStateOf(false)
        private set
    var done by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    /** App-wide (UI thread), so leaving the Library tab doesn't stop an update. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(categoryId: Long? = null) {
        if (isRunning) return
        val targets = Library.entries.filter { categoryId == null || categoryId in it.categories }
        if (targets.isEmpty()) return
        isRunning = true
        done = 0
        total = targets.size
        status = "Checking ${targets.size} manga for new chapters…"
        job = scope.launch {
            var newChapters = 0
            val failed = mutableListOf<String>()
            val permits = Semaphore(4)
            try {
                targets.map { entry ->
                    async {
                        permits.withPermit {
                            val source = SourceManager.get(entry.sourceId)
                            if (source == null) {
                                failed += "${entry.title} (extension not installed)"
                            } else {
                                try {
                                    val manga = entry.toSManga()
                                    val update = MangaCalls.update(source, manga, fetchDetails = false, fetchChapters = true)
                                    val found = ChapterCache.put(entry.sourceId, entry.url, update.chapters)
                                    val hidden = ChapterFilters.hiddenGroups(entry.sourceId, entry.url)
                                    val wanted = found.filter { it.scanlator?.trim().orEmpty() !in hidden }
                                    newChapters += wanted.size
                                    Updates.add(
                                        wanted.map {
                                            UpdateEntry(
                                                entry.sourceId, entry.url, entry.title, entry.thumbnailUrl,
                                                it.url, it.name, System.currentTimeMillis(),
                                            )
                                        },
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    System.err.println("[update] ${entry.title}: ${e.message}")
                                    failed += entry.title
                                }
                            }
                            done++
                        }
                    }
                }.awaitAll()
                status = buildString {
                    append(if (newChapters == 0) "No new chapters" else "$newChapters new chapter${if (newChapters == 1) "" else "s"}")
                    if (failed.isNotEmpty()) append(" · ${failed.size} couldn't be checked")
                }
            } finally {
                isRunning = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
        isRunning = false
        status = "Update cancelled"
    }
}
