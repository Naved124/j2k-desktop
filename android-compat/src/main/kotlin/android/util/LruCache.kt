package android.util

open class LruCache<K, V>(private var maxSize: Int) {
    private val map = LinkedHashMap<K, V>(0, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = map[key] ?: create(key)?.also { put(key, it) }

    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = map.put(key, value)
        trimToSize(maxSize)
        return previous
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun evictAll() = map.clear()

    @Synchronized
    fun size(): Int = map.values.sumOf { sizeOf(it) }

    fun maxSize(): Int = maxSize

    protected open fun create(key: K): V? = null

    protected open fun sizeOf(value: V): Int = 1

    @Synchronized
    fun trimToSize(maxSize: Int) {
        while (map.isNotEmpty() && size() > maxSize) {
            val eldest = map.keys.first()
            map.remove(eldest)
        }
    }
}
