package android.content

import dev.naved.j2kdesktop.compat.AppDirs
import java.io.File

/** Minimal stand-in for Android's Context: only what extensions actually use. */
abstract class Context {
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences

    open fun getApplicationContext(): Context = this
    open fun getPackageName(): String = "dev.naved.j2kdesktop"
    open fun getCacheDir(): File = AppDirs.cache
    open fun getFilesDir(): File = AppDirs.data
    open fun getDataDir(): File = AppDirs.data

    companion object {
        const val MODE_PRIVATE = 0
    }
}
