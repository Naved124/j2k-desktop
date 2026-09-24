package dev.naved.j2kdesktop.extension

import dev.naved.j2kdesktop.AppBootstrap
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.zip.GZIPInputStream

/** Reads an extension repo in any of the formats Mihon supports. */
object ExtensionStoreClient {
    data class Fetched(val repo: ExtensionRepo, val extensions: List<AvailableExtension>)

    private val json get() = AppBootstrap.json

    /** Blocking; call from Dispatchers.IO. [userUrl] is what the user added (kept as the repo's id). */
    fun fetch(userUrl: String): Fetched = fetchInternal(userUrl.trim(), userUrl.trim(), depth = 0)

    @OptIn(ExperimentalSerializationApi::class)
    private fun fetchInternal(url: String, userUrl: String, depth: Int): Fetched {
        require(depth < 4) { "Too many redirects between repo files" }
        val bytes = download(url).gunzipIfNeeded()
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar()

        return when (first) {
            // index.min.json: a plain list. Check repo.json next to it, which may point to index v2.
            '[' -> {
                require(url.endsWith("/index.min.json")) { "Legacy repo URLs must end with /index.min.json" }
                val base = url.removeSuffix("/index.min.json")
                val legacyRepo = runCatching {
                    json.decodeFromString(LegacyRepo.serializer(), String(download("$base/repo.json")))
                }.getOrNull()
                if (legacyRepo?.indexV2 != null) return fetchInternal(legacyRepo.indexV2, userUrl, depth + 1)

                val list = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(LegacyExtension.serializer()),
                    String(bytes),
                )
                val repo = ExtensionRepo(
                    url = userUrl,
                    name = legacyRepo?.meta?.name ?: base,
                    badge = legacyRepo?.meta?.shortName ?: legacyRepo?.meta?.name.orEmpty(),
                    signingKey = legacyRepo?.meta?.signingKeyFingerprint?.takeIf { it.isNotBlank() },
                    website = legacyRepo?.meta?.website,
                )
                Fetched(repo, list.map { it.toAvailable(base, repo) })
            }

            // repo.json (legacy, may point to index v2) or a JSON version of the new store
            '{' -> {
                val text = String(bytes)
                val legacyRepo = runCatching { json.decodeFromString(LegacyRepo.serializer(), text) }.getOrNull()
                when {
                    legacyRepo?.indexV2 != null -> fetchInternal(legacyRepo.indexV2, userUrl, depth + 1)
                    legacyRepo != null -> fetchInternal(url.substringBeforeLast('/') + "/index.min.json", userUrl, depth + 1)
                    else -> fromStore(json.decodeFromString(NetworkStore.serializer(), text), userUrl)
                }
            }

            // index v2: protobuf
            else -> fromStore(ProtoBuf.decodeFromByteArray<NetworkStore>(bytes), userUrl)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun fromStore(store: NetworkStore, userUrl: String): Fetched {
        val repo = ExtensionRepo(
            url = userUrl,
            name = store.name.ifBlank { userUrl },
            badge = store.badgeLabel,
            signingKey = store.signingKey.takeIf { it.isNotBlank() },
            website = store.contact?.website,
        )
        val list = store.extensionList?.extensions
            ?: store.extensionListUrl?.let { listUrl ->
                val listBytes = download(listUrl).gunzipIfNeeded()
                if (listBytes.firstOrNull()?.toInt()?.toChar() == '{') {
                    json.decodeFromString(NetworkStore.ExtensionList.serializer(), String(listBytes)).extensions
                } else {
                    ProtoBuf.decodeFromByteArray<NetworkStore.ExtensionList>(listBytes).extensions
                }
            }
            ?: emptyList()

        return Fetched(
            repo,
            list.map { ext ->
                val langs = ext.sources.map { it.language }.toSet()
                AvailableExtension(
                    pkg = ext.packageName,
                    name = ext.name,
                    versionName = ext.versionName,
                    versionCode = ext.versionCode,
                    lang = if (langs.size == 1) langs.first() else "all",
                    nsfw = ext.contentWarning == NetworkStore.ContentWarning.NSFW ||
                        ext.contentWarning == NetworkStore.ContentWarning.MIXED,
                    apkUrl = ext.resources.apkUrl,
                    jarUrl = ext.resources.jarUrl?.takeIf { it.isNotBlank() },
                    iconUrl = ext.resources.iconUrl.takeIf { it.isNotBlank() },
                    repoUrl = userUrl,
                    signingKey = repo.signingKey,
                    sourceNames = ext.sources.map { it.name },
                )
            },
        )
    }

    private fun LegacyExtension.toAvailable(base: String, repo: ExtensionRepo) = AvailableExtension(
        pkg = pkg,
        name = name.substringAfter("Tachiyomi: "),
        versionName = version,
        versionCode = code,
        lang = lang,
        nsfw = nsfw == 1,
        apkUrl = "$base/apk/$apk",
        iconUrl = "$base/icon/$pkg.png",
        repoUrl = repo.url,
        signingKey = repo.signingKey,
        sourceNames = sources.orEmpty().map { it.name }.ifEmpty { listOf(name) },
    )

    private fun download(url: String): ByteArray =
        NetworkHelper.default.client.newCall(GET(url)).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code)
            response.body.bytes()
        }

    private fun ByteArray.gunzipIfNeeded(): ByteArray =
        if (size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()) {
            GZIPInputStream(inputStream()).use { it.readBytes() }
        } else {
            this
        }
}
