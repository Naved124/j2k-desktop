package android.net

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Enough of android.net.Uri for extensions (parse, query params, path segments, Builder). */
class Uri private constructor(private val raw: String) {
    private val parsed: URI? =
        runCatching { URI(raw) }.getOrNull()
            ?: runCatching { URI(raw.replace(" ", "%20")) }.getOrNull()

    fun getScheme(): String? = parsed?.scheme
    fun getAuthority(): String? = parsed?.authority
    fun getEncodedAuthority(): String? = parsed?.rawAuthority
    fun getHost(): String? = parsed?.host
    fun getPort(): Int = parsed?.port ?: -1
    fun getPath(): String? = parsed?.path
    fun getEncodedPath(): String? = parsed?.rawPath
    fun getQuery(): String? = parsed?.query
    fun getEncodedQuery(): String? = parsed?.rawQuery
    fun getFragment(): String? = parsed?.fragment
    fun getEncodedFragment(): String? = parsed?.rawFragment
    fun getPathSegments(): List<String> = getPath().orEmpty().split('/').filter { it.isNotEmpty() }
    fun getLastPathSegment(): String? = getPathSegments().lastOrNull()
    fun isAbsolute(): Boolean = getScheme() != null
    fun isRelative(): Boolean = !isAbsolute()

    fun getQueryParameterNames(): Set<String> = queryPairs().mapTo(LinkedHashSet()) { it.first }
    fun getQueryParameters(key: String): List<String> = queryPairs().filter { it.first == key }.map { it.second }
    fun getQueryParameter(key: String): String? = queryPairs().firstOrNull { it.first == key }?.second
    fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val v = getQueryParameter(key) ?: return defaultValue
        return v.isNotEmpty() && v != "false" && v != "0"
    }

    fun buildUpon(): Builder = Builder()
        .scheme(getScheme())
        .encodedAuthority(getEncodedAuthority())
        .encodedPath(getEncodedPath())
        .encodedQuery(getEncodedQuery())
        .encodedFragment(getEncodedFragment())

    private fun queryPairs(): List<Pair<String, String>> =
        getEncodedQuery().orEmpty().split('&').filter { it.isNotEmpty() }.map { part ->
            val i = part.indexOf('=')
            if (i < 0) {
                decodeOrSame(part) to ""
            } else {
                decodeOrSame(part.substring(0, i)) to decodeOrSame(part.substring(i + 1))
            }
        }

    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    class Builder {
        private var scheme: String? = null
        private var authority: String? = null
        private val path = StringBuilder()
        private val query = StringBuilder()
        private var fragment: String? = null

        fun scheme(scheme: String?): Builder = apply { this.scheme = scheme }
        fun authority(authority: String?): Builder = apply { this.authority = authority?.let { encodeOrSame(it, "@:[]") } }
        fun encodedAuthority(authority: String?): Builder = apply { this.authority = authority }

        fun path(path: String?): Builder = apply {
            this.path.setLength(0)
            if (path != null) this.path.append(encodeOrSame(path, "/"))
        }

        fun encodedPath(path: String?): Builder = apply {
            this.path.setLength(0)
            if (path != null) this.path.append(path)
        }

        fun appendPath(segment: String?): Builder = apply {
            if (segment != null) appendEncodedPath(encodeOrSame(segment, null))
        }

        fun appendEncodedPath(segment: String?): Builder = apply {
            if (segment == null) return@apply
            if (path.isEmpty() || path[path.length - 1] != '/') path.append('/')
            path.append(segment.removePrefix("/"))
        }

        fun query(query: String?): Builder = apply {
            this.query.setLength(0)
            if (query != null) this.query.append(encodeOrSame(query, "=&"))
        }

        fun encodedQuery(query: String?): Builder = apply {
            this.query.setLength(0)
            if (query != null) this.query.append(query)
        }

        fun appendQueryParameter(key: String, value: String?): Builder = apply {
            if (query.isNotEmpty()) query.append('&')
            query.append(encodeOrSame(key, null)).append('=').append(encodeOrSame(value.orEmpty(), null))
        }

        fun clearQuery(): Builder = apply { query.setLength(0) }
        fun fragment(fragment: String?): Builder = apply { this.fragment = fragment?.let { encodeOrSame(it, null) } }
        fun encodedFragment(fragment: String?): Builder = apply { this.fragment = fragment }

        fun build(): Uri {
            val sb = StringBuilder()
            scheme?.let { sb.append(it).append(':') }
            authority?.let { sb.append("//").append(it) }
            if (path.isNotEmpty()) {
                if (authority != null && path[0] != '/') sb.append('/')
                sb.append(path)
            }
            if (query.isNotEmpty()) sb.append('?').append(query)
            fragment?.let { sb.append('#').append(it) }
            return Uri(sb.toString())
        }

        override fun toString(): String = build().toString()
    }

    companion object {
        @JvmField
        val EMPTY: Uri = Uri("")

        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())

        @JvmStatic
        fun withAppendedPath(baseUri: Uri, pathSegment: String): Uri =
            baseUri.buildUpon().appendEncodedPath(pathSegment).build()

        @JvmStatic
        fun encode(s: String?): String? = encode(s, null)

        @JvmStatic
        fun encode(s: String?, allow: String?): String? = s?.let { encodeOrSame(it, allow) }

        @JvmStatic
        fun decode(s: String?): String? = s?.let { decodeOrSame(it) }

        internal fun encodeOrSame(s: String, allow: String?): String {
            val sb = StringBuilder()
            for (b in s.toByteArray(StandardCharsets.UTF_8)) {
                val c = b.toInt() and 0xff
                val ch = c.toChar()
                val unreserved = c < 0x80 && (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "_-!.~'()*")
                if (unreserved || (c < 0x80 && allow != null && ch in allow)) {
                    sb.append(ch)
                } else {
                    sb.append('%').append(String.format("%02X", c))
                }
            }
            return sb.toString()
        }

        internal fun decodeOrSame(s: String): String =
            runCatching { URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8) }.getOrDefault(s)
    }
}
