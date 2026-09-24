package android.text

import android.graphics.Canvas
import android.graphics.Paint
import java.awt.font.TextLayout

/** Text wrapped to a width, the way Android's StaticLayout does it (Java2D underneath). */
abstract class Layout protected constructor(
    private val mText: CharSequence,
    private val mPaint: TextPaint,
    private val mWidth: Int,
    private val mAlign: Alignment,
    private val mSpacingMult: Float,
    private val mSpacingAdd: Float,
) {
    enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER, ALIGN_LEFT, ALIGN_RIGHT }

    private val font = mPaint.awtFont()
    private val metrics = Canvas.measureGraphics.getFontMetrics(font)
    private val lines: List<String> = wrap()
    private val lineHeight: Float = (metrics.ascent + metrics.descent + metrics.leading) * mSpacingMult + mSpacingAdd

    fun getText(): CharSequence = mText
    fun getPaint(): TextPaint = mPaint
    fun getWidth(): Int = mWidth
    fun getAlignment(): Alignment = mAlign
    fun getSpacingMultiplier(): Float = mSpacingMult
    fun getSpacingAdd(): Float = mSpacingAdd
    fun getLineCount(): Int = lines.size
    fun getHeight(): Int = kotlin.math.ceil(lines.size * lineHeight).toInt()
    fun getLineTop(line: Int): Int = (line * lineHeight).toInt()
    fun getLineBottom(line: Int): Int = ((line + 1) * lineHeight).toInt()
    fun getLineBaseline(line: Int): Int = (line * lineHeight + metrics.ascent).toInt()
    fun getLineWidth(line: Int): Float = metrics.stringWidth(lines.getOrElse(line) { "" }).toFloat()

    private fun widthOf(s: String) = metrics.stringWidth(s)

    private fun wrap(): List<String> {
        val result = ArrayList<String>()
        val max = mWidth.coerceAtLeast(1)
        for (paragraph in mText.toString().split('\n')) {
            if (paragraph.isEmpty()) {
                result += ""
                continue
            }
            var line = StringBuilder()
            // Split into words but keep spaces; very long words (or CJK text) break per character
            val tokens = Regex("\\S+\\s*|\\s+").findAll(paragraph).map { it.value }
            for (token in tokens) {
                if (widthOf(line.toString() + token.trimEnd()) <= max) {
                    line.append(token)
                    continue
                }
                if (line.isNotEmpty()) {
                    result += line.toString().trimEnd()
                    line = StringBuilder()
                }
                if (widthOf(token.trimEnd()) <= max) {
                    line.append(token)
                } else {
                    for (ch in token) {
                        if (line.isNotEmpty() && widthOf(line.toString() + ch) > max) {
                            result += line.toString().trimEnd()
                            line = StringBuilder()
                        }
                        line.append(ch)
                    }
                }
            }
            result += line.toString().trimEnd()
        }
        return result
    }

    fun draw(canvas: Canvas) {
        val g = canvas.awtGraphics()
        g.font = font
        g.color = java.awt.Color(mPaint.getColor(), true)
        lines.forEachIndexed { i, line ->
            if (line.isEmpty()) return@forEachIndexed
            val w = widthOf(line)
            val x = when (mAlign) {
                Alignment.ALIGN_CENTER -> (mWidth - w) / 2f
                Alignment.ALIGN_OPPOSITE, Alignment.ALIGN_RIGHT -> (mWidth - w).toFloat()
                else -> 0f
            }
            val y = i * lineHeight + metrics.ascent
            if (mPaint.getStyle() == Paint.Style.FILL) {
                g.drawString(line, x, y)
            } else {
                val shape = TextLayout(line, font, g.fontRenderContext).getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
                g.stroke = java.awt.BasicStroke(mPaint.getStrokeWidth().coerceAtLeast(1f))
                g.draw(shape)
                if (mPaint.getStyle() == Paint.Style.FILL_AND_STROKE) g.fill(shape)
            }
        }
    }
}

class StaticLayout : Layout {
    constructor(
        source: CharSequence,
        paint: TextPaint,
        width: Int,
        align: Alignment,
        spacingmult: Float,
        spacingadd: Float,
        includepad: Boolean,
    ) : super(source, paint, width, align, spacingmult, spacingadd)

    constructor(
        source: CharSequence,
        bufstart: Int,
        bufend: Int,
        paint: TextPaint,
        outerwidth: Int,
        align: Alignment,
        spacingmult: Float,
        spacingadd: Float,
        includepad: Boolean,
    ) : super(source.subSequence(bufstart, bufend), paint, outerwidth, align, spacingmult, spacingadd)

    class Builder private constructor(
        private val text: CharSequence,
        private val paint: TextPaint,
        private val width: Int,
    ) {
        private var align = Alignment.ALIGN_NORMAL
        private var spacingMult = 1f
        private var spacingAdd = 0f

        fun setAlignment(alignment: Alignment) = apply { align = alignment }
        fun setBreakStrategy(breakStrategy: Int) = this
        fun setHyphenationFrequency(hyphenationFrequency: Int) = this
        fun setIncludePad(includePad: Boolean) = this
        fun setLineSpacing(spacingAdd: Float, spacingMult: Float) = apply {
            this.spacingAdd = spacingAdd
            this.spacingMult = spacingMult
        }
        fun setMaxLines(maxLines: Int) = this
        fun setTextDirection(textDir: Any?) = this
        fun setJustificationMode(justificationMode: Int) = this

        fun build(): StaticLayout = StaticLayout(text, paint, width, align, spacingMult, spacingAdd, true)

        companion object {
            @JvmStatic
            fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int): Builder =
                Builder(source.subSequence(start, end), paint, width)
        }
    }
}
