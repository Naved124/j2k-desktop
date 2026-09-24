package dev.naved.j2kdesktop.extension

import eu.kanade.tachiyomi.source.Source
import kotlinx.serialization.Serializable

/** A repo the user added (saved to repos.json). */
@Serializable
data class ExtensionRepo(
    val url: String,
    val name: String = url,
    val badge: String = "",
    val signingKey: String? = null,
    val website: String? = null,
)

/** An extension listed in a repo. */
data class AvailableExtension(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val nsfw: Boolean,
    val apkUrl: String,
    /** Ready-made desktop jar, when the repo provides one (preferred over converting the APK). */
    val jarUrl: String? = null,
    val iconUrl: String?,
    val repoUrl: String,
    val signingKey: String?,
    val sourceNames: List<String>,
)

/** What we store next to an installed extension's jar (<pkg>.json). */
@Serializable
data class InstalledMeta(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val classNames: List<String>,
    val repoUrl: String?,
)

/** An installed extension plus whatever happened when we loaded it. */
data class InstalledExtension(
    val meta: InstalledMeta,
    val sources: List<Source>,
    val error: String?,
)
