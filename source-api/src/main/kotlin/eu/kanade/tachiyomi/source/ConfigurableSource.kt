package eu.kanade.tachiyomi.source

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import dev.naved.j2kdesktop.compat.AndroidCompat

/** A source with its own settings (shown in Browse → the source's "Settings"). */
interface ConfigurableSource : Source {
    /** @since extensions-lib 1.5 */
    fun getSourcePreferences(): SharedPreferences = AndroidCompat.sharedPreferences(preferenceKey())

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
