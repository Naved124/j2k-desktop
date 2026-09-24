package dev.naved.j2kdesktop.library

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class ChapterProgress(
    val lastPage: Int = 0,
    val pageCount: Int = 0,
    val read: Boolean = false,
    val lastReadAt: Long = 0,
)

/** Reading state of one manga (library or not), plus what's needed to show it in History. */
@Serializable
data class MangaProgress(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val chapters: Map<String, ChapterProgress> = emptyMap(),
    val lastChapterUrl: String? = null,
    val lastChapterName: String? = null,
    val lastReadAt: Long = 0,
    val inHistory: Boolean = true,
) {
    fun toSManga(): SManga = SManga.create().also {
        it.url = url
        it.title = title
        it.thumbnail_url = thumbnailUrl
    }
}

/** Read chapters and last pages, saved to progress.json. */
object ReadProgress {
    private val store = JsonStore("progress.json", MapSerializer(String.serializer(), MangaProgress.serializer()))

    private val map = mutableStateMapOf<String, MangaProgress>().apply { store.load()?.let { putAll(it) } }

    fun manga(sourceId: Long, url: String): MangaProgress? = map[mangaKey(sourceId, url)]

    fun chapter(sourceId: Long, mangaUrl: String, chapterUrl: String): ChapterProgress? =
        map[mangaKey(sourceId, mangaUrl)]?.chapters?.get(chapterUrl)

    fun isRead(sourceId: Long, mangaUrl: String, chapterUrl: String): Boolean =
        chapter(sourceId, mangaUrl, chapterUrl)?.read == true

    /** Called by the reader as pages change. Reaching the last page marks the chapter read. */
    fun onPage(sourceId: Long, manga: SManga, chapter: SChapter, page: Int, lastShownPage: Int, pageCount: Int) {
        if (pageCount <= 0) return
        val key = mangaKey(sourceId, manga.url)
        val now = System.currentTimeMillis()
        val old = map[key]
        val oldChapter = old?.chapters?.get(chapter.url)
        val read = (oldChapter?.read == true) || lastShownPage >= pageCount - 1
        val progress = ChapterProgress(lastPage = page, pageCount = pageCount, read = read, lastReadAt = now)
        map[key] = (old ?: MangaProgress(sourceId, manga.url, safeTitle(manga))).copy(
            title = safeTitle(manga),
            thumbnailUrl = manga.thumbnail_url ?: old?.thumbnailUrl,
            chapters = (old?.chapters.orEmpty()) + (chapter.url to progress),
            lastChapterUrl = chapter.url,
            lastChapterName = chapter.name,
            lastReadAt = now,
            inHistory = true,
        )
        save()
        if (read && oldChapter?.read != true) {
            dev.naved.j2kdesktop.tracking.Tracking.onChapterRead(sourceId, manga.url, chapter.chapter_number)
        }
    }

    fun setRead(sourceId: Long, manga: SManga, chapters: List<SChapter>, read: Boolean) {
        if (chapters.isEmpty()) return
        val key = mangaKey(sourceId, manga.url)
        val old = map[key] ?: MangaProgress(sourceId, manga.url, safeTitle(manga), manga.thumbnail_url, inHistory = false)
        val updated = old.chapters.toMutableMap()
        chapters.forEach { ch ->
            val prev = updated[ch.url] ?: ChapterProgress()
            updated[ch.url] = if (read) prev.copy(read = true) else prev.copy(read = false, lastPage = 0)
        }
        map[key] = old.copy(chapters = updated)
        save()
        if (read) {
            chapters.maxOfOrNull { it.chapter_number }?.let {
                dev.naved.j2kdesktop.tracking.Tracking.onChapterRead(sourceId, manga.url, it)
            }
        }
    }

    /** Backup import: merge (read wins, furthest page wins, newest read time wins). */
    fun importManga(incoming: MangaProgress) {
        val key = mangaKey(incoming.sourceId, incoming.url)
        val old = map[key]
        if (old == null) {
            map[key] = incoming
        } else {
            val merged = old.chapters.toMutableMap()
            incoming.chapters.forEach { (url, new) ->
                val prev = merged[url]
                merged[url] = if (prev == null) {
                    new
                } else {
                    ChapterProgress(
                        lastPage = maxOf(prev.lastPage, new.lastPage),
                        pageCount = maxOf(prev.pageCount, new.pageCount),
                        read = prev.read || new.read,
                        lastReadAt = maxOf(prev.lastReadAt, new.lastReadAt),
                    )
                }
            }
            val newer = incoming.lastReadAt > old.lastReadAt
            map[key] = old.copy(
                chapters = merged,
                lastChapterUrl = if (newer) incoming.lastChapterUrl else old.lastChapterUrl,
                lastChapterName = if (newer) incoming.lastChapterName else old.lastChapterName,
                lastReadAt = maxOf(old.lastReadAt, incoming.lastReadAt),
                inHistory = old.inHistory || incoming.inHistory,
                thumbnailUrl = old.thumbnailUrl ?: incoming.thumbnailUrl,
            )
        }
        save()
    }

