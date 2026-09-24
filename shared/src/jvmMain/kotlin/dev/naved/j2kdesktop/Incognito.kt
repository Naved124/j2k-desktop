package dev.naved.j2kdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Incognito mode: while on, reading leaves no trace — no history, no last page,
 * no "read" marks from the reader, no tracking updates, no per-manga reader settings,
 * and new covers aren't written to the disk cache.
 *
 * Deliberately NOT saved anywhere: it lasts for this session only and is off every time the app starts.
 */
object Incognito {
    var enabled by mutableStateOf(false)

    fun toggle() {
        enabled = !enabled
    }
}
