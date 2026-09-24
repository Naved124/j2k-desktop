package android.widget

import android.content.Context
import android.text.TextWatcher
import android.view.View

open class TextView(context: Context?) : View(context) {
    private var textValue: CharSequence = ""
    private val watchers = mutableListOf<TextWatcher>()

    open fun getText(): CharSequence = textValue

    open fun setText(text: CharSequence?) {
        textValue = text ?: ""
    }

    var hint: CharSequence? = null
    var inputType: Int = 0
    var error: CharSequence? = null

    fun setSingleLine() {}
    fun setSingleLine(singleLine: Boolean) {}
    fun setMaxLines(maxLines: Int) {}
    fun setHorizontallyScrolling(whether: Boolean) {}
    fun setMovementMethod(movement: android.text.method.MovementMethod?) {}
    fun setTextIsSelectable(selectable: Boolean) {}

    fun addTextChangedListener(watcher: TextWatcher) {
        watchers += watcher
    }

    fun removeTextChangedListener(watcher: TextWatcher) {
        watchers -= watcher
    }
}
