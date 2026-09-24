package dev.naved.j2kdesktop.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
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