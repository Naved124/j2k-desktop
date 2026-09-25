package dev.naved.j2kdesktop

import dev.naved.j2kdesktop.compat.AppDirs
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/** Command-line handling and "only one window": used by main() before the UI starts. */
object Startup {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /**
     * Queues tachiyomi:// / mihon:// repo links given as arguments (the desktop entry passes them),
     * and returns false if J2K Desktop is already running (that window picks the link up within 2 s).
     */
    fun begin(args: Array<String>): Boolean {
        val links = args.filter { it.startsWith("tachiyomi://") || it.startsWith("mihon://") }
        if (links.isNotEmpty()) {
            runCatching { File(AppDirs.data, "pending-repos.txt").appendText(links.joinToString("\n", postfix = "\n")) }
        }
        if (acquireLock()) {
            showRequest.delete()
            return true
        }
        // Already running: ask that copy to show its window
        runCatching { showRequest.writeText(System.currentTimeMillis().toString()) }
        return false
    }

    private val showRequest get() = File(AppDirs.data, "show-window.request")

    /** True (once) when another launch asked this running copy to show its window. */
    fun takeShowRequest(): Boolean = showRequest.exists() && showRequest.delete()

    private fun acquireLock(): Boolean {
        return try {
            val c = RandomAccessFile(File(AppDirs.data, "app.lock"), "rw").channel
            val l = c.tryLock() ?: run {
                c.close()
                return false
            }
            channel = c
            lock = l
            true
        } catch (e: Exception) {
            true // can't tell: start anyway
        }
    }
}
