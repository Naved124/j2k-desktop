package android.graphics

import dev.naved.j2kdesktop.compat.AndroidCompat
import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Android's Bitmap on top of an AWT image (always ARGB_8888 inside).
 * Extensions use it to unscramble page images: decode, cut into tiles, redraw, compress.
 */
class Bitmap internal constructor(image: BufferedImage) {
    var image: BufferedImage = image
        internal set

    private var mutable = true

    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102 }

    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    fun getWidth(): Int = image.width

    fun getHeight(): Int = image.height

    fun getConfig(): Config = Config.ARGB_8888

    fun isMutable(): Boolean = mutable

    fun isRecycled(): Boolean = false

    fun hasAlpha(): Boolean = true

    fun getByteCount(): Int = image.width * image.height * 4

    fun getAllocationByteCount(): Int = getByteCount()

    fun getRowBytes(): Int = image.width * 4

    fun recycle() {}

    fun eraseColor(color: Int) {
        val g = image.createGraphics()
        g.composite = java.awt.AlphaComposite.Src
        g.color = java.awt.Color(color, true)
        g.fillRect(0, 0, image.width, image.height)
        g.dispose()
    }

    fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)

    fun setPixel(x: Int, y: Int, color: Int) = image.setRGB(x, y, color)

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        image.getRGB(x, y, width, height, pixels, offset, stride)
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        image.setRGB(x, y, width, height, pixels, offset, stride)
    }

    fun copy(config: Config?, isMutable: Boolean): Bitmap {
        val copy = Bitmap(copyImage(image))
        copy.mutable = isMutable
        return copy
    }

    /** JPEG/PNG through ImageIO. WebP has no Java encoder, so it's written as JPEG; the reader sniffs the format anyway. */
    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        return when (format) {
            CompressFormat.PNG, CompressFormat.WEBP_LOSSLESS -> ImageIO.write(image, "png", stream)
            else -> writeJpeg(stream, quality)
        }
    }

    private fun writeJpeg(stream: OutputStream, quality: Int): Boolean {
        // JPEG has no alpha: flatten onto white first
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, rgb.width, rgb.height)
        g.drawImage(image, 0, 0, null)
        g.dispose()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        return try {
            ImageIO.createImageOutputStream(stream).use { out ->
                writer.output = out
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = (quality.coerceIn(1, 100) / 100f)
                }
                writer.write(null, IIOImage(rgb, null, null), params)
                out.flush()
            }
            true
        } finally {
            writer.dispose()
        }
    }

    companion object {
        internal fun blank(width: Int, height: Int) =
            Bitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))

        internal fun copyImage(src: BufferedImage): BufferedImage {
            val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.composite = java.awt.AlphaComposite.Src
            g.drawImage(src, 0, 0, null)
            g.dispose()
            return out
        }

        /** Wraps an image the app decoded (Skia or ImageIO), converting it to ARGB if needed. */
        fun wrap(image: BufferedImage): Bitmap =
            Bitmap(if (image.type == BufferedImage.TYPE_INT_ARGB) image else copyImage(image))

        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config?): Bitmap = blank(width, height)

        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config?, hasAlpha: Boolean): Bitmap = blank(width, height)

        @JvmStatic
        fun createBitmap(source: Bitmap): Bitmap = Bitmap(copyImage(source.image))

        @JvmStatic
        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap =
            Bitmap(copyImage(source.image.getSubimage(x, y, width, height)))

        @JvmStatic
        fun createBitmap(
            source: Bitmap,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            matrix: Matrix?,
            filter: Boolean,
        ): Bitmap {
            val cut = createBitmap(source, x, y, width, height)
            if (matrix == null || matrix.isIdentity()) return cut
            val bounds = matrix.transform.createTransformedShape(java.awt.Rectangle(0, 0, width, height)).bounds
            val out = blank(bounds.width, bounds.height)
            val g = out.image.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.translate(-bounds.x.toDouble(), -bounds.y.toDouble())
            g.drawImage(cut.image, matrix.transform, null)
            g.dispose()
            return out
        }

        @JvmStatic
        fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config?): Bitmap {
            val bitmap = blank(width, height)
            bitmap.image.setRGB(0, 0, width, height, colors, 0, width)
            return bitmap
        }

        @JvmStatic
        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            val out = blank(dstWidth, dstHeight)
            val g = out.image.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                if (filter) java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR else java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            g.drawImage(src.image, 0, 0, dstWidth, dstHeight, null)
            g.dispose()
            return out
        }
    }
}

/** Decodes with the app's decoder (Skia: JPEG, PNG, WebP, GIF...) and falls back to ImageIO. */
internal fun decodeImage(bytes: ByteArray): BufferedImage? {
    AndroidCompat.imageDecoder?.let { decode ->
        runCatching { decode(bytes) }.getOrNull()?.let { return it }
    }
    return runCatching { ImageIO.read(bytes.inputStream()) }.getOrNull()
}
