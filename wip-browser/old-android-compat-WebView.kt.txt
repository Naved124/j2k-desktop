package android.webkit

import android.content.Context
import android.graphics.Bitmap
import android.view.View

internal const val NO_WEBVIEW =
    "This source needs a built-in browser (Android WebView) to get past the site's checks. " +
        "J2K Desktop doesn't have one yet; it's planned (embedded Chromium)."

/** Placeholder: creating one fails with a clear message until the embedded browser exists. */
open class WebView(context: Context?) : View(context) {
    init {
        throw UnsupportedOperationException(NO_WEBVIEW)
    }

    open fun getSettings(): WebSettings = throw UnsupportedOperationException(NO_WEBVIEW)
    open fun loadUrl(url: String) {}
    open fun loadUrl(url: String, headers: Map<String, String>) {}
    open fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {}
    open fun addJavascriptInterface(obj: Any, name: String) {}
    open fun setWebViewClient(client: WebViewClient) {}
    open fun setWebChromeClient(client: WebChromeClient?) {}
    open fun stopLoading() {}
    open fun destroy() {}
    open fun getUrl(): String? = null
}

open class WebViewClient {
    open fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
    open fun onPageFinished(view: WebView?, url: String?) {}
    open fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? = null
    open fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {}
}

open class WebChromeClient {
    open fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = false
}

abstract class WebSettings {
    abstract fun getUserAgentString(): String?
    abstract fun setUserAgentString(ua: String?)

    companion object {
        @JvmStatic
        fun getDefaultUserAgent(context: Context?): String =
            eu_default_ua
    }
}

/** Keep in sync with NetworkHelper.DEFAULT_USER_AGENT (android-compat can't depend on source-api). */
private const val eu_default_ua =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

interface WebResourceRequest {
    fun getUrl(): android.net.Uri
    fun getMethod(): String
    fun getRequestHeaders(): Map<String, String>
    fun isForMainFrame(): Boolean
}

open class WebResourceResponse(
    private val mimeType: String?,
    private val encoding: String?,
    private val data: java.io.InputStream?,
) {
    fun getMimeType(): String? = mimeType
    fun getEncoding(): String? = encoding
    fun getData(): java.io.InputStream? = data
}

abstract class WebResourceError {
    abstract fun getErrorCode(): Int
    abstract fun getDescription(): CharSequence
}

open class ConsoleMessage(private val message: String?) {
    fun message(): String? = message
}

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}

abstract class CookieManager {
    abstract fun getCookie(url: String?): String?
    abstract fun setCookie(url: String?, value: String?)

    companion object {
        @JvmStatic
        fun getInstance(): CookieManager = throw UnsupportedOperationException(NO_WEBVIEW)
    }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JavascriptInterface
