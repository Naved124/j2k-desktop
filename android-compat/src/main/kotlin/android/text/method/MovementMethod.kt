package android.text.method

interface MovementMethod

class LinkMovementMethod : MovementMethod {
    companion object {
        private val sharedInstance by lazy { LinkMovementMethod() }

        @JvmStatic
        fun getInstance(): MovementMethod = sharedInstance
    }
}
