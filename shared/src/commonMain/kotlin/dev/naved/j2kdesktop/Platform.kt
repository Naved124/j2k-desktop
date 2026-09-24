package dev.naved.j2kdesktop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform