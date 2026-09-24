package dev.naved.j2kdesktop.browser

import dev.naved.j2kdesktop.compat.AndroidCompat
import dev.naved.j2kdesktop.compat.AppDirs
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Gets past Cloudflare's "Just a moment..." check with a real browser:
 * 1. silently, in the hidden browser;
 * 2. if that doesn't pass (it wants a click), in a visible browser window the user completes.
 * The page is never instrumented (Cloudflare notices DevTools hooks and loops forever): we only open
 * it and watch its title and the browser's cookies from outside. The cookies it earns (cf_clearance)
 * go into [CookieStore], which OkHttp uses, together with the browser's own User-Agent.
 */
object CloudflareSolver {
    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val interactiveLock = Mutex()
    private val recentFailures = ConcurrentHashMap<String, Long>()

    private val challengeTitles = listOf(
        "just a moment", "attention required", "please wait", "checking your browser", "verifying",
        "un instant", "einen moment", "un momento", "um momento", "подождите", "chờ một chút",
    )

    fun isChallenge(response: Response): Boolean {
        if (response.code !in setOf(403, 429, 503)) return false
        if (!response.header("Server").orEmpty().startsWith("cloudflare", ignoreCase = true)) return false
        if (response.header("cf-mitigated") == "challenge") return true
        val body = runCatching { response.peekBody(256 * 1024).string() }.getOrDefault("")
        return "cf_chl_opt" in body || "cf-browser-verification" in body || "challenge-form" in body ||
            "<title>Just a moment" in body
    }

    fun clearance(url: HttpUrl): String? = CookieStore.get(url).firstOrNull { it.name == "cf_clearance" }?.value

    fun isChallengeTitle(title: String?): Boolean {
        val t = title.orEmpty().trim().lowercase()
        return challengeTitles.any { t.startsWith(it) }
    }

    suspend fun solve(url: HttpUrl, oldClearance: String?) {
        val lock = hostLocks.getOrPut(url.host) { Mutex() }
        lock.withLock {
            // Another request may have solved it while this one waited
            clearance(url)?.let { if (it != oldClearance) return }
            // Don't pop a new window for every request right after the user gave up on one
            recentFailures[url.host]?.let { at ->
                if (System.currentTimeMillis() - at < 60_000) {
                    throw IOException("The check for ${url.host} wasn't completed. Try again in a minute.")
                }
            }
            if (solveHeadless(url, oldClearance)) return
            try {
                solveInteractive(url, oldClearance)
                recentFailures.remove(url.host)
            } catch (e: Throwable) {
                recentFailures[url.host] = System.currentTimeMillis()
                throw e
            }
        }
    }

    private suspend fun solveHeadless(url: HttpUrl, oldClearance: String?): Boolean {
        val tab = BrowserFetcher.openOn(url)
        return try {
            waitUntilPassed(tab.connection, tab.targetId, url, oldClearance, 15_000)
        } finally {
            CookieBridge.fromBrowser(tab.connection, url.toString())
            tab.close()
        }
    }

    /** A plain visible window (its own profile) opened straight on the page, so the user can tick the box. */
    suspend fun solveInteractive(url: HttpUrl, oldClearance: String?) = interactiveLock.withLock {
        val exe = BrowserLocator.executable ?: throw IOException(BrowserLocator.NOT_FOUND)
        AndroidCompat.toastHandler("${url.host} wants a quick check: complete it in the browser window that just opened")
        val profile = File(AppDirs.data, "browser-visible").apply { mkdirs() }
        val (process, wsUrl) = BrowserProcess.start(
            exe,
            profile,
            listOf("--window-size=1000,780", "--new-window"),
            startUrl = url.toString(),
        )
        try {
            val conn = CdpConnection.connect(wsUrl)
            // The real User-Agent of a normal window: OkHttp must send exactly this with cf_clearance
            Browser.rememberUserAgent(conn.send("Browser.getVersion").string("userAgent"))
            val passed = waitUntilPassed(conn, null, url, oldClearance, 180_000)
            if (conn.isOpen) CookieBridge.fromBrowser(conn, url.toString())
            conn.close()
            if (!passed) throw IOException("The check for ${url.host} wasn't completed (window closed or timed out)")
            AndroidCompat.toastHandler("${url.host}: check passed")
        } finally {
            process.destroy()
        }
    }

    /**
     * Watches from the outside (tab title + browser cookies) until the page is past the check:
     * a new cf_clearance, or a normal page that stays up for a few seconds.
     * [targetId] null = any tab showing this site (the visible window).
     */
    suspend fun waitUntilPassed(
        conn: CdpConnection,
        targetId: String?,
        url: HttpUrl,
        oldClearance: String?,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var calmSince = 0L
        var sawPage = false
        while (System.currentTimeMillis() < deadline) {
            delay(800)
            if (!conn.isOpen) return false
            val pages = runCatching { conn.send("Target.getTargets")["targetInfos"] as? JsonArray }.getOrNull()
                .orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "page" }
            val page = if (targetId != null) {
                pages.firstOrNull { it.string("targetId") == targetId }
            } else {
                pages.firstOrNull { p -> p.string("url")?.toHttpUrlOrNull()?.host?.let { CookieBridge.domainMatches(it, url.host) } == true }
            }
            if (page == null) {
                // The user closed the window (or the tab is gone)
                if (targetId == null && sawPage && pages.isEmpty()) return false
                continue
            }
            sawPage = true
            val title = page.string("title").orEmpty()
            val clearance = CookieBridge.browserCookies(conn)
                .firstOrNull { it.name == "cf_clearance" && CookieBridge.domainMatches(url.host, it.domain) }?.value
            if (isChallengeTitle(title)) {
                calmSince = 0
                continue
            }
            if (clearance != null && clearance != oldClearance) return true
            // Not a challenge (anymore), no new clearance: maybe the browser was never challenged
            if (title.isNotBlank()) {
                if (calmSince == 0L) calmSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - calmSince > 4_000) return true
            }
        }
        return false
    }
}

