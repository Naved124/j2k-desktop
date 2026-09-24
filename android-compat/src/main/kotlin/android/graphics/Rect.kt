package android.graphics

class Rect() {
    @JvmField var left: Int = 0
    @JvmField var top: Int = 0
    @JvmField var right: Int = 0
    @JvmField var bottom: Int = 0

    constructor(left: Int, top: Int, right: Int, bottom: Int) : this() {
        set(left, top, right, bottom)
    }

    constructor(r: Rect?) : this() {
        if (r != null) set(r.left, r.top, r.right, r.bottom)
    }

    fun set(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun set(src: Rect) = set(src.left, src.top, src.right, src.bottom)

    fun setEmpty() = set(0, 0, 0, 0)

    fun isEmpty(): Boolean = left >= right || top >= bottom

    fun width(): Int = right - left

    fun height(): Int = bottom - top

    fun centerX(): Int = (left + right) shr 1

    fun centerY(): Int = (top + bottom) shr 1

    fun exactCenterX(): Float = (left + right) * 0.5f

    fun exactCenterY(): Float = (top + bottom) * 0.5f

    fun offset(dx: Int, dy: Int) {
        left += dx
        right += dx
        top += dy
        bottom += dy
    }

    fun offsetTo(newLeft: Int, newTop: Int) = offset(newLeft - left, newTop - top)

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    override fun equals(other: Any?) =
        other is Rect && left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode() = ((left * 31 + top) * 31 + right) * 31 + bottom

    override fun toString() = "Rect($left, $top - $right, $bottom)"
}

class RectF() {
    @JvmField var left: Float = 0f
    @JvmField var top: Float = 0f
    @JvmField var right: Float = 0f
    @JvmField var bottom: Float = 0f

    constructor(left: Float, top: Float, right: Float, bottom: Float) : this() {
        set(left, top, right, bottom)
    }

    constructor(r: Rect?) : this() {
        if (r != null) set(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    }

    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun width(): Float = right - left

    fun height(): Float = bottom - top

    fun centerX(): Float = (left + right) * 0.5f

    fun centerY(): Float = (top + bottom) * 0.5f

    override fun toString() = "RectF($left, $top, $right, $bottom)"
}
