package dev.naved.j2kdesktop

import android.app.Application
import android.content.Context
import dev.naved.j2kdesktop.compat.AndroidCompat
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

/**
 * Registers what extensions pull out of Injekt (like the Android app's AppModule):
 * Injekt.get<Application>(), injectLazy<NetworkHelper>(), injectLazy<Json>(), Injekt.get<ProtoBuf>().
 */
object AppBootstrap {
    @Volatile
    private var done = false

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Synchronized
    fun init() {
        if (done) return
        val app = AndroidCompat.application
        Injekt.addSingleton<Application>(app)
        Injekt.addSingleton<Context>(app)
        Injekt.addSingleton<NetworkHelper>(NetworkHelper.default)
        Injekt.addSingleton<Json>(json)
        Injekt.addSingleton<ProtoBuf>(ProtoBuf)
        // BitmapFactory (used by extensions that unscramble images) decodes with Skia: WebP works
        AndroidCompat.imageDecoder = SkiaImageDecoder::decode
        done = true
    }
}
