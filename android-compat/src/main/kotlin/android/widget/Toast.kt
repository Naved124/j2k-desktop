package android.widget

import android.content.Context
import dev.naved.j2kdesktop.compat.AndroidCompat

/** Toast.makeText(...).show() ends up as a message in the app (or the terminal). */
class Toast private constructor(private var text: CharSequence?) {
    fun show() {
        AndroidCompat.toastHandler(text?.toString().orEmpty())
    }

    fun cancel() {}

    fun setText(s: CharSequence?) {
        text = s
    }

    fun setDuration(duration: Int) {}

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast = Toast(text)
    }
}
