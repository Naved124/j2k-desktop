package dev.naved.j2kdesktop.compat

import android.app.Application
import android.content.SharedPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Glue between the fake Android classes and the desktop app. */
object AndroidCompat {
    val application: Application by lazy { Application() }

    private val preferences = ConcurrentHashMap<String, SharedPreferences>()

    /** Same name => same instance, like Android's Context.getSharedPreferences. */
    fun sharedPreferences(name: String): SharedPreferences =
        preferences.computeIfAbsent(name) {
            val safe = it.replace(Regex("[^A-Za-z0-9._-]"), "_")
            FileSharedPreferences(File(AppDirs.prefs, "$safe.json"))
        }

    /** Extensions call Toast.makeText(...).show(); the UI replaces this to show a snackbar. */
    @Volatile
    var toastHandler: (String) -> Unit = { println("[toast] $it") }
}
