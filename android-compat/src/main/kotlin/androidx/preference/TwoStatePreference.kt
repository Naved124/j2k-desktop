package androidx.preference

import android.content.Context

abstract class TwoStatePreference(context: Context) : Preference(context) {
    var isChecked: Boolean = false
        set(v) {
            field = v
            persist { putBoolean(it, v) }
        }

    var summaryOn: CharSequence? = null
    var summaryOff: CharSequence? = null
    var disableDependentsState: Boolean = false
}

open class SwitchPreferenceCompat(context: Context) : TwoStatePreference(context) {
    var switchTextOn: CharSequence? = null
    var switchTextOff: CharSequence? = null
}

open class SwitchPreference(context: Context) : TwoStatePreference(context)

open class CheckBoxPreference(context: Context) : TwoStatePreference(context)
