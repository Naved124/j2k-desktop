package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** @since extension-lib 1.3 */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(RateLimitInterceptor(permits, period, unit))

/** e.g. rateLimit(5, 1.seconds) = at most 5 requests per second. @since extension-lib 1.5 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(RateLimitInterceptor(permits, period.inWholeMilliseconds, TimeUnit.MILLISECONDS))

private class RateLimitInterceptor(
    private val permits: Int,
    period: Long,
    unit: TimeUnit,
) : Interceptor {
    private val requestQueue = ArrayList<Long>(permits)
    private val rateLimitMillis = unit.toMillis(period)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.call().isCanceled()) throw IOException()

        synchronized(requestQueue) {
            val now = System.nanoTime() / 1_000_000 // Android used SystemClock.elapsedRealtime()
            val waitTime = if (requestQueue.size < permits) {
                0
            } else {
                val oldestReq = requestQueue[0]
                val newestReq = requestQueue[permits - 1]
                if (newestReq - oldestReq > rateLimitMillis) 0 else oldestReq + rateLimitMillis - now
            }

            if (chain.call().isCanceled()) throw IOException()

            if (requestQueue.size == permits) requestQueue.removeAt(0)
            if (waitTime > 0) {
                requestQueue.add(now + waitTime)
                Thread.sleep(waitTime)
            } else {
                requestQueue.add(now)
            }
        }
        return chain.proceed(chain.request())
    }
}