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
        println("J2K Desktop is already running: bringing its window to the front.")
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
                // Java's own fullscreen keeps the title bar on Windows, so do it like a browser does
                val frame = window
                try {
                    if (fullscreen) WindowsFullscreen.enter(frame) else WindowsFullscreen.exit(frame)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    fallbackFullscreen(frame, fullscreen)
                }
            }
            // The reader's top-edge bar has a Minimize button (there's no title bar in fullscreen)
            LaunchedEffect(Unit) {
                ReaderLauncher.minimizeWindow = { window.extendedState = window.extendedState or Frame.ICONIFIED }
            }
            // Launching the app again while it runs brings this window back instead of doing nothing
            LaunchedEffect(Unit) {
                while (true) {
                    if (Startup.takeShowRequest()) {
                        window.isVisible = true
                        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                        window.toFront()
                        window.requestFocus()
                    }
                    kotlinx.coroutines.delay(700)
                }
            }
            App()
        }
    }
    // Closing the window ends the app. Background threads (network, the browser helper) must not
    // keep it alive, or the next launch finds it "already running" with no window.
    exitProcess(0)
}

/**
 * Fallback if the Win32 calls fail: make the window cover the monitor with its title bar and
 * borders pushed just outside the screen.
 */
private fun fallbackFullscreen(frame: java.awt.Window, fullscreen: Boolean) {
    val f = frame as? Frame ?: return
    if (fullscreen) {
        savedBounds = f.bounds
        savedState = f.extendedState
        f.extendedState = Frame.NORMAL
        val screen = f.graphicsConfiguration.bounds
        val insets = f.insets
        f.setBounds(
            screen.x - insets.left,
            screen.y - insets.top,
            screen.width + insets.left + insets.right,
            screen.height + insets.top + insets.bottom,
        )
        f.isAlwaysOnTop = true
        f.toFront()
    } else {
        val bounds = savedBounds ?: return
        f.isAlwaysOnTop = false
        f.bounds = bounds
        f.extendedState = savedState
        savedBounds = null
    }
}
