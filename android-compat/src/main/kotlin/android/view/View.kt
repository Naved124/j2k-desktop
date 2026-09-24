package android.view

import android.content.Context

/** Just enough of View for extension preference code (EditText binders, buttons). */
open class View(private val ctx: Context?) {
    fun getContext(): Context? = ctx

    open var isEnabled: Boolean = true
    open var visibility: Int = VISIBLE

    private var clickListener: OnClickListener? = null

    open fun setOnClickListener(listener: OnClickListener?) {
        clickListener = listener
    }

    fun performClick(): Boolean {
        clickListener?.onClick(this) ?: return false
        return true
    }

    fun interface OnClickListener {
        fun onClick(v: View)
    }

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}
