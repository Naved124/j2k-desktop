package android.os

/** Pretends to be a recent Android phone; a few extensions read these. */
object Build {
    @JvmField val BRAND: String = "google"
    @JvmField val MANUFACTURER: String = "Google"
    @JvmField val MODEL: String = "Pixel 8"
    @JvmField val DEVICE: String = "shiba"
    @JvmField val PRODUCT: String = "shiba"
    @JvmField val ID: String = "AP2A.240805.005"
    @JvmField val FINGERPRINT: String = "google/shiba/shiba:14/AP2A.240805.005/12025142:user/release-keys"

    object VERSION {
        @JvmField val SDK_INT: Int = 34
        @JvmField val RELEASE: String = "14"
        @JvmField val CODENAME: String = "REL"
        @JvmField val INCREMENTAL: String = "12025142"
    }

    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val M = 23
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}
