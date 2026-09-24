package android.graphics

import java.awt.Font
import java.io.File

class Typeface internal constructor(internal val font: Font) {
    fun isBold(): Boolean = font.isBold

    fun isItalic(): Boolean = font.isItalic

    fun getStyle(): Int = (if (font.isBold) BOLD else 0) or (if (font.isItalic) ITALIC else 0)

    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        @JvmField val DEFAULT = Typeface(Font(Font.SANS_SERIF, Font.PLAIN, 12))
        @JvmField val DEFAULT_BOLD = Typeface(Font(Font.SANS_SERIF, Font.BOLD, 12))
        @JvmField val SANS_SERIF = Typeface(Font(Font.SANS_SERIF, Font.PLAIN, 12))
        @JvmField val SERIF = Typeface(Font(Font.SERIF, Font.PLAIN, 12))
        @JvmField val MONOSPACE = Typeface(Font(Font.MONOSPACED, Font.PLAIN, 12))

        private fun awtStyle(style: Int) =
            (if (style and BOLD != 0) Font.BOLD else 0) or (if (style and ITALIC != 0) Font.ITALIC else 0)

        @JvmStatic
        fun create(family: Typeface?, style: Int): Typeface = Typeface((family ?: DEFAULT).font.deriveFont(awtStyle(style)))

        @JvmStatic
        fun create(familyName: String?, style: Int): Typeface = Typeface(Font(familyName ?: Font.SANS_SERIF, awtStyle(style), 12))

        @JvmStatic
        fun defaultFromStyle(style: Int): Typeface = create(DEFAULT, style)

        @JvmStatic
        fun createFromFile(file: File): Typeface =
            runCatching { Typeface(Font.createFont(Font.TRUETYPE_FONT, file)) }.getOrElse { DEFAULT }

        @JvmStatic
        fun createFromFile(path: String): Typeface = createFromFile(File(path))
    }
}
