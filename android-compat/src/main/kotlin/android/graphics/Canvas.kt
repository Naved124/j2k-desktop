package android.graphics

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Android's Canvas drawing into a Bitmap, using Java2D. */
class Canvas() {
    private var bitmap: Bitmap? = null
    private var g: Graphics2D? = null
    private val saved = ArrayList<Pair<AffineTransform, Shape?>>()

    constructor(bitmap: Bitmap) : this() {
        setBitmap(bitmap)
    }

    fun setBitmap(bitmap: Bitmap?) {
        g?.dispose()
        this.bitmap = bitmap
        g = bitmap?.image?.createGraphics()?.apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }
        saved.clear()
    }

    private fun graphics(): Graphics2D = g ?: error("Canvas has no bitmap")

    fun getWidth(): Int = bitmap?.getWidth() ?: 0

    fun getHeight(): Int = bitmap?.getHeight() ?: 0

    fun isHardwareAccelerated(): Boolean = false

    // ----- State -----

    fun save(): Int {
        val g = graphics()
        saved += AffineTransform(g.transform) to g.clip
        return saved.size
    }

    fun saveLayer(bounds: RectF?, paint: Paint?): Int = save()

    fun restore() {
        val (transform, clip) = saved.removeLastOrNull() ?: return
        val g = graphics()
        g.transform = transform
        g.clip = clip
    }

    fun restoreToCount(count: Int) {
        while (saved.size >= count && saved.isNotEmpty()) restore()
    }

    fun getSaveCount(): Int = saved.size + 1

    fun translate(dx: Float, dy: Float) = graphics().translate(dx.toDouble(), dy.toDouble())

    fun scale(sx: Float, sy: Float) = graphics().scale(sx.toDouble(), sy.toDouble())

    fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        translate(px, py)
        scale(sx, sy)
        translate(-px, -py)
    }

    fun rotate(degrees: Float) = graphics().rotate(Math.toRadians(degrees.toDouble()))

    fun rotate(degrees: Float, px: Float, py: Float) =
        graphics().rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())

    fun concat(matrix: Matrix?) {
        if (matrix != null) graphics().transform(matrix.transform)
    }

    fun setMatrix(matrix: Matrix?) {
        graphics().transform = matrix?.transform?.let { AffineTransform(it) } ?: AffineTransform()
    }

    fun clipRect(rect: Rect): Boolean {
        graphics().clipRect(rect.left, rect.top, rect.width(), rect.height())
        return true
    }

    fun clipRect(rect: RectF): Boolean = clipRect(rect.left, rect.top, rect.right, rect.bottom)

    fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        graphics().clip(Rectangle2D.Float(left, top, right - left, bottom - top))
        return true
    }

    fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        graphics().clipRect(left, top, right - left, bottom - top)
        return true
    }

    // ----- Bitmaps -----

    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        val g = graphics()
        if (left == left.toInt().toFloat() && top == top.toInt().toFloat()) {
            g.drawImage(bitmap.image, left.toInt(), top.toInt(), null)
        } else {
            g.drawImage(bitmap.image, AffineTransform.getTranslateInstance(left.toDouble(), top.toDouble()), null)
        }
    }

    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
        val s = src ?: Rect(0, 0, bitmap.getWidth(), bitmap.getHeight())
        graphics().drawImage(
            bitmap.image,
            dst.left, dst.top, dst.right, dst.bottom,
            s.left, s.top, s.right, s.bottom,
            null,
        )
    }

    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        val s = src ?: Rect(0, 0, bitmap.getWidth(), bitmap.getHeight())
        val sw = s.width().coerceAtLeast(1)
        val sh = s.height().coerceAtLeast(1)
        val t = AffineTransform()
        t.translate(dst.left.toDouble(), dst.top.toDouble())
        t.scale(dst.width().toDouble() / sw, dst.height().toDouble() / sh)
        val g = graphics()
        val old = g.transform
        g.transform(t)
        g.drawImage(bitmap.image, 0, 0, sw, sh, s.left, s.top, s.right, s.bottom, null)
        g.transform = old
    }

    fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        graphics().drawImage(bitmap.image, matrix.transform, null)
    }

    // ----- Colours and shapes -----

    fun drawColor(color: Int) {
        val g = graphics()
        val old = g.transform
        val oldComposite = g.composite
        g.transform = AffineTransform()
        g.composite = AlphaComposite.SrcOver
        g.color = java.awt.Color(color, true)
        g.fillRect(0, 0, getWidth(), getHeight())
        g.composite = oldComposite
        g.transform = old
    }

    fun drawARGB(a: Int, r: Int, g: Int, b: Int) = drawColor(Color.argb(a, r, g, b))

    fun drawRGB(r: Int, g: Int, b: Int) = drawColor(Color.rgb(r, g, b))

    fun drawPaint(paint: Paint) = drawColor(paint.getColor())

    private fun shape(shape: Shape, paint: Paint) {
        val g = graphics()
        g.color = java.awt.Color(paint.getColor(), true)
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            if (paint.isAntiAlias()) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF,
        )
        if (paint.getStyle() != Paint.Style.STROKE) g.fill(shape)
        if (paint.getStyle() != Paint.Style.FILL) {
            g.stroke = BasicStroke(paint.getStrokeWidth().coerceAtLeast(1f))
            g.draw(shape)
        }
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        shape(Rectangle2D.Float(left, top, right - left, bottom - top), paint)

    fun drawRect(rect: Rect, paint: Paint) =
        drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)

    fun drawRect(rect: RectF, paint: Paint) = drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) =
        shape(RoundRectangle2D.Float(rect.left, rect.top, rect.width(), rect.height(), rx * 2, ry * 2), paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
        shape(Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), paint)

    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        val g = graphics()
        g.color = java.awt.Color(paint.getColor(), true)
        g.stroke = BasicStroke(paint.getStrokeWidth().coerceAtLeast(1f))
        g.draw(Line2D.Float(startX, startY, stopX, stopY))
    }

    // ----- Text -----

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        val g = graphics()
        g.font = paint.awtFont()
        g.color = java.awt.Color(paint.getColor(), true)
        val width = g.fontMetrics.stringWidth(text)
        val left = when (paint.getTextAlign()) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - width / 2f
            Paint.Align.RIGHT -> x - width
        }
        if (paint.getStyle() == Paint.Style.FILL) {
            g.drawString(text, left, y)
        } else {
            val outline = g.font.createGlyphVector(g.fontRenderContext, text).getOutline(left, y)
            shape(outline, paint)
        }
    }

    fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) =
        drawText(text.subSequence(start, end).toString(), x, y, paint)

    internal fun awtGraphics(): Graphics2D = graphics()

    companion object {
        /** For measuring text without a bitmap. */
        internal val measureGraphics: Graphics2D by lazy {
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            }
        }
    }
}
