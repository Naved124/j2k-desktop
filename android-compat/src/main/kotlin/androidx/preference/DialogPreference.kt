package androidx.preference

import android.content.Context
import android.widget.EditText

abstract class DialogPreference(context: Context) : Preference(context) {
    var dialogTitle: CharSequence? = null
    var dialogMessage: CharSequence? = null
    var positiveButtonText: CharSequence? = null
    var negativeButtonText: CharSequence? = null
}

open class EditTextPreference(context: Context) : DialogPreference(context) {
    var text: String? = null
        set(value) {
            field = value
            persist { putString(it, value) }
        }

    var onBindEditTextListener: OnBindEditTextListener? = null

    fun interface OnBindEditTextListener {
        fun onBindEditText(editText: EditText)
    }
}

open class ListPreference(context: Context) : DialogPreference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()

    var value: String? = null
        set(v) {
            field = v
            persist { putString(it, v) }
        }

    fun findIndexOfValue(value: String?): Int = entryValues.indexOfFirst { it.toString() == value }

    fun setValueIndex(index: Int) {
        value = entryValues[index].toString()
    }

    fun getEntry(): CharSequence? = findIndexOfValue(value).takeIf { it >= 0 }?.let { entries.getOrNull(it) }
}

open class MultiSelectListPreference(context: Context) : DialogPreference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()

    var values: Set<String> = emptySet()
        set(v) {
            field = v
            persist { putStringSet(it, v) }
        }

    fun findIndexOfValue(value: String?): Int = entryValues.indexOfFirst { it.toString() == value }
}
