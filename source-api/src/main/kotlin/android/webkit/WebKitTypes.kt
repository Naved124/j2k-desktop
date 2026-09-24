package android.webkit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import dev.naved.j2kdesktop.browser.Browser
import java.io.InputStream

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

    open fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url: String? = request?.getUrl()?.toString()
        return shouldOverrideUrlLoading(view, url)
    }

    open fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}

    open fun onPageFinished(view: WebView?, url: String?) {}

    open fun onPageCommitVisible(view: WebView?, url: String?) {}

    open fun onLoadResource(view: WebView?, url: String?) {}

    open fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? = null

    open fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url: String? = request?.getUrl()?.toString()
        return shouldInterceptRequest(view, url)
    }

    open fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {}

    open fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame() == true) {
            onReceivedError(view, error?.getErrorCode() ?: ERROR_UNKNOWN, error?.getDescription()?.toString(), request.getUrl().toString())
        }
    }

    open fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {}

    open fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }

    open fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = false

    open fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {}

    companion object {
        const val ERROR_UNKNOWN = -1
        const val ERROR_HOST_LOOKUP = -2
        const val ERROR_CONNECT = -6
        const val ERROR_IO = -7
        const val ERROR_TIMEOUT = -8
        const val ERROR_FAILED_SSL_HANDSHAKE = -11
    }
}

open class WebChromeClient {
    open fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(), consoleMessage.sourceId())
        return false
    }

    open fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {}

    open fun onProgressChanged(view: WebView?, newProgress: Int) {}

    open fun onReceivedTitle(view: WebView?, title: String?) {}

    open fun onCloseWindow(window: WebView?) {}
}

/** Settings the desktop WebView honours: User-Agent and blocking images/network. The rest are stored only. */
open class WebSettings internal constructor(private val onUserAgentChanged: () -> Unit) {
    var javaScriptEnabled: Boolean = false
    var domStorageEnabled: Boolean = false
    var databaseEnabled: Boolean = false
    var blockNetworkImage: Boolean = false
    var blockNetworkLoads: Boolean = false
    var loadsImagesAutomatically: Boolean = true
    var loadWithOverviewMode: Boolean = false
    var useWideViewPort: Boolean = false
    var cacheMode: Int = LOAD_DEFAULT
    var mixedContentMode: Int = MIXED_CONTENT_NEVER_ALLOW
    var mediaPlaybackRequiresUserGesture: Boolean = true
    var javaScriptCanOpenWindowsAutomatically: Boolean = false
    var builtInZoomControls: Boolean = false
    var displayZoomControls: Boolean = true
    var allowFileAccess: Boolean = false
    var allowContentAccess: Boolean = true
    var textZoom: Int = 100
    var defaultTextEncodingName: String = "UTF-8"
    var offscreenPreRaster: Boolean = false
    var safeBrowsingEnabled: Boolean = true

    var userAgentString: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            onUserAgentChanged()
        }

    private var zoomSupported = true
    private var multipleWindows = false

    fun setSupportZoom(support: Boolean) {
        zoomSupported = support
    }

    fun supportZoom(): Boolean = zoomSupported

    fun setSupportMultipleWindows(support: Boolean) {
        multipleWindows = support
    }

    fun supportMultipleWindows(): Boolean = multipleWindows

    fun setGeolocationEnabled(flag: Boolean) {}
    fun setAppCacheEnabled(flag: Boolean) {}
    fun setRenderPriority(priority: Any?) {}
    fun setLayoutAlgorithm(algorithm: Any?) {}
    fun setSaveFormData(save: Boolean) {}
    fun setSavePassword(save: Boolean) {}
    fun setAllowFileAccessFromFileURLs(flag: Boolean) {}
    fun setAllowUniversalAccessFromFileURLs(flag: Boolean) {}
    fun setNeedInitialFocus(flag: Boolean) {}

    /** What the tab actually sends. */
    internal fun effectiveUserAgent(): String = userAgentString ?: Browser.userAgent

    companion object {
        const val LOAD_DEFAULT = -1
        const val LOAD_NORMAL = 0
        const val LOAD_CACHE_ELSE_NETWORK = 1
        const val LOAD_NO_CACHE = 2
        const val LOAD_CACHE_ONLY = 3
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE = 2

        @JvmStatic
        fun getDefaultUserAgent(context: Context?): String = Browser.userAgent
    }
}

