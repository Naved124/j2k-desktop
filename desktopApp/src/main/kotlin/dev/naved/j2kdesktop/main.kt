package dev.naved.j2kdesktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "J2K Desktop",
    ) {
        App()
    }
}