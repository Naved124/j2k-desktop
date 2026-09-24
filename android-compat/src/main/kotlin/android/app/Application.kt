package android.app

import android.content.ContextWrapper
import android.content.SharedPreferences
import dev.naved.j2kdesktop.compat.AndroidCompat

/** The one "Android Application" extensions get from Injekt. */
open class Application : ContextWrapper(null) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        AndroidCompat.sharedPreferences(name)
}
