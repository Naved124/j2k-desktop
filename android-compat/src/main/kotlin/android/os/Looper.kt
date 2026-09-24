package android.os

import java.awt.EventQueue

/**
 * Android's "main looper" = the UI thread. On desktop that's the AWT/Compose event thread.
 * Extensions mostly use this to check "am I on the main thread?" before blocking.
 */
class Looper private constructor(private val isMain: Boolean) {
    fun isCurrentThread(): Boolean = isMain && EventQueue.isDispatchThread()

    fun quit() {}
    fun quitSafely() {}

    companion object {
        private val main = Looper(isMain = true)

        @JvmStatic
        fun getMainLooper(): Looper = main

        /** The main looper on the UI thread, null on background threads (they have no looper). */
        @JvmStatic
        fun myLooper(): Looper? = if (EventQueue.isDispatchThread()) main else null

        @JvmStatic
        fun prepare() {}

        @JvmStatic
        fun loop() {}
    }
}
