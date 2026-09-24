package android.webkit

import android.os.Handler
import android.os.Looper
import dev.naved.j2kdesktop.browser.Browser
import dev.naved.j2kdesktop.browser.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The app's one cookie store (the same one OkHttp and the browser use), like on Android. */
abstract class CookieManager {
    abstract fun setAcceptCookie(accept: Boolean)
    abstract fun acceptCookie(): Boolean
    abstract fun setAcceptThirdPartyCookies(webview: WebView?, accept: Boolean)
    abstract fun acceptThirdPartyCookies(webview: WebView?): Boolean
    abstract fun setCookie(url: String?, value: String?)
    abstract fun setCookie(url: String?, value: String?, callback: ValueCallback<Boolean>?)
    abstract fun getCookie(url: String?): String?
    abstract fun removeSessionCookies(callback: ValueCallback<Boolean>?)
    abstract fun removeAllCookies(callback: ValueCallback<Boolean>?)
    abstract fun hasCookies(): Boolean
    abstract fun flush()

    open fun removeSessionCookie() = removeSessionCookies(null)
    open fun removeAllCookie() = removeAllCookies(null)
    open fun removeExpiredCookie() {}

    companion object {
        @JvmStatic
        fun getInstance(): CookieManager = DesktopCookieManager

        @JvmStatic
        fun allowFileSchemeCookies(): Boolean = false

        @JvmStatic
        fun setAcceptFileSchemeCookies(accept: Boolean) {}
    }
}

internal object DesktopCookieManager : CookieManager() {
    @Volatile
    private var accept = true
    private val main = Handler(Looper.getMainLooper())

    override fun setAcceptCookie(accept: Boolean) {
        this.accept = accept
    }

    override fun acceptCookie(): Boolean = accept

    override fun setAcceptThirdPartyCookies(webview: WebView?, accept: Boolean) {}

    override fun acceptThirdPartyCookies(webview: WebView?): Boolean = true

    override fun setCookie(url: String?, value: String?) = setCookie(url, value, null)

    override fun setCookie(url: String?, value: String?, callback: ValueCallback<Boolean>?) {
        val parsedUrl = url?.let { if ("://" in it) it else "https://$it" }?.toHttpUrlOrNull()
        val cookie = if (parsedUrl != null && value != null) {
            Cookie.parse(parsedUrl, value) ?: run {
                // e.g. a Domain attribute that doesn't match the url: keep it for the url's host
                val name = value.substringBefore('=').trim()
                val cookieValue = value.substringAfter('=', "").substringBefore(';').trim()
                if (name.isEmpty()) null else runCatching {
                    Cookie.Builder().name(name).value(cookieValue).hostOnlyDomain(parsedUrl.host).path("/").build()
                }.getOrNull()
            }
        } else {
            null
        }
        if (cookie != null) CookieStore.add(listOf(cookie))
        callback?.let { cb -> main.post { cb.onReceiveValue(cookie != null) } }
    }

    override fun getCookie(url: String?): String? {
        val full = url?.let { if ("://" in it) it else "https://$it" } ?: return null
        return CookieStore.header(full)
    }

    override fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        CookieStore.removeAll { !it.persistent }
        callback?.let { cb -> main.post { cb.onReceiveValue(true) } }
    }

    override fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        CookieStore.removeAll { true }
        Browser.clearCookies()
        callback?.let { cb -> main.post { cb.onReceiveValue(true) } }
    }

    override fun hasCookies(): Boolean = CookieStore.all().isNotEmpty()

    override fun flush() {}
}
