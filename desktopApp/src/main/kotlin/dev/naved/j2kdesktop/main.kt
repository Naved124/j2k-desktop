package dev.naved.j2kdesktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.naved.j2kdesktop.reader.ReaderLauncher
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // A repo link opened while the app runs goes to that window; don't start a second copy
    if (!Startup.begin(args)) {
        println("J2K Desktop is already running.")
        exitProcess(0)
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        val icon = remember {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
                ?.use { BitmapPainter(loadImageBitmap(it)) }
        }

        // The reader toggles this (F / F11); Esc or closing the reader turns it off
        LaunchedEffect(ReaderLauncher.isFullscreen) {
            windowState.placement =
                if (ReaderLauncher.isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "J2K Desktop",
            state = windowState,
            icon = icon,
        ) {
            App()
        }
    }
}
