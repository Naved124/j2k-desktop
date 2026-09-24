@file:OptIn(ExperimentalSerializationApi::class)

package dev.naved.j2kdesktop.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// The Tachiyomi/Mihon/J2K backup format (.tachibk / .proto.gz): gzipped protobuf.
// Field numbers match the Android apps, so their backups import here and ours import there.
// Fields we don't use are left out; the decoder skips them.

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(106) val backupExtensionRepo: List<BackupExtensionRepos> = emptyList(),
)

@Serializable
data class BackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(16) val chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(104) val history: List<BackupHistory> = emptyList(),
    @ProtoNumber(108) val excludedScanlators: List<String> = emptyList(),
)

@Serializable
data class BackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0f,
    @ProtoNumber(10) val sourceOrder: Long = 0,
)

@Serializable
data class BackupCategory(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val order: Long = 0,
    @ProtoNumber(100) val flags: Long = 0,
)

@Serializable
data class BackupHistory(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val lastRead: Long,
    @ProtoNumber(3) val readDuration: Long = 0,
)

@Serializable
data class BackupSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long,
)

@Serializable
data class BackupExtensionRepos(
    @ProtoNumber(1) val baseUrl: String,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val shortName: String? = null,
    @ProtoNumber(4) val website: String = "",
    @ProtoNumber(5) val signingKeyFingerprint: String = "",
)
