package android.graphics

open class Paint() {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }

    enum class Align { LEFT, CENTER, RIGHT }

    class FontMetrics {
        @JvmField var ascent: Float = 0f
        @JvmField var bottom: Float = 0f
        @JvmField var descent: Float = 0f
        @JvmField var leading: Float = 0f
        @JvmField var top: Float = 0f
    }

    class FontMetricsInt {
        @JvmField var ascent: Int = 0
        @JvmField var bottom: Int = 0
        @JvmField var descent: Int = 0
        @JvmField var leading: Int = 0
        @JvmField var top: Int = 0
    }

    constructor(flags: Int) : this() {
        antiAlias = flags and ANTI_ALIAS_FLAG != 0
    }

    constructor(paint: Paint?) : this() {
        if (paint != null) {
            antiAlias = paint.antiAlias
            color = paint.color
            style = paint.style
            textSize = paint.textSize
            typeface = paint.typeface
            strokeWidth = paint.strokeWidth
            filterBitmap = paint.filterBitmap
            textAlign = paint.textAlign
        }
    }

    private var antiAlias = false
    private var color = 0xFF000000.toInt()
    private var style = Style.FILL
    private var textSize = 12f
    private var typeface: Typeface? = null
    private var strokeWidth = 0f
    private var filterBitmap = false
    private var textAlign = Align.LEFT

    fun isAntiAlias() = antiAlias
    fun setAntiAlias(aa: Boolean) { antiAlias = aa }
    fun isFilterBitmap() = filterBitmap
    fun setFilterBitmap(filter: Boolean) { filterBitmap = filter }
    fun isDither() = false
    fun setDither(dither: Boolean) {}
    fun getColor(): Int = color
    fun setColor(color: Int) { this.color = color }
    fun getAlpha(): Int = color ushr 24
    fun setAlpha(a: Int) { color = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24) }
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }
    fun getStyle(): Style = style
    fun setStyle(style: Style) { this.style = style }
    fun getTextSize(): Float = textSize
    fun setTextSize(size: Float) { textSize = size }
    fun getTypeface(): Typeface? = typeface
    fun setTypeface(typeface: Typeface?): Typeface? {
        this.typeface = typeface
        return typeface
    }
    fun getStrokeWidth(): Float = strokeWidth
    fun setStrokeWidth(width: Float) { strokeWidth = width }
    fun getTextAlign(): Align = textAlign
    fun setTextAlign(align: Align) { textAlign = align }
    fun setFlags(flags: Int) { antiAlias = flags and ANTI_ALIAS_FLAG != 0 }

    internal fun awtFont(): java.awt.Font = (typeface ?: Typeface.DEFAULT).font.deriveFont(textSize)

    private fun awtMetrics(): java.awt.FontMetrics = Canvas.measureGraphics.getFontMetrics(awtFont())

    fun measureText(text: String): Float = awtMetrics().stringWidth(text).toFloat()

    fun measureText(text: CharSequence, start: Int, end: Int): Float = measureText(text.subSequence(start, end).toString())

    fun getFontMetrics(): FontMetrics = FontMetrics().also { getFontMetrics(it) }

    fun getFontMetrics(metrics: FontMetrics?): Float {
        val m = awtMetrics()
        metrics?.apply {
            ascent = -m.ascent.toFloat()
            descent = m.descent.toFloat()
            top = -m.maxAscent.toFloat()
            bottom = m.maxDescent.toFloat()
            leading = m.leading.toFloat()
        }
        return (m.height).toFloat()
    }

    fun getFontMetricsInt(): FontMetricsInt {
        val m = awtMetrics()
        return FontMetricsInt().apply {
            ascent = -m.ascent
            descent = m.descent
            top = -m.maxAscent
            bottom = m.maxDescent
            leading = m.leading
        }
    }

    fun ascent(): Float = -awtMetrics().ascent.toFloat()

    fun descent(): Float = awtMetrics().descent.toFloat()

    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) {
        val m = awtMetrics()
        val width = m.stringWidth(text.substring(start, end))
        bounds.set(0, -m.ascent, width, m.descent)
    }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }
}
