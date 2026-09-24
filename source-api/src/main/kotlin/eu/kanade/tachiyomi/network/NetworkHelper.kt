package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(cacheDir: File) {
    val cookieJar = MemoryCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .cache(Cache(File(cacheDir, "network_cache"), 5L * 1024 * 1024))
        // Same interceptors (and order) as the Android app; Keiyoushi extensions check for them
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor { defaultUserAgent })
        .addInterceptor(CloudflareInterceptor())
        .build()

    @Deprecated("The regular client handles Cloudflare by default")
    val cloudflareClient: OkHttpClient
        get() = client

    val defaultUserAgent: String
        get() = DEFAULT_USER_AGENT

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

        /** One shared instance for the whole app. Cache lives in ~/.cache/j2k-desktop */
        val default: NetworkHelper by lazy { NetworkHelper(appCacheDir()) }

        private fun appCacheDir(): File {
            val base = System.getenv("XDG_CACHE_HOME")?.let(::File)
                ?: File(System.getProperty("user.home"), ".cache")
            return File(base, "j2k-desktop").apply { mkdirs() }
        }
    }
}