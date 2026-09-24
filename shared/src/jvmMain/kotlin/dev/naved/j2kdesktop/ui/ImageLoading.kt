package dev.naved.j2kdesktop.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import java.io.File

/** The app's image loader (covers), kept so More → "Clear image cache" can reach it. */
object ImageLoaderHolder {
    @Volatile
    var loader: ImageLoader? = null

    fun clearCache() {
        loader?.memoryCache?.clear()
        loader?.diskCache?.clear()
    }
}

fun buildImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(NetworkHelper.default.client))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(dev.naved.j2kdesktop.compat.AppDirs.cache, "covers"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .build()
        .also { ImageLoaderHolder.loader = it }
/**
 * Many sites refuse cover requests without their Referer/User-Agent, so covers are
 * requested with the source's own headers (like the Android app does).
 */
@androidx.compose.runtime.Composable
fun rememberImageRequest(url: String?, headers: okhttp3.Headers?): coil3.request.ImageRequest {
    val context = coil3.compose.LocalPlatformContext.current
    val incognito = dev.naved.j2kdesktop.Incognito.enabled
    return androidx.compose.runtime.remember(url, headers, incognito) {
        coil3.request.ImageRequest.Builder(context)
            .data(url)
            // Incognito: use covers already cached, but don't write new ones to disk
            .apply { if (incognito) diskCachePolicy(coil3.request.CachePolicy.READ_ONLY) }
            .apply {
                if (headers != null) {
                    val builder = coil3.network.NetworkHeaders.Builder()
                    headers.forEach { (name, value) -> builder[name] = value }
                    httpHeaders(builder.build())
                }
            }
            .build()
    }
}