    /** Everything (backup export). */
    fun all(): List<MangaProgress> = map.values.toList()

    /** History: the last chapter read of each manga, newest first. */
    fun history(): List<MangaProgress> = map.values.filter { it.inHistory && it.lastReadAt > 0 }.sortedByDescending { it.lastReadAt }

    fun removeFromHistory(sourceId: Long, url: String) {
        val key = mangaKey(sourceId, url)
        map[key]?.let { map[key] = it.copy(inHistory = false) }
        save()
    }

    fun clearHistory() {
        map.keys.toList().forEach { k -> map[k] = map.getValue(k).copy(inHistory = false) }
        save()
    }

    /** In reading order (oldest first): where "Continue reading" goes. */
    fun <T : SChapter> continueIndex(sourceId: Long, mangaUrl: String, ordered: List<T>): Int {
        if (ordered.isEmpty()) return -1
        val progress = manga(sourceId, mangaUrl) ?: return 0
        val last = ordered.indexOfFirst { it.url == progress.lastChapterUrl }
        if (last >= 0) {
            val state = progress.chapters[ordered[last].url]
            if (state?.read != true) return last
            val next = (last + 1 until ordered.size).firstOrNull { progress.chapters[ordered[it].url]?.read != true }
            if (next != null) return next
        }
        return ordered.indexOfFirst { progress.chapters[it.url]?.read != true }.takeIf { it >= 0 } ?: (ordered.size - 1)
    }

    private fun safeTitle(manga: SManga) = runCatching { manga.title }.getOrDefault(manga.url)

    private fun save() = store.save(map.toMap())
}

@Serializable
data class CachedChapter(
    val url: String,
    val name: String,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0,
    val scanlator: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().also {
        it.url = url
        it.name = name
        it.chapter_number = chapterNumber
        it.date_upload = dateUpload
        it.scanlator = scanlator
    }
}

/** The last known chapter list of each library manga (for unread counts and finding new chapters). */
object ChapterCache {
    private val store = JsonStore("library-chapters.json", MapSerializer(String.serializer(), ListSerializer(CachedChapter.serializer())))

    private val map = mutableStateMapOf<String, List<CachedChapter>>().apply { store.load()?.let { putAll(it) } }

    fun get(sourceId: Long, url: String): List<CachedChapter>? = map[mangaKey(sourceId, url)]

    /** Stores the list (newest first, as sources give it) and returns the chapters that weren't known before. */
    fun put(sourceId: Long, url: String, chapters: List<SChapter>): List<CachedChapter> {
        val key = mangaKey(sourceId, url)
        val old = map[key]
        val cached = chapters.map {
            CachedChapter(it.url, it.name, it.chapter_number, it.date_upload, it.scanlator)
        }
        if (old == cached) return emptyList()
        map[key] = cached
        store.save(map.toMap())
        if (old == null) return emptyList()
        val known = old.mapTo(HashSet()) { it.url }
        return cached.filter { it.url !in known }
    }

    /** Backup import: only fills in manga we don't have a list for yet. */
    fun importIfMissing(sourceId: Long, url: String, chapters: List<CachedChapter>) {
        val key = mangaKey(sourceId, url)
        if (map[key] != null || chapters.isEmpty()) return
        map[key] = chapters
        store.save(map.toMap())
    }

    /** Unread chapters of a library manga, respecting its hidden scanlator groups. */
    fun unreadCount(sourceId: Long, url: String): Int? {
        val chapters = get(sourceId, url) ?: return null
        val hidden = ChapterFilters.hiddenGroups(sourceId, url)
        return chapters.count { ch ->
            (ch.scanlator?.trim().orEmpty() !in hidden) && !ReadProgress.isRead(sourceId, url, ch.url)
        }
    }
}

@Serializable
data class UpdateEntry(
    val sourceId: Long,
    val mangaUrl: String,
    val mangaTitle: String,
    val thumbnailUrl: String? = null,
    val chapterUrl: String,
    val chapterName: String,
    val foundAt: Long,
)

/** New chapters found by library updates (Recents → Updates). */
object Updates {
    private val store = JsonStore("updates.json", ListSerializer(UpdateEntry.serializer()))

    val list = mutableStateListOf<UpdateEntry>().apply { store.load()?.let { addAll(it) } }

    fun add(entries: List<UpdateEntry>) {
        if (entries.isEmpty()) return
        list.addAll(0, entries)
        while (list.size > 1000) list.removeAt(list.lastIndex)
        store.save(list.toList())
    }

    fun clear() {
        list.clear()
        store.save(emptyList())
    }
}
