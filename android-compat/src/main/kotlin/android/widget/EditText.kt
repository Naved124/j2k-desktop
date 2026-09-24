package android.widget

import android.content.Context
import android.text.Editable
import android.text.SimpleEditable

open class EditText(context: Context?) : TextView(context) {
    // Android's EditText.getText() returns Editable, and extension bytecode depends on that
    override fun getText(): Editable = SimpleEditable(super.getText().toString())

    fun setSelection(index: Int) {}
    fun selectAll() {}
}