interface WebResourceRequest {
    fun getUrl(): Uri
    fun isForMainFrame(): Boolean
    fun isRedirect(): Boolean
    fun hasGesture(): Boolean
    fun getMethod(): String
    fun getRequestHeaders(): Map<String, String>
}

internal class DesktopResourceRequest(
    private val url: Uri,
    private val method: String,
    private val headers: Map<String, String>,
    private val mainFrame: Boolean,
    private val redirect: Boolean,
) : WebResourceRequest {
    override fun getUrl(): Uri = url
    override fun isForMainFrame(): Boolean = mainFrame
    override fun isRedirect(): Boolean = redirect
    override fun hasGesture(): Boolean = false
    override fun getMethod(): String = method
    override fun getRequestHeaders(): Map<String, String> = headers
}

open class WebResourceResponse {
    private var mMimeType: String?
    private var mEncoding: String?
    private var mStatusCode: Int = 200
    private var mReasonPhrase: String = "OK"
    private var mHeaders: Map<String, String>? = null
    private var mData: InputStream?

    constructor(mimeType: String?, encoding: String?, data: InputStream?) {
        mMimeType = mimeType
        mEncoding = encoding
        mData = data
    }

    constructor(
        mimeType: String?,
        encoding: String?,
        statusCode: Int,
        reasonPhrase: String,
        responseHeaders: Map<String, String>?,
        data: InputStream?,
    ) {
        mMimeType = mimeType
        mEncoding = encoding
        mStatusCode = statusCode
        mReasonPhrase = reasonPhrase
        mHeaders = responseHeaders
        mData = data
    }

    fun getMimeType(): String? = mMimeType
    fun setMimeType(mimeType: String?) {
        mMimeType = mimeType
    }
    fun getEncoding(): String? = mEncoding
    fun setEncoding(encoding: String?) {
        mEncoding = encoding
    }
    fun getStatusCode(): Int = mStatusCode
    fun getReasonPhrase(): String = mReasonPhrase
    fun setStatusCodeAndReasonPhrase(statusCode: Int, reasonPhrase: String) {
        mStatusCode = statusCode
        mReasonPhrase = reasonPhrase
    }
    fun getResponseHeaders(): Map<String, String>? = mHeaders
    fun setResponseHeaders(headers: Map<String, String>?) {
        mHeaders = headers
    }
    fun getData(): InputStream? = mData
    fun setData(data: InputStream?) {
        mData = data
    }
}

abstract class WebResourceError {
    abstract fun getErrorCode(): Int
    abstract fun getDescription(): CharSequence
}

internal class DesktopResourceError(private val code: Int, private val text: String) : WebResourceError() {
    override fun getErrorCode(): Int = code
    override fun getDescription(): CharSequence = text
}

open class ConsoleMessage(
    private val message: String?,
    private val sourceId: String?,
    private val lineNumber: Int,
    private val level: MessageLevel,
) {
    enum class MessageLevel { TIP, LOG, WARNING, ERROR, DEBUG }

    fun message(): String? = message
    fun sourceId(): String? = sourceId
    fun lineNumber(): Int = lineNumber
    fun messageLevel(): MessageLevel = level
}

abstract class RenderProcessGoneDetail {
    abstract fun didCrash(): Boolean
    abstract fun rendererPriorityAtExit(): Int
}

internal class DesktopRenderProcessGone : RenderProcessGoneDetail() {
    override fun didCrash(): Boolean = true
    override fun rendererPriorityAtExit(): Int = 0
}

open class SslErrorHandler {
    open fun proceed() {}
    open fun cancel() {}
}

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JavascriptInterface

object URLUtil {
    @JvmStatic
    fun isValidUrl(url: String?): Boolean =
        url != null && Regex("^(https?|file|data|about|javascript|content):.*", RegexOption.IGNORE_CASE).matches(url)

    @JvmStatic fun isNetworkUrl(url: String?): Boolean = isHttpUrl(url) || isHttpsUrl(url)

    @JvmStatic fun isHttpUrl(url: String?): Boolean = url?.startsWith("http://", ignoreCase = true) == true

    @JvmStatic fun isHttpsUrl(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true

    @JvmStatic fun isDataUrl(url: String?): Boolean = url?.startsWith("data:", ignoreCase = true) == true

    @JvmStatic
    fun guessFileName(url: String?, contentDisposition: String?, mimeType: String?): String {
        contentDisposition?.let { Regex("filename\\*?=\"?([^\";]+)").find(it)?.groupValues?.get(1) }?.let { return it }
        val last = url?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return last ?: "downloadfile.bin"
    }
}
