@file:OptIn(ExperimentalSerializationApi::class)

package dev.naved.j2kdesktop.extension

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Repo formats, matching what Mihon reads:
// - new "index v2": gzipped protobuf (NetworkStore)
// - legacy: repo.json (LegacyRepo, may point to index_v2) + index.min.json (List<LegacyExtension>)

@Serializable
data class NetworkStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList())

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: Resources = Resources(),
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String = "",
        @ProtoNumber(2) val iconUrl: String = "",
        // Prebuilt JVM jar (Keiyoushi publishes these for desktop apps like Suwayomi)
        @ProtoNumber(501) val jarUrl: String? = null,
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long = 0,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val language: String = "",
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(7) val message: String? = null,
    )

    @Serializable
    enum class ContentWarning {
        @ProtoNumber(0) UNSPECIFIED,
        @ProtoNumber(1) SAFE,
        @ProtoNumber(2) MIXED,
        @ProtoNumber(3) NSFW,
    }
}

@Serializable
data class LegacyRepo(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: Meta,
) {
    @Serializable
    data class Meta(
        val name: String,
        val shortName: String? = null,
        val website: String = "",
        val signingKeyFingerprint: String = "",
    )
}

@Serializable
data class LegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<Source>? = null,
) {
    @Serializable
    data class Source(
        val name: String = "",
        val lang: String = "",
        val baseUrl: String = "",
    )
}
