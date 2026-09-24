package eu.kanade.tachiyomi.network.interceptor

import dev.naved.j2kdesktop.browser.Browser
import dev.naved.j2kdesktop.browser.BrowserFetcher
import dev.naved.j2kdesktop.browser.BrowserLocator
import dev.naved.j2kdesktop.browser.CloudflareSolver
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Like the Android app's: when a site answers with Cloudflare's browser check, pass it in a real
 * browser and retry with the cookies it earned. If the site still refuses this connection, its
 * requests go through the browser from then on. (Keiyoushi extensions require this class name.)
 */
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val host = request.url.host
        if (BrowserFetcher.isRouted(host)) return viaBrowser { BrowserFetcher.fetch(request) }

        // cf_clearance only works together with the User-Agent of the browser that earned it
        if (CloudflareSolver.clearance(request.url) != null) request = request.withBrowserAgent()

        val response = chain.proceed(request)
        if (!CloudflareSolver.isChallenge(response)) return response
        response.close()

        if (!Browser.isAvailable) {
            throw IOException("$host is behind a Cloudflare check. ${BrowserLocator.NOT_FOUND}")
        }
        val oldClearance = CloudflareSolver.clearance(request.url)
        viaBrowser { CloudflareSolver.solve(request.url, oldClearance) }

        // The cookie jar now has the browser's cookies (cf_clearance)
        val retryRequest = request.withBrowserAgent()
        val retry = chain.proceed(retryRequest)
        if (!CloudflareSolver.isChallenge(retry)) return retry
        retry.close()

        BrowserFetcher.route(host)
        return viaBrowser { BrowserFetcher.fetch(retryRequest) }
    }

    private fun Request.withBrowserAgent(): Request = newBuilder().header("User-Agent", Browser.userAgent).build()

    private fun <T> viaBrowser(block: suspend () -> T): T = try {
        runBlocking { block() }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e.message ?: e.toString(), e)
    }
}
