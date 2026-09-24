@file:OptIn(ExperimentalSerializationApi::class)

package dev.naved.j2kdesktop.backup

import dev.naved.j2kdesktop.extension.ExtensionManager
import dev.naved.j2kdesktop.library.CachedChapter
import dev.naved.j2kdesktop.library.Categories
import dev.naved.j2kdesktop.library.ChapterCache
import dev.naved.j2kdesktop.library.ChapterFilters
import dev.naved.j2kdesktop.library.ChapterProgress
import dev.naved.j2kdesktop.library.Library
import dev.naved.j2kdesktop.library.LibraryEntry
import dev.naved.j2kdesktop.library.MangaProgress
import dev.naved.j2kdesktop.library.ReadProgress
import dev.naved.j2kdesktop.source.SourceManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Imports backups from Tachiyomi / TachiyomiJ2K / Mihon (.tachibk, .proto.gz) and writes our own in the same format. */
object BackupManager {
    class ImportResult(
        val mangaInBackup: Int,
        val addedToLibrary: Int,
        val categories: Int,
        val chaptersRead: Int,
        val historyEntries: Int,
        val reposAdded: Int,
        val missingSources: List<String>,
    ) {
        override fun toString() = buildString {
            append("Library: $addedToLibrary new of $mangaInBackup manga · ")
            append("$categories categories · $chaptersRead chapters marked read · $historyEntries in history")
            if (reposAdded > 0) append(" · $reposAdded extension repo${if (reposAdded == 1) "" else "s"} added")
            if (missingSources.isNotEmpty()) {
                append("\nInstall these extensions to open those manga: ${missingSources.joinToString(", ")}")
            }
        }
    }

    /** Reads (on a background thread) a gzipped or plain protobuf backup. */
    fun read(file: File): Backup {
        val raw = file.readBytes()
        val gzipped = raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
        val bytes = if (gzipped) GZIPInputStream(raw.inputStream()).use { it.readBytes() } else raw
        return ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
    }

    /** Applies a backup (on the UI thread: it updates the app's state). Merges; never deletes anything. */
    fun import(backup: Backup): ImportResult {
        // Categories: manga refer to them by their "order" number
        val categoryIds = backup.backupCategories.associate { it.order to Categories.ensure(it.name) }

        var added = 0
        var chaptersRead = 0
        var history = 0
        backup.backupManga.forEach { m ->
            val historyByUrl = m.history.associate { it.url to it.lastRead }
            if (m.favorite) {
                val entry = LibraryEntry(
                    sourceId = m.source,
                    url = m.url,
                    title = m.title.ifBlank { m.url },
                    thumbnailUrl = m.thumbnailUrl,
                    author = m.author,
                    artist = m.artist,
                    description = m.description,
                    genre = m.genre.joinToString(", ").ifBlank { null },
                    status = m.status,
                    addedAt = m.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    categories = m.categories.mapNotNull { categoryIds[it] }.distinct(),
                )
                if (Library.importEntry(entry)) added++
                ChapterCache.importIfMissing(
                    m.source,
                    m.url,
                    m.chapters.sortedBy { it.sourceOrder }.map {
                        CachedChapter(it.url, it.name, it.chapterNumber, it.dateUpload, it.scanlator)
                    },
                )
            }
            if (m.excludedScanlators.isNotEmpty() && ChapterFilters.hiddenGroups(m.source, m.url).isEmpty()) {
                ChapterFilters.setHiddenGroups(m.source, m.url, m.excludedScanlators.toSet())
            }

            val progress = m.chapters
                .filter { it.read || it.lastPageRead > 0 || it.url in historyByUrl }
                .associate {
                    it.url to ChapterProgress(
                        lastPage = it.lastPageRead.toInt().coerceAtLeast(0),
                        pageCount = 0,
                        read = it.read,
                        lastReadAt = historyByUrl[it.url] ?: 0,
                    )
                }
            if (progress.isNotEmpty()) {
                chaptersRead += progress.values.count { it.read }
                val last = m.history.maxByOrNull { it.lastRead }
                if (last != null && last.lastRead > 0) history++
                ReadProgress.importManga(
                    MangaProgress(
                        sourceId = m.source,
                        url = m.url,
                        title = m.title.ifBlank { m.url },
                        thumbnailUrl = m.thumbnailUrl,
                        chapters = progress,
                        lastChapterUrl = last?.url,
                        lastChapterName = last?.let { h -> m.chapters.firstOrNull { it.url == h.url }?.name },
                        lastReadAt = last?.lastRead ?: 0,
                        inHistory = last != null && last.lastRead > 0,
                    ),
                )
            }
        }

        // Your extension repos (the backup's, not any default ones)
        var repos = 0
        backup.backupExtensionRepo.forEach { repo ->
            val base = repo.baseUrl.trimEnd('/')
            if (base.isNotBlank() && ExtensionManager.repos.none { it.url.startsWith(base) }) {
                ExtensionManager.addRepo("$base/index.min.json")
                repos++
            }
        }

        val usedSources = backup.backupManga.map { it.source }.toSet()
        val missing = backup.backupSources
            .filter { it.sourceId in usedSources && SourceManager.get(it.sourceId) == null }
            .map { it.name.ifBlank { it.sourceId.toString() } }
            .distinct()
            .sorted()

        return ImportResult(backup.backupManga.size, added, backup.backupCategories.size, chaptersRead, history, repos, missing)
    }

