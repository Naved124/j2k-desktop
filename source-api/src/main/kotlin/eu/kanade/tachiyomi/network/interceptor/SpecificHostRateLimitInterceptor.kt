package eu.kanade.tachiyomi.network.interceptor

import android.os.SystemClock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Rate limit only requests to one host (ported from J2K; older extensions use it). */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(SpecificHostRateLimitInterceptor(httpUrl, permits, period, unit))

fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(
    SpecificHostRateLimitInterceptor(httpUrl, permits, period.inWholeMilliseconds, TimeUnit.MILLISECONDS),
)

fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder {
    val httpUrl = url.toHttpUrlOrNull() ?: return this
    return addInterceptor(
        SpecificHostRateLimitInterceptor(httpUrl, permits, period.inWholeMilliseconds, TimeUnit.MILLISECONDS),
    )
}

class SpecificHostRateLimitInterceptor(
    httpUrl: HttpUrl,
    private val permits: Int,
    period: Long,
    unit: TimeUnit,
) : Interceptor {
    private val requestQueue = ArrayList<Long>(permits)
    private val rateLimitMillis = unit.toMillis(period)
    private val host = httpUrl.host

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.call().isCanceled()) {
            throw IOException()
        } else if (chain.request().url.host != host) {
            return chain.proceed(chain.request())
        }

        synchronized(requestQueue) {
            val now = SystemClock.elapsedRealtime()
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
