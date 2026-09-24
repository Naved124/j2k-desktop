package dev.naved.j2kdesktop.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CdpException(message: String) : IOException(message)

/** An event from the browser: method name, parameters and (for tab events) the session it belongs to. */
class CdpEvent(val method: String, val params: JsonObject, val sessionId: String?)

/**
 * A Chrome DevTools Protocol connection over a WebSocket.
 * Commands are suspend calls; events go to listeners, which must return quickly (launch work elsewhere).
 */
class CdpConnection private constructor() : WebSocketListener() {
    private lateinit var socket: WebSocket
    private val nextId = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val listeners = CopyOnWriteArrayList<(CdpEvent) -> Unit>()
    private val opened = CompletableDeferred<Unit>()
    val closed = CompletableDeferred<Unit>()

    val isOpen: Boolean get() = opened.isCompleted && !closed.isCompleted

    suspend fun send(
        method: String,
        params: JsonObject = EMPTY,
        sessionId: String? = null,
        timeoutMs: Long = 30_000,
    ): JsonObject {
        if (closed.isCompleted) throw CdpException("The browser connection is closed")
        val id = nextId.incrementAndGet()
        val result = CompletableDeferred<JsonObject>()
        pending[id] = result
        val message = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
            if (sessionId != null) put("sessionId", sessionId)
        }
        if (!socket.send(message.toString())) {
            pending.remove(id)
            throw CdpException("Couldn't send $method to the browser")
        }
        return try {
            withTimeout(timeoutMs) { result.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** Adds an event listener; returns a function that removes it. */
    fun on(listener: (CdpEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    fun close() {
        runCatching { socket.close(1000, null) }
        failAll("The browser connection was closed")
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        opened.complete(Unit)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val id = msg["id"]?.jsonPrimitive?.intOrNull
        if (id != null) {
            val waiter = pending.remove(id) ?: return
            val error = msg["error"] as? JsonObject
            if (error != null) {
                val text2 = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
                waiter.completeExceptionally(CdpException(text2))
            } else {
                waiter.complete(msg["result"] as? JsonObject ?: EMPTY)
            }
            return
        }
        val method = msg["method"]?.jsonPrimitive?.contentOrNull ?: return
        val event = CdpEvent(method, msg["params"] as? JsonObject ?: EMPTY, msg["sessionId"]?.jsonPrimitive?.contentOrNull)
        for (listener in listeners) {
            runCatching { listener(event) }.onFailure { it.printStackTrace() }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        failAll("The browser closed the connection")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        failAll("The browser closed the connection")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        opened.completeExceptionally(t)
        failAll("Lost the connection to the browser: ${t.message}")
    }

    private fun failAll(reason: String) {
        closed.complete(Unit)
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it.completeExceptionally(CdpException(reason)) }
    }

    companion object {
        val EMPTY = JsonObject(emptyMap())
        val json = Json { ignoreUnknownKeys = true }

        private val wsClient by lazy {
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }

        suspend fun connect(url: String): CdpConnection {
            val conn = CdpConnection()
            conn.socket = wsClient.newWebSocket(Request.Builder().url(url).build(), conn)
            withTimeout(15_000) { conn.opened.await() }
            return conn
        }
    }
}

/** One browser tab (a flattened target session). */
class CdpSession(val connection: CdpConnection, val sessionId: String, val targetId: String) {
    @Volatile
    var isClosed = false
        private set

    private val removers = CopyOnWriteArrayList<() -> Unit>()

    suspend fun send(method: String, params: JsonObject = CdpConnection.EMPTY, timeoutMs: Long = 30_000): JsonObject =
        connection.send(method, params, sessionId, timeoutMs)

    /** Events for this tab only. */
    fun on(listener: (CdpEvent) -> Unit) {
        removers += connection.on { if (it.sessionId == sessionId) listener(it) }
    }

    /** Runs JavaScript and returns the value (JSON), or throws with the page's error. */
    suspend fun evaluate(expression: String, awaitPromise: Boolean = false, timeoutMs: Long = 30_000): JsonElement? {
        val result = send(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", expression)
                put("returnByValue", true)
                put("awaitPromise", awaitPromise)
                put("userGesture", true)
            },
            timeoutMs,
        )
        (result["exceptionDetails"] as? JsonObject)?.let { details ->
            val text = (details["exception"] as? JsonObject)?.get("description")?.jsonPrimitive?.contentOrNull
                ?: details["text"]?.jsonPrimitive?.contentOrNull
            throw CdpException("JavaScript error: $text")
        }
        return (result["result"] as? JsonObject)?.get("value")
    }

    suspend fun close() {
        if (isClosed) return
        isClosed = true
        removers.forEach { it() }
        removers.clear()
        runCatching {
            connection.send("Target.closeTarget", buildJsonObject { put("targetId", targetId) }, timeoutMs = 5_000)
        }
        Browser.tabClosed(connection)
    }
}

internal fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
