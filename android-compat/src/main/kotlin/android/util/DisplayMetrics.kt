package android.util

class DisplayMetrics {
    @JvmField var widthPixels: Int = 1920
    @JvmField var heightPixels: Int = 1080
    @JvmField var density: Float = 1f
    @JvmField var densityDpi: Int = 160
    @JvmField var scaledDensity: Float = 1f
    @JvmField var xdpi: Float = 160f
    @JvmField var ydpi: Float = 160f

    companion object {
        const val DENSITY_DEFAULT = 160

        internal fun fromScreen() = DisplayMetrics().apply {
            runCatching { java.awt.Toolkit.getDefaultToolkit().screenSize }.getOrNull()?.let {
                if (it.width > 0 && it.height > 0) {
                    widthPixels = it.width
                    heightPixels = it.height
                }
            }
        }
    }
}
