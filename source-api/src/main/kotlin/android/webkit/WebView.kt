package android.webkit

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import dev.naved.j2kdesktop.browser.Browser
import dev.naved.j2kdesktop.browser.BrowserLocator
import dev.naved.j2kdesktop.browser.CdpEvent
import dev.naved.j2kdesktop.browser.CdpSession
import dev.naved.j2kdesktop.browser.CookieBridge
import dev.naved.j2kdesktop.browser.int
import dev.naved.j2kdesktop.browser.obj
import dev.naved.j2kdesktop.browser.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Android's WebView, backed by a tab in the hidden browser (Chromium/Chrome/Brave/Edge over DevTools).
 * Calls return immediately and run in order in the background; callbacks arrive on the main thread,
 * like on Android. Supports what extensions use: loading pages, evaluateJavascript,
 * addJavascriptInterface (methods without return values), shouldInterceptRequest, cookies.
 */
open class WebView(context: Context?) : ViewGroup(context) {
    private val webSettings = WebSettings { enqueue { session?.let { applyUserAgent(it) } } }

    @Volatile private var viewClient: WebViewClient = WebViewClient()
    @Volatile private var chromeClient: WebChromeClient? = null

    private val jsInterfaces = ConcurrentHashMap<String, Any>()
    private val interfaceScripts = ConcurrentHashMap<String, String>()

    @Volatile private var pageUrl: String? = null
    @Volatile private var firstUrl: String? = null
    @Volatile private var pageTitle: String? = null
    @Volatile private var loadProgress = 0
    @Volatile private var documentOverride: DocumentOverride? = null
    @Volatile private var requestedUrl: String? = null
    @Volatile private var mainFrameId: String? = null
    private val documentRequests = ConcurrentHashMap<String, String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    @Volatile private var session: CdpSession? = null
    @Volatile private var destroyed = false
    @Volatile private var lastActivity = System.currentTimeMillis()

    private class DocumentOverride(val url: String, val bytes: ByteArray, val mimeType: String, val encoding: String)

