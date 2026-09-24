package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * On Android this solves Cloudflare's "checking your browser" page in a hidden WebView.
 * We don't have an embedded browser yet, so this only detects the challenge and gives a clear
 * error instead of a confusing HTTP 403. (Keiyoushi extensions require an interceptor with this name.)
 */
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val isChallenge = response.code in setOf(403, 503) &&
            response.header("Server").orEmpty().startsWith("cloudflare", ignoreCase = true) &&
            (response.header("cf-mitigated") == "challenge" || response.header("cf-chl-bypass") != null ||
                response.peekBody(64 * 1024).string().let { "challenge-platform" in it || "cf-browser-verification" in it })
        if (isChallenge) {
            response.close()
            throw IOException(
                "This site is behind a Cloudflare browser check, which J2K Desktop can't solve yet " +
                    "(${chain.request().url.host})",
            )
        }
        return response
    }
}