    fun defaultBackupName(): String =
        "j2k-desktop_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".tachibk"

    /** Builds a backup of everything (on the UI thread), in the Tachiyomi/Mihon format. */
    fun create(): Backup {
        val categories = Categories.list.mapIndexed { i, c -> BackupCategory(c.name, i.toLong()) }
        val orderOf = Categories.list.mapIndexed { i, c -> c.id to i.toLong() }.toMap()
        val progressByKey = ReadProgress.all().associateBy { "${it.sourceId}|${it.url}" }

        fun chaptersFor(sourceId: Long, url: String, progress: MangaProgress?): List<BackupChapter> {
            val cached = ChapterCache.get(sourceId, url).orEmpty()
            val fromCache = cached.mapIndexed { i, ch ->
                val p = progress?.chapters?.get(ch.url)
                BackupChapter(
                    url = ch.url,
                    name = ch.name,
                    scanlator = ch.scanlator,
                    read = p?.read == true,
                    lastPageRead = (p?.lastPage ?: 0).toLong(),
                    dateUpload = ch.dateUpload,
                    chapterNumber = ch.chapterNumber,
                    sourceOrder = i.toLong(),
                )
            }
            val known = cached.mapTo(HashSet()) { it.url }
            val extra = progress?.chapters.orEmpty().filterKeys { it !in known }.map { (chUrl, p) ->
                BackupChapter(
                    url = chUrl,
                    name = if (chUrl == progress?.lastChapterUrl) progress?.lastChapterName.orEmpty() else "",
                    read = p.read,
                    lastPageRead = p.lastPage.toLong(),
                    sourceOrder = (cached.size + 1).toLong(),
                )
            }
            return fromCache + extra
        }

        fun historyFor(progress: MangaProgress?): List<BackupHistory> =
            progress?.chapters.orEmpty().filter { it.value.lastReadAt > 0 }.map { (url, p) -> BackupHistory(url, p.lastReadAt) }

        val libraryManga = Library.entries.map { e ->
            val progress = progressByKey[e.key]
            BackupManga(
                source = e.sourceId,
                url = e.url,
                title = e.title,
                artist = e.artist,
                author = e.author,
                description = e.description,
                genre = e.genre?.split(", ")?.filter { it.isNotBlank() }.orEmpty(),
                status = e.status,
                thumbnailUrl = e.thumbnailUrl,
                dateAdded = e.addedAt,
                chapters = chaptersFor(e.sourceId, e.url, progress),
                categories = e.categories.mapNotNull { orderOf[it] },
                favorite = true,
                history = historyFor(progress),
                excludedScanlators = ChapterFilters.hiddenGroups(e.sourceId, e.url).toList(),
            )
        }
        val libraryKeys = Library.entries.mapTo(HashSet()) { it.key }
        val readOnly = ReadProgress.all().filter { "${it.sourceId}|${it.url}" !in libraryKeys }.map { p ->
            BackupManga(
                source = p.sourceId,
                url = p.url,
                title = p.title,
                thumbnailUrl = p.thumbnailUrl,
                chapters = chaptersFor(p.sourceId, p.url, p),
                favorite = false,
                history = historyFor(p),
            )
        }
        val allManga = libraryManga + readOnly
        val sources = allManga.map { it.source }.distinct().map { id ->
            BackupSource(SourceManager.get(id)?.name ?: id.toString(), id)
        }
        val repos = ExtensionManager.repos.map { r ->
            BackupExtensionRepos(
                baseUrl = r.url.removeSuffix("/index.min.json").removeSuffix("/repo.json").removeSuffix("/index.json"),
                name = r.name,
                website = r.website.orEmpty(),
                signingKeyFingerprint = r.signingKey.orEmpty(),
            )
        }
        return Backup(allManga, categories, sources, repos)
    }

    /** Writes a backup (background thread). */
    fun write(backup: Backup, file: File) {
        file.parentFile?.mkdirs()
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        GZIPOutputStream(file.outputStream()).use { it.write(bytes) }
    }
}
