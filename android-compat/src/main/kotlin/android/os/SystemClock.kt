package android.os

object SystemClock {
    @JvmStatic fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
    @JvmStatic fun elapsedRealtimeNanos(): Long = System.nanoTime()
    @JvmStatic fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
    @JvmStatic fun currentThreadTimeMillis(): Long = System.nanoTime() / 1_000_000

    @JvmStatic
    fun sleep(ms: Long) {
        // Android's version ignores interrupts; close enough
        runCatching { Thread.sleep(ms) }
    }
}
