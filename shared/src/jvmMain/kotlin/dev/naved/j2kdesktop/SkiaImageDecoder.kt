package dev.naved.j2kdesktop

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes images for extensions' android.graphics.BitmapFactory with Skia, which (unlike ImageIO)
 * reads WebP. Extensions use this when they unscramble page images.
 */
object SkiaImageDecoder {
    fun decode(bytes: ByteArray): BufferedImage? {
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        val w = image.width
        val h = image.height
        val bitmap = Bitmap.makeFromImage(image)
        // BGRA little-endian bytes == ARGB ints, which is what TYPE_INT_ARGB stores
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val pixels = bitmap.readPixels(dstInfo = info) ?: return null
        bitmap.close()
        image.close()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val data = (out.raster.dataBuffer as DataBufferInt).data
        ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(data)
        return out
    }
}
