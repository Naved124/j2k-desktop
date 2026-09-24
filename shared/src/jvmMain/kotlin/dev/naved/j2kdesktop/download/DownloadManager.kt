package dev.naved.j2kdesktop.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.naved.j2kdesktop.reader.PageLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One chapter waiting to be (or being) downloaded. */
class DownloadTask(val source: Source, val manga: SManga, val chapter: SChapter) {
    val key: String = DownloadManager.keyOf(source, manga, chapter)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)
}

/**
 * Saves chapters for offline reading to ~/Documents/J2KDesktop/downloads/<source>/<manga>/<chapter>/
 * as 001.jpg, 002.png… (the same layout Tachiyomi uses, so the folders are easy to browse or back up).
 * One chapter at a time; a chapter folder only appears once every page is saved.
 */
object DownloadManager {
    val root: File = File(System.getProperty("user.home"), "Documents/J2KDesktop/downloads")

    val queue = mutableStateListOf<DownloadTask>()
    var isPaused by mutableStateOf(false)
        private set

    /** Bumped whenever downloads appear or disappear on disk, so the UI re-checks. */
    var version by mutableStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var worker: Job? = null
    private val downloadedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun clean(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|\u0000-\u001f]"""), "_").trim().trimEnd('.').take(120).ifEmpty { "_" }

    private fun mangaTitle(manga: SManga) = runCatching { manga.title }.getOrDefault(manga.url)

    fun mangaDir(source: Source, manga: SManga): File = File(File(root, clean(source.name)), clean(mangaTitle(manga)))

    fun chapterDir(source: Source, manga: SManga, chapter: SChapter): File {
        val group = chapter.scanlator?.trim().orEmpty()
        val name = if (group.isNotEmpty()) "${group}_${chapter.name}" else chapter.name
        return File(mangaDir(source, manga), clean(name))
    }

    fun keyOf(source: Source, manga: SManga, chapter: SChapter) = "${source.id}|${manga.url}|${chapter.url}"

    fun isDownloaded(source: Source, manga: SManga, chapter: SChapter): Boolean {
        version // read, so composables re-check after changes
        val dir = chapterDir(source, manga, chapter)
        return downloadedCache.getOrPut(dir.path) { dir.isDirectory && (dir.list()?.isNotEmpty() == true) }
    }

    fun taskFor(source: Source, manga: SManga, chapter: SChapter): DownloadTask? {
        val key = keyOf(source, manga, chapter)
        return queue.firstOrNull { it.key == key }
    }

    /** Pages of a downloaded chapter as file:// pages, or null if it isn't downloaded. */
    fun localPages(source: Source, manga: SManga, chapter: SChapter): List<Page>? {
        val dir = chapterDir(source, manga, chapter)
        if (!dir.isDirectory) return null
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return null
        return files.mapIndexed { i, f -> Page(i, "", "file://" + f.absolutePath) }
    }

    fun enqueue(source: Source, manga: SManga, chapters: List<SChapter>) {
        val mangaCopy = manga.copy()
        chapters.forEach { chapter ->
            if (!isDownloaded(source, manga, chapter) && taskFor(source, manga, chapter) == null) {
                queue += DownloadTask(source, mangaCopy, chapter)
            }
        }
        startWorker()
    }

    fun cancel(task: DownloadTask) {
        queue.remove(task)
        if (task.running) {
            worker?.cancel()
            worker = null
            startWorker()
        }
    }

    // ----- Order: the queue runs top to bottom, so the first chapter you clicked downloads first -----

    /** Where a task sits in line (1 = next / downloading now). */
    fun positionOf(task: DownloadTask): Int = queue.indexOf(task) + 1

    fun moveToTop(task: DownloadTask) = move(task, 0)

    fun moveToBottom(task: DownloadTask) = move(task, queue.size - 1)

    fun moveUp(task: DownloadTask) = move(task, queue.indexOf(task) - 1)

    fun moveDown(task: DownloadTask) = move(task, queue.indexOf(task) + 1)

    /**
     * Moves a task in the line. The chapter being downloaded right now finishes first;
     * whatever is on top after that goes next.
     */
    private fun move(task: DownloadTask, to: Int) {
        val from = queue.indexOf(task)
        if (from < 0) return
        val target = to.coerceIn(0, queue.size - 1)
        if (target == from) return
        queue.removeAt(from)
        queue.add(target, task)
    }

    /** Clears the error on failed chapters so they're tried again (in their place in line). */
    fun retryFailed() {
        queue.forEach { it.error = null }
        resume()
    }

    fun clearQueue() {
        queue.clear()
        worker?.cancel()
        worker = null
    }

    fun pause() {
        isPaused = true
        worker?.cancel()
        worker = null
        queue.forEach { it.running = false }
    }

    fun resume() {
        isPaused = false
        startWorker()
    }

    fun delete(source: Source, manga: SManga, chapters: List<SChapter>) {
        chapters.forEach { chapter ->
            chapterDir(source, manga, chapter).deleteRecursively()
        }
        mangaDir(source, manga).let { dir -> if (dir.list()?.isEmpty() == true) dir.delete() }
        changed()
    }

    private fun changed() {
        downloadedCache.clear()
        version++
    }

    private fun startWorker() {
        if (isPaused || worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val task = queue.firstOrNull { it.error == null } ?: break
                task.running = true
                try {
                    download(task)
                    queue.remove(task)
                    changed()
                } catch (e: CancellationException) {
                    task.running = false
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                    task.error = e.message ?: e.toString()
                } finally {
                    task.running = false
                }
            }
        }
    }

    private suspend fun download(task: DownloadTask) {
        val finalDir = chapterDir(task.source, task.manga, task.chapter)
        val tmpDir = File(finalDir.parentFile, finalDir.name + "_tmp")
        val pages = withContext(Dispatchers.IO) { task.source.getPageList(task.chapter) }
        if (pages.isEmpty()) error("The source returned no pages")
        task.total = pages.size
        task.done = 0
        withContext(Dispatchers.IO) { tmpDir.mkdirs() }
        pages.forEachIndexed { i, page ->
            val name = "%03d".format(i + 1)
            val existing = tmpDir.listFiles { f -> f.name.startsWith("$name.") }?.firstOrNull()
            if (existing == null || existing.length() == 0L) {
                val bytes = PageLoader.fetchBytes(task.source, page)
                withContext(Dispatchers.IO) { File(tmpDir, "$name.${extensionOf(bytes)}").writeBytes(bytes) }
            }
            task.done = i + 1
        }
        withContext(Dispatchers.IO) {
            if (finalDir.exists()) finalDir.deleteRecursively()
            if (!tmpDir.renameTo(finalDir)) error("Couldn't finish writing ${finalDir.path}")
        }
    }

    private fun extensionOf(bytes: ByteArray): String {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 -> "jpg"
            at(0) == 0x89 && at(1) == 0x50 -> "png"
            at(0) == 0x47 && at(1) == 0x49 -> "gif"
            at(0) == 0x52 && at(1) == 0x49 && at(8) == 0x57 && at(9) == 0x45 -> "webp"
            bytes.size > 12 && String(bytes, 4, 8, Charsets.ISO_8859_1).startsWith("ftypavi") -> "avif"
            else -> "jpg"
        }
    }
}
