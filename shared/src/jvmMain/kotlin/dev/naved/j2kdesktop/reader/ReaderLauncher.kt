package dev.naved.j2kdesktop.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/** What to open in the reader. [chapters] are in reading order (oldest first). */
data class ReaderRequest(
    val source: Source,
    val manga: SManga,
    val chapters: List<SChapter>,
    val startIndex: Int,
)

/** App-wide switches the reader needs: which chapter is open, and fullscreen. */
object ReaderLauncher {
    var request by mutableStateOf<ReaderRequest?>(null)
        private set

    fun open(request: ReaderRequest) {
        this.request = request
        // Like a browser's fullscreen: the reader takes the whole screen (More → Reader to turn off)
        if (dev.naved.j2kdesktop.AppSettings.readerFullscreen) isFullscreen = true
    }

    fun close() {
        request = null
        isFullscreen = false
    }

    /** main.kt watches this and switches the window between fullscreen and normal. */
    var isFullscreen by mutableStateOf(false)

    /** Set by main.kt: minimizes the window (fullscreen has no title bar to do it from). */
    var minimizeWindow: (() -> Unit)? = null
}
