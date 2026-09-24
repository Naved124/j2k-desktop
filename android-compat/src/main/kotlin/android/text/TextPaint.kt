package android.text

import android.graphics.Paint

open class TextPaint : Paint {
    @JvmField var bgColor: Int = 0
    @JvmField var baselineShift: Int = 0
    @JvmField var linkColor: Int = 0
    @JvmField var density: Float = 1f
    @JvmField var drawableState: IntArray? = null
    @JvmField var underlineColor: Int = 0

    constructor() : super()
    constructor(flags: Int) : super(flags)
    constructor(p: Paint?) : super(p)
}
