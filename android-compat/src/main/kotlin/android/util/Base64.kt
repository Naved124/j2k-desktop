package android.util

/** android.util.Base64 on top of java.util.Base64 (same flags, same line wrapping). */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        val out = encoder.encodeToString(input)
        if (flags and NO_WRAP != 0 || out.isEmpty()) return out
        // Android wraps every 76 chars and ends with a newline unless NO_WRAP is set
        val newline = if (flags and CRLF != 0) "\r\n" else "\n"
        return out.chunked(76).joinToString(newline) + newline
    }

    @JvmStatic
    fun encodeToString(input: ByteArray, offset: Int, len: Int, flags: Int): String =
        encodeToString(input.copyOfRange(offset, offset + len), flags)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).toByteArray(Charsets.US_ASCII)

    @JvmStatic
    fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray =
        encode(input.copyOfRange(offset, offset + len), flags)

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = decode(str.toByteArray(Charsets.US_ASCII), flags)

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray = decode(input, 0, input.size, flags)

    @JvmStatic
    fun decode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        // Android's decoder is lenient: ignores whitespace and missing padding
        val cleaned = String(input, offset, len, Charsets.US_ASCII)
            .filterNot { it.isWhitespace() }
            .trimEnd('=')
            .replace('-', '+')
            .replace('_', '/')
        return java.util.Base64.getDecoder().decode(cleaned)
    }
}
