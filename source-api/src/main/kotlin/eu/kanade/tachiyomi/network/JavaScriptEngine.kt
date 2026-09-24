package eu.kanade.tachiyomi.network

import android.content.Context

/** Exists so extensions that reference it still load; running JS isn't supported on desktop yet. */
@Suppress("UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {
    suspend fun <T> evaluate(script: String): T =
        throw UnsupportedOperationException("JavaScript evaluation isn't supported in J2K Desktop yet")
}
