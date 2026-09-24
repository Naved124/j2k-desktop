package android.text

interface Spanned : CharSequence {
    fun <T> getSpans(start: Int, end: Int, type: Class<T>): Array<T> =
        java.lang.reflect.Array.newInstance(type, 0) as Array<T>
    fun getSpanStart(tag: Any?): Int = -1
    fun getSpanEnd(tag: Any?): Int = -1
    fun getSpanFlags(tag: Any?): Int = 0
}

interface Spannable : Spanned {
    fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {}
    fun removeSpan(what: Any?) {}
}

open class SpannedString(private val text: CharSequence) : Spanned, CharSequence by text.toString() {
    override fun toString(): String = text.toString()
}

open class SpannableString(private val text: CharSequence) : Spannable, CharSequence by text.toString() {
    override fun toString(): String = text.toString()
}

object Html {
    const val FROM_HTML_MODE_LEGACY = 0
    const val FROM_HTML_MODE_COMPACT = 63
    const val TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0

    @JvmStatic
    fun fromHtml(source: String?): Spanned = fromHtml(source, FROM_HTML_MODE_LEGACY)

    /** Plain text from HTML: line breaks for br/p/div/li, tags removed, entities decoded. */
    @JvmStatic
    fun fromHtml(source: String?, flags: Int): Spanned {
        var s = source ?: ""
        s = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|h[1-6]|li|tr)>"), "\n")
        s = s.replace(Regex("(?i)<li[^>]*>"), "• ")
        s = s.replace(Regex("<[^>]+>"), "")
        s = decodeEntities(s)
        s = s.replace(Regex("\n{3,}"), "\n\n").trim()
        return SpannedString(s)
    }

    @JvmStatic
    fun escapeHtml(text: CharSequence): String =
        text.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "copy" to "©", "reg" to "®", "trade" to "™",
    )

    private fun decodeEntities(s: String): String =
        Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> named[body] ?: m.value
            }
        }
}
