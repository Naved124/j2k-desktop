package dev.naved.j2kdesktop.compat

import java.io.File

/** Where the app keeps its data: XDG base dirs on Linux, %APPDATA% / %LOCALAPPDATA% on Windows. */
object AppDirs {
    private val home = File(System.getProperty("user.home"))
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File)

    val data: File =
        (if (isWindows) env("APPDATA") ?: File(home, "AppData/Roaming") else env("XDG_DATA_HOME") ?: File(home, ".local/share"))
            .resolve("j2k-desktop").apply { mkdirs() }

    val cache: File =
        (if (isWindows) (env("LOCALAPPDATA") ?: File(home, "AppData/Local")).resolve("j2k-desktop/cache") else (env("XDG_CACHE_HOME") ?: File(home, ".cache")).resolve("j2k-desktop"))
            .apply { mkdirs() }

    val extensions: File = data.resolve("extensions").apply { mkdirs() }
    val prefs: File = data.resolve("prefs").apply { mkdirs() }
}