    init {
        if (!Browser.isAvailable) throw UnsupportedOperationException(BrowserLocator.NOT_FOUND)
        scope.launch {
            for (op in ops) {
                try {
                    op()
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "WebView operation failed", e)
                }
            }
        }
        // A WebView an extension forgot to destroy shouldn't keep a browser tab forever
        scope.launch {
            while (isActive) {
                delay(60_000)
                if (!destroyed && session != null && System.currentTimeMillis() - lastActivity > 10 * 60_000L) {
                    enqueue { closeTab() }
                }
            }
        }
    }

    private fun enqueue(op: suspend () -> Unit) {
        if (!destroyed) {
            lastActivity = System.currentTimeMillis()
            ops.trySend(op)
        }
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post { runCatching(block).onFailure { Log.e(TAG, "WebView callback failed", it) } }
    }

    // ----- Tab -----

    private suspend fun tab(): CdpSession {
        session?.let { if (!it.isClosed && it.connection.isOpen) return it }
        val s = Browser.newTab()
        session = s
        s.on { onEvent(s, it) }
        mainFrameId = s.send("Page.getFrameTree").obj("frameTree")?.obj("frame")?.string("id")
        applyUserAgent(s)
        s.send(
            "Fetch.enable",
            buildJsonObject {
                putJsonArray("patterns") {
                    addJsonObject {
                        put("urlPattern", "*")
                        put("requestStage", "Request")
                    }
                }
            },
        )
        jsInterfaces.forEach { (name, obj) -> registerInterface(s, name, obj) }
        return s
    }

    private suspend fun closeTab() {
        session?.close()
        session = null
        interfaceScripts.clear()
    }

    private suspend fun applyUserAgent(s: CdpSession) {
        Browser.setUserAgent(s, webSettings.effectiveUserAgent())
    }

    // ----- Loading -----

    open fun loadUrl(url: String) = loadUrl(url, emptyMap())

    open fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        if (url.startsWith("javascript:", ignoreCase = true)) {
            evaluateJavascript(url.substringAfter(':'), null)
            return
        }
        pageUrl = url
        if (firstUrl == null) firstUrl = url
        enqueue {
            val s = tab()
            CookieBridge.toBrowser(s, url)
            s.send(
                "Network.setExtraHTTPHeaders",
                buildJsonObject { put("headers", buildJsonObject { additionalHttpHeaders.forEach { (k, v) -> put(k, v) } }) },
            )
            navigate(s, url)
        }
    }

    open fun postUrl(url: String, postData: ByteArray) = loadUrl(url)

    open fun loadData(data: String, mimeType: String?, encoding: String?) {
        val mime = mimeType ?: "text/html"
        val body = if (encoding.equals("base64", ignoreCase = true)) data else Base64.getEncoder().encodeToString(data.toByteArray())
        enqueue { navigate(tab(), "data:$mime;base64,$body") }
    }

    open fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        val mime = mimeType ?: "text/html"
        val enc = encoding ?: "utf-8"
        val bytes = data.toByteArray(runCatching { charset(enc) }.getOrDefault(Charsets.UTF_8))
        val base = baseUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (base != null) {
            pageUrl = base
            if (firstUrl == null) firstUrl = base
        }
        enqueue {
            val s = tab()
            if (base != null) {
                // The browser "loads" baseUrl, and we answer that request with the given HTML
                documentOverride = DocumentOverride(base, bytes, mime, enc)
                CookieBridge.toBrowser(s, base)
                navigate(s, base)
            } else {
                navigate(s, "data:$mime;charset=$enc;base64," + Base64.getEncoder().encodeToString(bytes))
            }
        }
    }

    private suspend fun navigate(s: CdpSession, url: String) {
        requestedUrl = url
        loadProgress = 10
        val result = s.send("Page.navigate", buildJsonObject { put("url", url) })
        result.string("errorText")?.let { error ->
            val request = DesktopResourceRequest(Uri.parse(url), "GET", emptyMap(), mainFrame = true, redirect = false)
            onMain { viewClient.onReceivedError(this@WebView, request, DesktopResourceError(errorCode(error), error)) }
        }
    }

    open fun reload() {
        enqueue { session?.send("Page.reload") }
    }

    open fun stopLoading() {
        enqueue { session?.send("Page.stopLoading") }
    }

    // ----- JavaScript -----

    open fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        enqueue {
            val s = tab()
            val result = runCatching {
                s.send(
                    "Runtime.evaluate",
                    buildJsonObject {
                        put("expression", script)
                        put("returnByValue", true)
                        put("awaitPromise", false)
                        put("userGesture", true)
                    },
                )
            }.onFailure { Log.w(TAG, "evaluateJavascript failed", it) }.getOrNull()
            val value = result?.obj("result")
            val json = when {
                result == null || result["exceptionDetails"] != null -> "null"
                value?.string("unserializableValue") != null -> value!!.string("unserializableValue")!!
                value?.get("value") != null -> value!!["value"].toString()
                else -> "null"
            }
            CookieBridge.fromBrowser(s, pageUrl)
            resultCallback?.let { cb -> onMain { cb.onReceiveValue(json) } }
        }
    }

    open fun addJavascriptInterface(obj: Any, name: String) {
        jsInterfaces[name] = obj
        enqueue { session?.let { registerInterface(it, name, obj) } }
    }

    open fun removeJavascriptInterface(name: String) {
        jsInterfaces.remove(name)
        enqueue {
            val s = session ?: return@enqueue
            interfaceScripts.remove(name)?.let { id ->
                runCatching {
                    s.send("Page.removeScriptToEvaluateOnNewDocument", buildJsonObject { put("identifier", id) })
                }
            }
            runCatching { s.evaluate("delete window[${q(name)}]") }
        }
    }

    /** window[name].method(...) in the page calls obj.method(...) here (return values aren't supported). */
    private suspend fun registerInterface(s: CdpSession, name: String, obj: Any) {
        val binding = BINDING_PREFIX + name
        s.send("Runtime.addBinding", buildJsonObject { put("name", binding) })
        val methods = obj.javaClass.methods
            .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            .map { it.name }
            .distinct()
        val script = buildString {
            append("(function(){var n=").append(q(name)).append(",b=").append(q(binding)).append(";")
            append("if(window[n]&&window[n].__j2k)return;var o={__j2k:true};")
            methods.forEach { m ->
                append("o[").append(q(m)).append("]=function(){window[b](JSON.stringify({m:").append(q(m))
                append(",a:Array.prototype.slice.call(arguments)}));};")
            }
            append("window[n]=o;})();")
        }
        s.send("Page.addScriptToEvaluateOnNewDocument", buildJsonObject { put("source", script) })
            .string("identifier")?.let { interfaceScripts[name] = it }
        runCatching { s.evaluate(script) }
    }

    private fun handleBinding(params: JsonObject) {
        val binding = params.string("name") ?: return
        if (!binding.startsWith(BINDING_PREFIX)) return
        val obj = jsInterfaces[binding.removePrefix(BINDING_PREFIX)] ?: return
        val payload = runCatching {
            CdpJson.parseToJsonElement(params.string("payload").orEmpty()).jsonObject
        }.getOrNull() ?: return
        val methodName = payload.string("m") ?: return
        val args = payload["a"] as? JsonArray ?: JsonArray(emptyList())
        val method = obj.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == args.size && it.isAnnotationPresent(JavascriptInterface::class.java)
        } ?: return
        val converted = method.parameterTypes.mapIndexed { i, type -> convertArg(args[i], type) }.toTypedArray()
        runCatching { method.invoke(obj, *converted) }.onFailure { Log.e(TAG, "JavaScript interface $methodName failed", it) }
    }

    private fun convertArg(el: JsonElement, type: Class<*>): Any? {
        val prim = el as? JsonPrimitive
        val text = if (el is JsonNull) null else prim?.contentOrNull ?: el.toString()
        val number = prim?.doubleOrNull ?: text?.toDoubleOrNull() ?: 0.0
        return when (type) {
            String::class.java, CharSequence::class.java, Any::class.java -> text
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> number.toInt()
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> number.toLong()
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> number
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> number.toFloat()
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> prim?.booleanOrNull ?: (text == "true")
            else -> null
        }
    }

    // ----- Browser events -----

    private fun onEvent(s: CdpSession, e: CdpEvent) {
        lastActivity = System.currentTimeMillis()
        val p = e.params
        when (e.method) {
            "Fetch.requestPaused" -> scope.launch { handlePaused(s, p) }

            "Page.frameNavigated" -> {
                val frame = p.obj("frame") ?: return
                if (frame.string("parentId") != null) return
                mainFrameId = frame.string("id")
                val url = frame.string("url").orEmpty() + frame.string("urlFragment").orEmpty()
                if (url.startsWith("data:") && pageUrl != null && documentOverride == null) return
                pageUrl = url
                loadProgress = 30
                onMain {
                    viewClient.onPageStarted(this@WebView, url, null)
                    chromeClient?.onProgressChanged(this@WebView, 30)
                }
            }

            "Page.loadEventFired" -> scope.launch {
                val url = pageUrl
                pageTitle = runCatching { (s.evaluate("document.title") as? JsonPrimitive)?.contentOrNull }.getOrNull()
                CookieBridge.fromBrowser(s, url)
                loadProgress = 100
                onMain {
                    chromeClient?.onReceivedTitle(this@WebView, pageTitle)
                    chromeClient?.onProgressChanged(this@WebView, 100)
                    viewClient.onPageFinished(this@WebView, url)
                }
            }

            "Runtime.bindingCalled" -> scope.launch { handleBinding(p) }

            "Runtime.consoleAPICalled" -> {
                val client = chromeClient ?: return
                val text = (p["args"] as? JsonArray).orEmpty().joinToString(" ") { arg ->
                    val o = arg as? JsonObject
                    o?.get("value")?.let { v -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }
                        ?: o?.string("description") ?: ""
                }
                val frame = (p.obj("stackTrace")?.get("callFrames") as? JsonArray)?.firstOrNull() as? JsonObject
                val level = when (p.string("type")) {
                    "error", "assert" -> ConsoleMessage.MessageLevel.ERROR
                    "warning" -> ConsoleMessage.MessageLevel.WARNING
                    "debug" -> ConsoleMessage.MessageLevel.DEBUG
                    else -> ConsoleMessage.MessageLevel.LOG
                }
                val message = ConsoleMessage(text, frame?.string("url"), (frame?.int("lineNumber") ?: 0) + 1, level)
                onMain { client.onConsoleMessage(message) }
            }

            "Runtime.exceptionThrown" -> {
                val client = chromeClient ?: return
                val details = p.obj("exceptionDetails") ?: return
                val text = details.obj("exception")?.string("description") ?: details.string("text").orEmpty()
                val message = ConsoleMessage(text, details.string("url"), (details.int("lineNumber") ?: 0) + 1, ConsoleMessage.MessageLevel.ERROR)
                onMain { client.onConsoleMessage(message) }
            }

            "Network.requestWillBeSent" -> if (p.string("type") == "Document") {
                val id = p.string("requestId") ?: return
                documentRequests[id] = p.obj("request")?.string("url").orEmpty()
            }

            "Network.responseReceived" -> if (p.string("type") == "Document") {
                val response = p.obj("response") ?: return
                val status = response.int("status") ?: return
                if (status >= 400) {
                    val url = response.string("url").orEmpty()
                    val headers = (response.obj("headers") ?: JsonObject(emptyMap()))
                        .mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }
                    val request = DesktopResourceRequest(Uri.parse(url), "GET", emptyMap(), isMainFrame(p.string("frameId")), false)
                    val errorResponse = WebResourceResponse(
                        response.string("mimeType"), null, status,
                        response.string("statusText").orEmpty().ifEmpty { "Error" }, headers, null,
                    )
                    onMain { viewClient.onReceivedHttpError(this@WebView, request, errorResponse) }
                }
            }

            "Network.loadingFinished" -> p.string("requestId")?.let { documentRequests.remove(it) }

            "Network.loadingFailed" -> {
                val url = p.string("requestId")?.let { documentRequests.remove(it) } ?: return
                if (p["canceled"]?.jsonPrimitive?.booleanOrNull == true) return
                val errorText = p.string("errorText").orEmpty()
                if (errorText.contains("BLOCKED_BY_CLIENT") || errorText.contains("ERR_ABORTED")) return
                val request = DesktopResourceRequest(Uri.parse(url), "GET", emptyMap(), mainFrame = true, redirect = false)
                onMain { viewClient.onReceivedError(this@WebView, request, DesktopResourceError(errorCode(errorText), errorText)) }
            }

            "Inspector.targetCrashed" -> onMain { viewClient.onRenderProcessGone(this@WebView, DesktopRenderProcessGone()) }
        }
    }

    private fun isMainFrame(frameId: String?) = frameId == null || frameId == mainFrameId

    private suspend fun handlePaused(s: CdpSession, p: JsonObject) {
        val requestId = p.string("requestId") ?: return
        try {
            val req = p.obj("request") ?: return continueRequest(s, requestId)
            val url = req.string("url").orEmpty()
            val type = p.string("resourceType")
            val isMain = type == "Document" && isMainFrame(p.string("frameId"))

            documentOverride?.let { doc ->
                if (isMain && sameUrl(url, doc.url)) {
                    documentOverride = null
                    return fulfill(s, requestId, 200, "OK", mapOf("Content-Type" to "${doc.mimeType}; charset=${doc.encoding}"), doc.bytes)
                }
            }
            if (webSettings.blockNetworkLoads || (type == "Image" && (webSettings.blockNetworkImage || !webSettings.loadsImagesAutomatically))) {
                s.send("Fetch.failRequest", buildJsonObject {
                    put("requestId", requestId)
                    put("errorReason", "BlockedByClient")
                })
                return
            }

            val headers = (req.obj("headers") ?: JsonObject(emptyMap()))
                .mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }
            val redirect = p["redirectedRequestId"] != null
            val request = DesktopResourceRequest(Uri.parse(url), req.string("method") ?: "GET", headers, isMain, redirect)

            // Links/redirects the page itself starts (not our loadUrl) can be cancelled by the client
            if (isMain && requestedUrl != url && url.startsWith("http")) {
                val cancel = runCatching { viewClient.shouldOverrideUrlLoading(this@WebView, request) }.getOrDefault(false)
                if (cancel) {
                    s.send("Fetch.failRequest", buildJsonObject {
                        put("requestId", requestId)
                        put("errorReason", "Aborted")
                    })
                    return
                }
            }
            if (isMain) requestedUrl = null

            val response = runCatching { viewClient.shouldInterceptRequest(this@WebView, request) }
                .onFailure { Log.e(TAG, "shouldInterceptRequest failed", it) }
                .getOrNull()
            val data = response?.getData()
            if (response != null && data != null) {
                val bytes = data.use { it.readBytes() }
                val responseHeaders = LinkedHashMap(response.getResponseHeaders().orEmpty())
                if (responseHeaders.keys.none { it.equals("Content-Type", ignoreCase = true) } && response.getMimeType() != null) {
                    responseHeaders["Content-Type"] = response.getMimeType() + (response.getEncoding()?.let { "; charset=$it" } ?: "")
                }
                fulfill(s, requestId, response.getStatusCode(), response.getReasonPhrase(), responseHeaders, bytes)
            } else {
                continueRequest(s, requestId)
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Request handling failed", e)
            runCatching { continueRequest(s, requestId) }
        }
    }

    private suspend fun continueRequest(s: CdpSession, requestId: String) {
        s.send("Fetch.continueRequest", buildJsonObject { put("requestId", requestId) })
    }

    private suspend fun fulfill(
        s: CdpSession,
        requestId: String,
        status: Int,
        reason: String,
        headers: Map<String, String>,
        body: ByteArray,
    ) {
        s.send(
            "Fetch.fulfillRequest",
            buildJsonObject {
                put("requestId", requestId)
                put("responseCode", status)
                if (reason.isNotBlank()) put("responsePhrase", reason)
                putJsonArray("responseHeaders") {
                    headers.forEach { (k, v) ->
                        addJsonObject {
                            put("name", k)
                            put("value", v)
                        }
                    }
                }
                put("body", Base64.getEncoder().encodeToString(body))
            },
        )
    }

    private fun sameUrl(a: String, b: String): Boolean {
        fun norm(u: String) = u.substringBefore('#').trimEnd('/')
        return norm(a) == norm(b)
    }

    private fun errorCode(error: String): Int = when {
        "NAME_NOT_RESOLVED" in error -> WebViewClient.ERROR_HOST_LOOKUP
        "TIMED_OUT" in error -> WebViewClient.ERROR_TIMEOUT
        "CONNECTION" in error -> WebViewClient.ERROR_CONNECT
        "SSL" in error || "CERT" in error -> WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
        else -> WebViewClient.ERROR_UNKNOWN
    }

    // ----- Plain accessors -----

    open fun getSettings(): WebSettings = webSettings

    open fun setWebViewClient(client: WebViewClient?) {
        viewClient = client ?: WebViewClient()
    }

    open fun getWebViewClient(): WebViewClient = viewClient

    open fun setWebChromeClient(client: WebChromeClient?) {
        chromeClient = client
    }

    open fun getWebChromeClient(): WebChromeClient? = chromeClient

    open fun getUrl(): String? = pageUrl

    open fun getOriginalUrl(): String? = firstUrl

    open fun getTitle(): String? = pageTitle

    open fun getProgress(): Int = loadProgress

    open fun getContentHeight(): Int = 0

    open fun destroy() {
        if (destroyed) return
        destroyed = true
        ops.trySend {
            closeTab()
            scope.cancel()
        }
        ops.close()
    }

    open fun onResume() {}
    open fun onPause() {}
    open fun resumeTimers() {}
    open fun pauseTimers() {}
    open fun clearCache(includeDiskFiles: Boolean) {}
    open fun clearHistory() {}
    open fun clearFormData() {}
    open fun clearMatches() {}
    open fun clearSslPreferences() {}
    open fun canGoBack(): Boolean = false
    open fun goBack() {}
    open fun canGoForward(): Boolean = false
    open fun goForward() {}
    open fun setBackgroundColor(color: Int) {}
    open fun setInitialScale(scaleInPercent: Int) {}
    open fun setNetworkAvailable(networkUp: Boolean) {}
    open fun setVerticalScrollBarEnabled(enabled: Boolean) {}
    open fun setHorizontalScrollBarEnabled(enabled: Boolean) {}
    open fun setDownloadListener(listener: Any?) {}
    open fun setFindListener(listener: Any?) {}
    open fun freeMemory() {}

    companion object {
        private const val TAG = "WebView"
        private const val BINDING_PREFIX = "__j2k_js_"
        private val CdpJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        private fun q(value: String): String = JsonPrimitive(value).toString()

        @JvmStatic
        fun getWebViewClassLoader(): ClassLoader = WebView::class.java.classLoader

        @JvmStatic
        fun setWebContentsDebuggingEnabled(enabled: Boolean) {}

        @JvmStatic
        fun getCurrentWebViewPackage(): Any? = null

        @JvmStatic
        fun setDataDirectorySuffix(suffix: String) {}

        @JvmStatic
        fun enableSlowWholeDocumentDraw() {}
    }
}
