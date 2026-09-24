package dev.naved.j2kdesktop.compat

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/** SharedPreferences stored as a small JSON file: {"key": {"t": "s", "v": "value"}, ...} */
class FileSharedPreferences(private val file: File) : SharedPreferences {
    private val lock = Any()
    private val values: MutableMap<String, Any> = load()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = synchronized(lock) { HashMap(values) }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(lock) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        synchronized(lock) { (values[key] as? Set<String>)?.toSet() ?: defValues }

    override fun getInt(key: String, defValue: Int): Int =
        synchronized(lock) { (values[key] as? Number)?.toInt() ?: defValue }

    override fun getLong(key: String, defValue: Long): Long =
        synchronized(lock) { (values[key] as? Number)?.toLong() ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float =
        synchronized(lock) { (values[key] as? Number)?.toFloat() ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(lock) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.addIfAbsent(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>() // null = remove
        private var clearFirst = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clearFirst = true }

        override fun commit(): Boolean {
            val changedKeys = synchronized(lock) {
                val keys = LinkedHashSet<String>()
                if (clearFirst) {
                    keys += values.keys
                    values.clear()
                }
                for ((k, v) in changes) {
                    if (v == null) values.remove(k) else values[k] = v
                    keys += k
                }
                save()
                keys
            }
            for (k in changedKeys) {
                listeners.forEach { runCatching { it.onSharedPreferenceChanged(this@FileSharedPreferences, k) } }
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }

    private fun load(): MutableMap<String, Any> {
        val map = LinkedHashMap<String, Any>()
        if (!file.exists()) return map
        runCatching {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            for ((key, element) in root) {
                val obj = element.jsonObject
                val v = obj["v"] ?: continue
                val value: Any? = when (obj["t"]?.jsonPrimitive?.content) {
                    "s" -> v.jsonPrimitive.content
                    "i" -> v.jsonPrimitive.int
                    "l" -> v.jsonPrimitive.long
                    "f" -> v.jsonPrimitive.float
                    "b" -> v.jsonPrimitive.boolean
                    "set" -> v.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    else -> null
                }
                if (value != null) map[key] = value
            }
        }.onFailure { System.err.println("Couldn't read prefs ${file.name}: $it") }
        return map
    }

    private fun save() {
        val root = buildJsonObject {
            for ((key, value) in values) {
                put(
                    key,
                    buildJsonObject {
                        when (value) {
                            is String -> { put("t", "s"); put("v", value) }
                            is Int -> { put("t", "i"); put("v", value) }
                            is Long -> { put("t", "l"); put("v", value) }
                            is Float -> { put("t", "f"); put("v", value) }
                            is Boolean -> { put("t", "b"); put("v", value) }
                            is Set<*> -> { put("t", "set"); put("v", JsonArray(value.map { JsonPrimitive(it.toString()) })) }
                        }
                    },
                )
            }
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(root.toString())
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
