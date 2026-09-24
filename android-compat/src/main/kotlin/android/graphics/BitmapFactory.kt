package android.graphics

import java.io.InputStream

object BitmapFactory {
    class Options {
        @JvmField var inMutable: Boolean = false
        @JvmField var inJustDecodeBounds: Boolean = false
        @JvmField var inSampleSize: Int = 1
        @JvmField var inPreferredConfig: Bitmap.Config? = Bitmap.Config.ARGB_8888
        @JvmField var inScaled: Boolean = true
        @JvmField var outWidth: Int = 0
        @JvmField var outHeight: Int = 0
        @JvmField var outMimeType: String? = null
    }

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        return decode(bytes, opts)
    }

    @JvmStatic
    fun decodeStream(stream: InputStream?): Bitmap? = decodeStream(stream, null, null)

    @JvmStatic
    fun decodeStream(stream: InputStream?, outPadding: Rect?, opts: Options?): Bitmap? {
        if (stream == null) return null
        return decode(stream.readBytes(), opts)
    }

    @JvmStatic
    fun decodeFile(pathName: String): Bitmap? = decodeFile(pathName, null)

    @JvmStatic
    fun decodeFile(pathName: String, opts: Options?): Bitmap? =
        runCatching { java.io.File(pathName).readBytes() }.getOrNull()?.let { decode(it, opts) }

    private fun decode(bytes: ByteArray, opts: Options?): Bitmap? {
        val image = decodeImage(bytes) ?: return null
        var bitmap = Bitmap.wrap(image)
        val sample = opts?.inSampleSize ?: 1
        if (sample > 1) {
            bitmap = Bitmap.createScaledBitmap(bitmap, image.width / sample, image.height / sample, true)
        }
        if (opts != null) {
            opts.outWidth = bitmap.getWidth()
            opts.outHeight = bitmap.getHeight()
            if (opts.inJustDecodeBounds) return null
        }
        return bitmap
    }
}
