package android.widget

import android.content.Context
import android.view.ViewGroup

open class FrameLayout(context: Context?) : ViewGroup(context)

open class LinearLayout(context: Context?) : ViewGroup(context) {
    var orientation: Int = VERTICAL

    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
    }
}
