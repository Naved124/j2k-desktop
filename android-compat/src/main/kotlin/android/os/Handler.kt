package android.os

import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Runs posted work on the UI thread (main looper) or a background thread, like Android's Handler. */
open class Handler(private val looper: Looper?) {
    constructor() : this(Looper.myLooper())

    private val pending = java.util.concurrent.ConcurrentHashMap<Runnable, ScheduledFuture<*>>()

    fun getLooper(): Looper? = looper

    fun post(r: Runnable): Boolean = postDelayed(r, 0)

    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        val future = scheduler.schedule({
            pending.remove(r)
            if (looper === Looper.getMainLooper()) EventQueue.invokeLater(r) else r.run()
        }, delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        pending[r] = future
        return true
    }

    fun removeCallbacks(r: Runnable) {
        pending.remove(r)?.cancel(false)
    }

    fun removeCallbacksAndMessages(token: Any?) {
        pending.values.forEach { it.cancel(false) }
        pending.clear()
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "android-handler").apply { isDaemon = true }
        }
    }
}
