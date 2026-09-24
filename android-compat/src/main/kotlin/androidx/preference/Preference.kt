package androidx.preference

import android.content.Context
import android.content.SharedPreferences

/**
 * Minimal androidx.preference model. Extensions build these in setupPreferenceScreen();
 * our Compose settings screen renders them and writes to [sharedPreferences].
 */
open class Preference(private val context: Context) {
    fun getContext(): Context = context

    var key: String? = null
    open var title: CharSequence? = null
    open var summary: CharSequence? = null
    var isEnabled: Boolean = true
    var isVisible: Boolean = true
    var isSelectable: Boolean = true
    var isPersistent: Boolean = true
    var isIconSpaceReserved: Boolean = false
    var isSingleLineTitle: Boolean = true
    var order: Int = Int.MAX_VALUE
    var dependency: String? = null

    /** Set by the screen when this preference is added to it. */
    var sharedPreferences: SharedPreferences? = null

    var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    var onPreferenceClickListener: OnPreferenceClickListener? = null

    private var storedDefault: Any? = null

    fun setDefaultValue(defaultValue: Any?) {
        storedDefault = defaultValue
    }

    /** Not part of Android's API; used by our settings screen. */
    fun defaultValueForUi(): Any? = storedDefault

    fun callChangeListener(newValue: Any?): Boolean =
        onPreferenceChangeListener?.onPreferenceChange(this, newValue) ?: true

    fun notifyChanged() {}

    protected fun persist(block: SharedPreferences.Editor.(String) -> Unit) {
        val k = key ?: return
        val prefs = sharedPreferences ?: return
        if (!isPersistent) return
        prefs.edit().apply { block(k) }.apply()
    }

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    fun interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }
}
