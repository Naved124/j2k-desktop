package dev.naved.j2kdesktop.library

import androidx.compose.runtime.mutableStateListOf
import dev.naved.j2kdesktop.compat.AndroidCompat
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

fun mangaKey(sourceId: Long, url: String) = "$sourceId|$url"

/** A manga in the library: which source it's from, its url there, and the details we last saw. */
@Serializable
data class LibraryEntry(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = SManga.UNKNOWN,
    val addedAt: Long = System.currentTimeMillis(),
    val categories: List<Long> = emptyList(),
) {
    val key: String get() = mangaKey(sourceId, url)

    fun toSManga(): SManga = SManga.create().also {
        it.url = url
        it.title = title
        it.thumbnail_url = thumbnailUrl
        it.author = author
        it.artist = artist
        it.description = description
        it.genre = genre
        it.status = status
    }
}

/** The library, saved to ~/.local/share/j2k-desktop/library.json. */
object Library {
    private val store = JsonStore("library.json", ListSerializer(LibraryEntry.serializer()))

    /** Compose-observable, so the Library tab and "In library" buttons update by themselves. */
    val entries = mutableStateListOf<LibraryEntry>().apply { store.load()?.let { addAll(it) } }

    fun get(sourceId: Long, url: String): LibraryEntry? = entries.firstOrNull { it.sourceId == sourceId && it.url == url }

    fun contains(sourceId: Long, url: String): Boolean = get(sourceId, url) != null

    fun add(sourceId: Long, manga: SManga, chapters: List<SChapter>? = null) {
        if (contains(sourceId, manga.url)) return
        entries += manga.toEntry(sourceId, System.currentTimeMillis(), emptyList())
        // Remember the chapters we have now, so the next library update only reports new ones
        chapters?.let { ChapterCache.put(sourceId, manga.url, it) }
        save()
    }

    fun remove(sourceId: Long, url: String) {
        if (entries.removeAll { it.sourceId == sourceId && it.url == url }) save()
    }

    /** Keep the stored title/cover/description fresh when a library manga is opened. */
    fun refresh(sourceId: Long, manga: SManga) {
        val index = entries.indexOfFirst { it.sourceId == sourceId && it.url == manga.url }
        if (index < 0) return
        val old = entries[index]
        val updated = manga.toEntry(sourceId, old.addedAt, old.categories)
        if (updated != old) {
            entries[index] = updated
            save()
        }
    }

    fun setCategories(sourceId: Long, url: String, categories: List<Long>) {
        val index = entries.indexOfFirst { it.sourceId == sourceId && it.url == url }
        if (index < 0) return
        entries[index] = entries[index].copy(categories = categories)
        save()
    }

    /** Backup import: add the manga, or add the backup's categories to the one already there. */
    fun importEntry(entry: LibraryEntry): Boolean {
        val index = entries.indexOfFirst { it.sourceId == entry.sourceId && it.url == entry.url }
        if (index >= 0) {
            val old = entries[index]
            entries[index] = old.copy(categories = (old.categories + entry.categories).distinct())
            save()
            return false
        }
        entries += entry
        save()
        return true
    }

    internal fun removeCategoryEverywhere(id: Long) {
        entries.indices.forEach { i ->
            val e = entries[i]
            if (id in e.categories) entries[i] = e.copy(categories = e.categories - id)
        }
        save()
    }

    private fun SManga.toEntry(sourceId: Long, addedAt: Long, categories: List<Long>) = LibraryEntry(
        sourceId = sourceId,
        url = url,
        title = runCatching { title }.getOrDefault(url),
        thumbnailUrl = thumbnail_url,
        author = author,
        artist = artist,
        description = description,
        genre = genre,
        status = status,
        addedAt = addedAt,
        categories = categories,
    )

    private fun save() = store.save(entries.toList())
}

@Serializable
data class Category(val id: Long, val name: String)

/** Library categories (tabs), like J2K. Manga without a category show under "Default". */
object Categories {
    private val store = JsonStore("categories.json", ListSerializer(Category.serializer()))

    val list = mutableStateListOf<Category>().apply { store.load()?.let { addAll(it) } }

    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || list.any { it.name.equals(clean, ignoreCase = true) }) return
        list += Category((list.maxOfOrNull { it.id } ?: 0) + 1, clean)
        save()
    }

    /** The id of the category with this name, creating it if needed (backup import). */
    fun ensure(name: String): Long {
        list.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it.id }
        add(name)
        return list.first { it.name.equals(name.trim(), ignoreCase = true) }.id
    }

    fun rename(id: Long, name: String) {
        val clean = name.trim()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0 || clean.isEmpty()) return
        list[index] = list[index].copy(name = clean)
        save()
    }

    fun delete(id: Long) {
        list.removeAll { it.id == id }
        Library.removeCategoryEverywhere(id)
        save()
    }

    fun move(id: Long, up: Boolean) {
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        save()
    }

    private fun save() = store.save(list.toList())
}

/** Which scanlation groups to hide in a manga's chapter list (remembered per manga, library or not). */
object ChapterFilters {
    private val prefs get() = AndroidCompat.sharedPreferences("chapter_filters")

    const val NO_GROUP = ""

    private fun key(sourceId: Long, url: String) = "hidden_${sourceId}_$url"

    fun hiddenGroups(sourceId: Long, url: String): Set<String> =
        prefs.getStringSet(key(sourceId, url), emptySet()).orEmpty()

    fun setHiddenGroups(sourceId: Long, url: String, hidden: Set<String>) {
        prefs.edit().putStringSet(key(sourceId, url), hidden).apply()
    }

    fun groupOf(chapter: SChapter): String = chapter.scanlator?.trim().orEmpty()

    fun <T : SChapter> visible(sourceId: Long, url: String, chapters: List<T>): List<T> {
        val hidden = hiddenGroups(sourceId, url)
        return if (hidden.isEmpty()) chapters else chapters.filter { groupOf(it) !in hidden }
    }
}
