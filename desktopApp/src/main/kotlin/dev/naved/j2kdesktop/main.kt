package dev.naved.j2kdesktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.naved.j2kdesktop.reader.ReaderLauncher

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    // The reader toggles this (F / F11); Esc or closing the reader turns it off
    LaunchedEffect(ReaderLauncher.isFullscreen) {
        windowState.placement =
            if (ReaderLauncher.isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "J2K Desktop",
        state = windowState,
    ) {
        App()
    }
}
