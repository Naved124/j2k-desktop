package android.content

import dev.naved.j2kdesktop.compat.AndroidCompat

/** Android's Context hierarchy is Context <- ContextWrapper <- Application; some extensions rely on it. */
open class ContextWrapper(private val base: Context?) : Context() {
    fun getBaseContext(): Context? = base

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        base?.getSharedPreferences(name, mode) ?: AndroidCompat.sharedPreferences(name)
}
