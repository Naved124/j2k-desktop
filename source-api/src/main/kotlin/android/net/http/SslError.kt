package android.net.http

open class SslError(private val primaryError: Int, private val failingUrl: String?) {
    fun getPrimaryError(): Int = primaryError
    fun getUrl(): String? = failingUrl
    fun hasError(error: Int): Boolean = error == primaryError

    companion object {
        const val SSL_NOTYETVALID = 0
        const val SSL_EXPIRED = 1
        const val SSL_IDMISMATCH = 2
        const val SSL_UNTRUSTED = 3
        const val SSL_DATE_INVALID = 4
        const val SSL_INVALID = 5
    }
}
