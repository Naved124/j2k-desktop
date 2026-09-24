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
import java.awt.Frame
import java.awt.Rectangle
import kotlin.system.exitProcess

private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/** Where the window was before reader fullscreen (Windows), to put it back. */
private var savedBounds: Rectangle? = null
private var savedState: Int = Frame.NORMAL

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

        Window(
            onCloseRequest = ::exitApplication,
            title = "J2K Desktop",
            state = windowState,
            icon = icon,
        ) {
            // The reader toggles this (F / F11); Esc or closing the reader turns it off
            LaunchedEffect(ReaderLauncher.isFullscreen) {
                val fullscreen = ReaderLauncher.isFullscreen
                if (!isWindows) {
                    windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
                    return@LaunchedEffect
                }
                // Windows keeps the title bar in Java's fullscreen mode. Instead, make the window cover
                // the whole monitor with its title bar and borders pushed just outside the screen.
                val frame = window
                if (fullscreen) {
                    savedBounds = frame.bounds
                    savedState = frame.extendedState
                    frame.extendedState = Frame.NORMAL
                    val screen = frame.graphicsConfiguration.bounds
                    val insets = frame.insets
                    frame.setBounds(
                        screen.x - insets.left,
                        screen.y - insets.top,
                        screen.width + insets.left + insets.right,
                        screen.height + insets.top + insets.bottom,
                    )
                    // Above the taskbar too
                    frame.isAlwaysOnTop = true
                    frame.toFront()
                } else {
                    val bounds = savedBounds ?: return@LaunchedEffect // wasn't fullscreen (app start)
                    frame.isAlwaysOnTop = false
                    frame.bounds = bounds
                    frame.extendedState = savedState
                    savedBounds = null
                }
            }
            App()
        }
    }
}
