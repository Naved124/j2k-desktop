package androidx.preference

import android.content.Context

open class PreferenceGroup(context: Context) : Preference(context) {
    private val children = mutableListOf<Preference>()

    /** Not part of Android's API; used by our settings screen. */
    fun children(): List<Preference> = children.toList()

    open fun addPreference(preference: Preference): Boolean {
        preference.sharedPreferences = sharedPreferences
        children += preference
        if (preference is PreferenceGroup) preference.children.forEach { it.sharedPreferences = sharedPreferences }
        return true
    }

    fun removePreference(preference: Preference): Boolean = children.remove(preference)

    fun removeAll() {
        children.clear()
    }

    fun getPreferenceCount(): Int = children.size

    fun getPreference(index: Int): Preference = children[index]

    @Suppress("UNCHECKED_CAST")
    fun <T : Preference> findPreference(key: CharSequence): T? {
        for (p in children) {
            if (p.key == key.toString()) return p as T
            if (p is PreferenceGroup) p.findPreference<T>(key)?.let { return it }
        }
        return null
    }
}

class PreferenceScreen(context: Context) : PreferenceGroup(context)

open class PreferenceCategory(context: Context) : PreferenceGroup(context)
