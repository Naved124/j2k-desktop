package dev.naved.j2kdesktop.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import java.io.File

fun buildImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(NetworkHelper.default.client))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(System.getProperty("user.home"), ".cache/j2k-desktop/covers"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .build()
/**
 * Many sites refuse cover requests without their Referer/User-Agent, so covers are
 * requested with the source's own headers (like the Android app does).
 */
@androidx.compose.runtime.Composable
fun rememberImageRequest(url: String?, headers: okhttp3.Headers?): coil3.request.ImageRequest {
    val context = coil3.compose.LocalPlatformContext.current
    return androidx.compose.runtime.remember(url, headers) {
        coil3.request.ImageRequest.Builder(context)
            .data(url)
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
