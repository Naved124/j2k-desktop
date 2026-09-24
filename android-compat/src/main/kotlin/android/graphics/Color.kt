package android.graphics

object Color {
    const val BLACK = -0x1000000
    const val DKGRAY = -0xbbbbbc
    const val GRAY = -0x777778
    const val LTGRAY = -0x333334
    const val WHITE = -0x1
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val YELLOW = -0x100
    const val CYAN = -0xff0001
    const val MAGENTA = -0xff01
    const val TRANSPARENT = 0

    @JvmStatic fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    @JvmStatic fun rgb(red: Int, green: Int, blue: Int): Int = argb(255, red, green, blue)

    @JvmStatic fun alpha(color: Int): Int = color ushr 24

    @JvmStatic fun red(color: Int): Int = (color shr 16) and 0xFF

    @JvmStatic fun green(color: Int): Int = (color shr 8) and 0xFF

    @JvmStatic fun blue(color: Int): Int = color and 0xFF

    @JvmStatic
    fun parseColor(colorString: String): Int {
        if (colorString.startsWith("#")) {
            var color = colorString.substring(1).toLong(16)
            when (colorString.length) {
                7 -> color = color or 0xFF000000L
                9 -> {}
                else -> throw IllegalArgumentException("Unknown color")
            }
            return color.toInt()
        }
        return when (colorString.lowercase()) {
            "black" -> BLACK
            "darkgray" -> DKGRAY
            "gray", "grey" -> GRAY
            "lightgray", "lightgrey" -> LTGRAY
            "white" -> WHITE
            "red" -> RED
            "green" -> GREEN
            "blue" -> BLUE
            "yellow" -> YELLOW
            "cyan", "aqua" -> CYAN
            "magenta", "fuchsia" -> MAGENTA
            "transparent" -> TRANSPARENT
            else -> throw IllegalArgumentException("Unknown color")
        }
    }
}
