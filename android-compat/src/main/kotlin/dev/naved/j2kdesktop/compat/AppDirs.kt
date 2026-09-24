package dev.naved.j2kdesktop.compat

import java.io.File

/** Where the app keeps its data (XDG base dirs on Linux). */
object AppDirs {
    private val home = File(System.getProperty("user.home"))

    val data: File =
        (System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".local/share"))
            .resolve("j2k-desktop").apply { mkdirs() }

    val cache: File =
        (System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".cache"))
            .resolve("j2k-desktop").apply { mkdirs() }

    val extensions: File = data.resolve("extensions").apply { mkdirs() }
    val prefs: File = data.resolve("prefs").apply { mkdirs() }
}
