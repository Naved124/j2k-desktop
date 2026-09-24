package android.view

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper

/** Just enough of View for extension preference code (EditText binders, buttons) and WebView sizing. */
open class View(private val ctx: Context?) {
    fun getContext(): Context? = ctx

    open var isEnabled: Boolean = true
    open var visibility: Int = VISIBLE

    private var clickListener: OnClickListener? = null
    private var params: ViewGroup.LayoutParams? = null
    internal var parentGroup: ViewGroup? = null
    private var measuredW = 0
    private var measuredH = 0

    open fun setOnClickListener(listener: OnClickListener?) {
        clickListener = listener
    }

    fun performClick(): Boolean {
        clickListener?.onClick(this) ?: return false
        return true
    }

    open fun getResources(): Resources = Resources.getSystem()

    open fun getParent(): ViewParent? = parentGroup

    open fun getRootView(): View {
        var view: View = this
        while (true) view = view.parentGroup ?: return view
    }

    open fun <T : View> findViewById(id: Int): T? = null

    open fun post(action: Runnable): Boolean = Handler(Looper.getMainLooper()).post(action)

    open fun postDelayed(action: Runnable, delayMillis: Long): Boolean =
        Handler(Looper.getMainLooper()).postDelayed(action, delayMillis)

    open fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        this.params = params
    }

    open fun getLayoutParams(): ViewGroup.LayoutParams? = params

    open fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {}

    open fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measuredW = MeasureSpec.getSize(widthMeasureSpec)
        measuredH = MeasureSpec.getSize(heightMeasureSpec)
    }

    open fun layout(l: Int, t: Int, r: Int, b: Int) {}

    fun getMeasuredWidth(): Int = measuredW

    fun getMeasuredHeight(): Int = measuredH

    open fun setLayerType(layerType: Int, paint: android.graphics.Paint?) {}

    open fun requestFocus(): Boolean = false

    fun interface OnClickListener {
        fun onClick(v: View)
    }

    class MeasureSpec {
        companion object {
            const val UNSPECIFIED = 0
            const val EXACTLY = 1 shl 30
            const val AT_MOST = 2 shl 30
            private const val MODE_MASK = 3 shl 30

            @JvmStatic fun makeMeasureSpec(size: Int, mode: Int): Int = (size and MODE_MASK.inv()) or (mode and MODE_MASK)

            @JvmStatic fun getSize(measureSpec: Int): Int = measureSpec and MODE_MASK.inv()

            @JvmStatic fun getMode(measureSpec: Int): Int = measureSpec and MODE_MASK
        }
    }

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
        const val LAYER_TYPE_NONE = 0
        const val LAYER_TYPE_SOFTWARE = 1
        const val LAYER_TYPE_HARDWARE = 2
    }
}

interface ViewParent

open class ViewGroup(context: Context?) : View(context), ViewParent {
    private val children = ArrayList<View>()

    open fun addView(child: View) {
        children += child
        child.parentGroup = this
    }

    open fun addView(child: View, params: LayoutParams?) {
        child.setLayoutParams(params)
        addView(child)
    }

    open fun removeView(child: View) {
        children -= child
        child.parentGroup = null
    }

    open fun removeAllViews() {
        children.forEach { it.parentGroup = null }
        children.clear()
    }

    fun getChildAt(index: Int): View? = children.getOrNull(index)

    fun getChildCount(): Int = children.size

    open class LayoutParams(@JvmField var width: Int, @JvmField var height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val FILL_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}
