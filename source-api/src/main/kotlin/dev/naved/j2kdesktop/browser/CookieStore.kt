package dev.naved.j2kdesktop.browser

import dev.naved.j2kdesktop.compat.AppDirs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * All cookies, shared by OkHttp, android.webkit.CookieManager and the browser (like Android, where
 * the app's cookie jar is the WebView's CookieManager). Persistent cookies are saved to disk, so a
 * solved Cloudflare check (cf_clearance) survives a restart.
 */
object CookieStore {
    private val file get() = File(AppDirs.data, "cookies.json")
    private val cookies = ArrayList<Cookie>()
    private val timer = Timer("cookie-save", true)
    private var saveScheduled = false

    init {
        load()
    }

    @Synchronized
    fun get(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun all(): List<Cookie> = cookies.toList()

    @Synchronized
    fun add(new: List<Cookie>) {
        if (new.isEmpty()) return
        val now = System.currentTimeMillis()
        for (cookie in new) {
            cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt >= now) cookies += cookie
        }
        scheduleSave()
    }

    @Synchronized
    fun removeAll(predicate: (Cookie) -> Boolean) {
        if (cookies.removeAll(predicate)) scheduleSave()
    }

    /** "a=1; b=2" for a URL, like CookieManager.getCookie. */
    fun header(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        return get(parsed).joinToString("; ") { "${it.name}=${it.value}" }.ifEmpty { null }
    }

    private fun scheduleSave() {
        if (saveScheduled) return
        saveScheduled = true
        timer.schedule(1_000) { save() }
    }

    private fun save() {
        val snapshot = synchronized(this) {
            saveScheduled = false
            cookies.filter { it.persistent }
        }
        val json = buildJsonArray {
            snapshot.forEach { c ->
                addJsonObject {
                    put("name", c.name)
                    put("value", c.value)
                    put("domain", c.domain)
                    put("path", c.path)
                    put("expiresAt", c.expiresAt)
                    put("secure", c.secure)
                    put("httpOnly", c.httpOnly)
                    put("hostOnly", c.hostOnly)
                }
            }
        }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "cookies.json.tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }.onFailure { it.printStackTrace() }
    }

    private fun load() {
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        runCatching {
            CdpConnection.json.parseToJsonElement(text).jsonArray.forEach { el ->
                val o = el.jsonObject
                val expires = o["expiresAt"]!!.jsonPrimitive.long
                if (expires < now) return@forEach
                val b = Cookie.Builder()
                    .name(o.string("name")!!)
                    .value(o.string("value")!!)
                    .path(o.string("path") ?: "/")
                    .expiresAt(expires)
                val domain = o.string("domain")!!
                if (o["hostOnly"]?.jsonPrimitive?.booleanOrNull == true) b.hostOnlyDomain(domain) else b.domain(domain)
                if (o["secure"]?.jsonPrimitive?.booleanOrNull == true) b.secure()
                if (o["httpOnly"]?.jsonPrimitive?.booleanOrNull == true) b.httpOnly()
                cookies += b.build()
            }
        }.onFailure { it.printStackTrace() }
    }
}

class PersistentCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = CookieStore.add(cookies)

    override fun loadForRequest(url: HttpUrl): List<Cookie> = CookieStore.get(url)
}

/** Copies cookies between [CookieStore] and the browser. */
object CookieBridge {
    /** Store -> browser, before a tab loads [url]. */
    suspend fun toBrowser(session: CdpSession, url: String) {
        val params = cookieParams(url) ?: return
        runCatching { session.send("Network.setCookies", params) }.onFailure { it.printStackTrace() }
    }

    /** Store -> browser without touching any tab (browser-wide Storage domain). */
    suspend fun toBrowser(connection: CdpConnection, url: String) {
        val params = cookieParams(url) ?: return
        runCatching { connection.send("Storage.setCookies", params) }.onFailure { it.printStackTrace() }
    }

    /** Browser -> store, for the cookies the browser would send to [url]. */
    suspend fun fromBrowser(session: CdpSession, url: String?) {
        val parsed = url?.toHttpUrlOrNull() ?: return
        val result = runCatching {
            session.send("Network.getCookies", buildJsonObject { putJsonArray("urls") { add(parsed.toString()) } })
        }.getOrNull() ?: return
        CookieStore.add(parse(result["cookies"] as? JsonArray))
    }

    /** Browser -> store, every cookie for [url]'s site, without touching any tab. */
    suspend fun fromBrowser(connection: CdpConnection, url: String?) {
        val parsed = url?.toHttpUrlOrNull() ?: return
        CookieStore.add(browserCookies(connection).filter { domainMatches(parsed.host, it.domain) })
    }

    /** All cookies the browser has (browser-wide). */
    suspend fun browserCookies(connection: CdpConnection): List<Cookie> {
        val result = runCatching { connection.send("Storage.getCookies") }.getOrNull() ?: return emptyList()
        return parse(result["cookies"] as? JsonArray)
    }

    fun domainMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain") || domain.endsWith(".$host")

    private fun cookieParams(url: String): JsonObject? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val list = CookieStore.get(parsed)
        if (list.isEmpty()) return null
        return buildJsonObject {
            putJsonArray("cookies") {
                list.forEach { c ->
                    addJsonObject {
                        put("name", c.name)
                        put("value", c.value)
                        if (c.hostOnly) {
                            put("url", "${if (c.secure) "https" else parsed.scheme}://${c.domain}${c.path}")
                        } else {
                            put("domain", "." + c.domain)
                        }
                        put("path", c.path)
                        put("secure", c.secure)
                        put("httpOnly", c.httpOnly)
                        if (c.persistent) put("expires", c.expiresAt / 1000.0)
                    }
                }
            }
        }
    }

    private fun parse(array: JsonArray?): List<Cookie> = array.orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        runCatching {
            val domain = o.string("domain")!!
            val b = Cookie.Builder()
                .name(o.string("name")!!)
                .value(o.string("value")!!)
                .path(o.string("path") ?: "/")
            if (domain.startsWith(".")) b.domain(domain.removePrefix(".")) else b.hostOnlyDomain(domain)
            val expires = o["expires"]?.jsonPrimitive?.doubleOrNull ?: -1.0
            val isSession = o["session"]?.jsonPrimitive?.booleanOrNull ?: (expires <= 0)
            if (!isSession && expires > 0) b.expiresAt((expires * 1000).toLong())
            if (o["secure"]?.jsonPrimitive?.booleanOrNull == true) b.secure()
            if (o["httpOnly"]?.jsonPrimitive?.booleanOrNull == true) b.httpOnly()
            b.build()
        }.getOrNull()
    }
}
