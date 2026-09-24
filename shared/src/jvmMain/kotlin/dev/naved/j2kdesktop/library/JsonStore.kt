package dev.naved.j2kdesktop.library

import dev.naved.j2kdesktop.AppBootstrap
import dev.naved.j2kdesktop.compat.AppDirs
import kotlinx.serialization.KSerializer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A JSON file in ~/.local/share/j2k-desktop. Saves are debounced (many page turns = one write) and
 * atomic (write a temp file, then rename). Pending saves are flushed when the app exits.
 */
class JsonStore<T>(private val name: String, private val serializer: KSerializer<T>, private val delayMs: Long = 800) {
    private val file get() = File(AppDirs.data, name)
    private var latest: T? = null
    private var scheduled: ScheduledFuture<*>? = null

    fun load(): T? = runCatching { AppBootstrap.json.decodeFromString(serializer, file.readText()) }
        .onFailure { if (file.exists()) System.err.println("[store] couldn't read $name: ${it.message}") }
        .getOrNull()

    @Synchronized
    fun save(value: T) {
        latest = value
        if (scheduled == null) {
            scheduled = executor.schedule({ flush() }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    fun flush() {
        val value = synchronized(this) {
            scheduled = null
            latest.also { latest = null }
        } ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$name.tmp")
            tmp.writeText(AppBootstrap.json.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { it.printStackTrace() }
    }

    init {
        stores += this
    }

    companion object {
        private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "json-store").apply { isDaemon = true } }
        private val stores = java.util.concurrent.CopyOnWriteArrayList<JsonStore<*>>()

        init {
            Runtime.getRuntime().addShutdownHook(Thread { stores.forEach { it.flush() } })
        }
    }
}