/**
 * Layer 2: when a site still blocks OkHttp after the check was passed (it recognises the connection
 * itself, not just the cookies), requests to that host go through the browser: a tab sitting on the
 * site runs fetch() and hands back the bytes. Slower, but it's a real browser connection.
 */
object BrowserFetcher {
    private val routed = ConcurrentHashMap.newKeySet<String>()
    private val tabs = LinkedHashMap<String, CdpSession>()
    private val lock = Mutex()
    private const val MAX_TABS = 3

    fun isRouted(host: String): Boolean = host in routed

    fun route(host: String) {
        if (routed.add(host)) {
            AndroidCompat.toastHandler("$host blocks the app's own connection, so it now loads through the browser (slower)")
        }
    }

    private val forbiddenHeaders = setOf(
        "host", "cookie", "user-agent", "accept-encoding", "connection", "content-length", "origin", "referer",
        "keep-alive", "te", "trailer", "transfer-encoding", "upgrade", "via", "proxy-connection", "dnt",
    )

    suspend fun fetch(request: Request): Response {
        val url = request.url
        val origin = "${url.scheme}://${url.host}" + if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        val tab = tabFor(origin)

        val bodyBytes = request.body?.let { body -> Buffer().also { body.writeTo(it) }.readByteArray() }
        val headers = buildJsonObject {
            request.headers.forEach { (name, value) ->
                val lower = name.lowercase()
                if (lower !in forbiddenHeaders && !lower.startsWith("sec-") && !lower.startsWith("proxy-")) put(name, value)
            }
            request.body?.contentType()?.let { if (request.header("Content-Type") == null) put("Content-Type", it.toString()) }
        }
        val referer = request.header("Referer")
        val js = """(async () => {
            const b64 = ${q(bodyBytes?.let { Base64.getEncoder().encodeToString(it) })};
            const init = { method: ${q(request.method)}, headers: $headers, credentials: 'include', redirect: 'follow' };
            if (b64) init.body = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
            const ref = ${q(referer)};
            if (ref) init.referrer = ref;
            const r = await fetch(${q(url.toString())}, init);
            const buf = new Uint8Array(await r.arrayBuffer());
            let s = '';
            for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode.apply(null, buf.subarray(i, i + 0x8000));
            return { status: r.status, statusText: r.statusText, url: r.url, headers: [...r.headers], body: btoa(s) };
        })()"""
        val result = tab.evaluate(js, awaitPromise = true, timeoutMs = 120_000) as? JsonObject
            ?: throw IOException("The browser returned nothing for $url")
        CookieBridge.fromBrowser(tab, url.toString())

        val headerBuilder = Headers.Builder()
        (result["headers"] as? JsonArray).orEmpty().forEach { pair ->
            val kv = pair as? JsonArray ?: return@forEach
            val name = kv.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@forEach
            val value = kv.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@forEach
            // The browser already decompressed the body
            if (name.lowercase() !in setOf("content-encoding", "content-length", "transfer-encoding")) {
                runCatching { headerBuilder.addUnsafeNonAscii(name, value) }
            }
        }
        val responseHeaders = headerBuilder.build()
        val bytes = Base64.getDecoder().decode(result.string("body").orEmpty())
        val finalUrl = result.string("url")?.takeIf { it.isNotBlank() } ?: url.toString()
        val code = result.int("status") ?: 200
        return Response.Builder()
            .request(request.newBuilder().url(finalUrl.toHttpUrl()).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(result.string("statusText").orEmpty())
            .headers(responseHeaders)
            .body(bytes.toResponseBody(responseHeaders["Content-Type"]?.toMediaTypeOrNull()))
            .build()
    }

    /** A tab parked on [origin] (past any check), reused for that site's requests. Not instrumented. */
    private suspend fun tabFor(origin: String): CdpSession = lock.withLock {
        tabs[origin]?.let { if (!it.isClosed && it.connection.isOpen) return it else tabs.remove(origin) }
        val home = "$origin/".toHttpUrl()
        var tab = openOn(home)
        if (!CloudflareSolver.waitUntilPassed(tab.connection, tab.targetId, home, null, 20_000)) {
            tab.close()
            CloudflareSolver.solveInteractive(home, CloudflareSolver.clearance(home))
            tab = openOn(home)
            if (!CloudflareSolver.waitUntilPassed(tab.connection, tab.targetId, home, null, 20_000)) {
                tab.close()
                throw IOException("Couldn't get past the check on $origin")
            }
        }
        tabs[origin] = tab
        while (tabs.size > MAX_TABS) {
            val oldest = tabs.keys.first()
            tabs.remove(oldest)?.close()
        }
        tab
    }

    /**
     * A tab on [url] that looks like a normal window: the browser's UA and client hints are set before
     * it loads, and no page/runtime hooks are installed.
     */
    internal suspend fun openOn(url: HttpUrl): CdpSession {
        val tab = Browser.newTab(instrument = false)
        try {
            Browser.setUserAgent(tab, Browser.userAgent)
            CookieBridge.toBrowser(tab.connection, url.toString())
            tab.send("Page.navigate", buildJsonObject { put("url", url.toString()) })
        } catch (e: Throwable) {
            tab.close()
            throw e
        }
        return tab
    }

    /** A JavaScript string literal (or null). */
    private fun q(value: String?): String = if (value == null) "null" else JsonPrimitive(value).toString()
}
