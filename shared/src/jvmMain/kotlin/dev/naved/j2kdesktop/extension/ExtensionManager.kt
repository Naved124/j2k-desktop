package dev.naved.j2kdesktop.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.naved.j2kdesktop.AppBootstrap
import dev.naved.j2kdesktop.compat.AppDirs
import dev.naved.j2kdesktop.source.SourceManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Repos + installed extensions, as Compose state for the Extensions tab.
 * Heavy work runs on Dispatchers.IO; state is only changed on the UI thread.
 */
object ExtensionManager {
    var repos by mutableStateOf<List<ExtensionRepo>>(emptyList())
        private set
    var available by mutableStateOf<List<AvailableExtension>>(emptyList())
        private set
    var installed by mutableStateOf<List<InstalledExtension>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** pkg -> what's happening right now ("Installing…" etc.) */
    val busy = mutableStateMapOf<String, String>()

    private val reposFile = File(AppDirs.data, "repos.json")
    private val pendingFile = File(AppDirs.data, "pending-repos.txt")
    private lateinit var scope: CoroutineScope
    private var started = false

    /** Called once from App(): loads installed extensions, refreshes repos, watches for "add repo" links. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        this.scope = scope
        AppBootstrap.init()
        repos = loadRepos()

        scope.launch {
            loadInstalled()
            importPendingRepos()
            refresh()
            // The tachiyomi:// link handler (scripts/install-link-handler.sh) drops URLs in this file
            while (true) {
                delay(2_000)
                importPendingRepos()
            }
        }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            val current = repos
            val results = withContext(Dispatchers.IO) {
                current.map { repo -> repo to runCatching { ExtensionStoreClient.fetch(repo.url) } }
            }
            val errors = mutableListOf<String>()
            val updatedRepos = results.map { (repo, result) ->
                result.fold(
                    onSuccess = { it.repo },
                    onFailure = {
                        errors += "${repo.name}: ${it.message ?: it}"
                        repo
                    },
                )
            }
            available = results
                .flatMap { it.second.getOrNull()?.extensions.orEmpty() }
                .groupBy { it.pkg }
                .map { (_, versions) -> versions.maxBy { it.versionCode } }
                .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
            if (updatedRepos != repos) {
                repos = updatedRepos
                saveRepos()
            }
            if (errors.isNotEmpty()) message = "Couldn't refresh: " + errors.joinToString("; ")
            isRefreshing = false
        }
    }

    fun addRepo(input: String) {
        val url = normalizeRepoUrl(input) ?: run {
            message = "That doesn't look like a repo URL"
            return
        }
        if (repos.any { it.url == url }) {
            message = "That repo is already added"
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ExtensionStoreClient.fetch(url) } }
            result
                .onSuccess { fetched ->
                    repos = repos + fetched.repo
                    saveRepos()
                    message = "Added ${fetched.repo.name} (${fetched.extensions.size} extensions)"
                    refresh()
                }
                .onFailure { message = "Couldn't add repo: ${it.message ?: it}" }
        }
    }

    fun removeRepo(url: String) {
        repos = repos.filterNot { it.url == url }
        saveRepos()
        refresh()
    }

    fun install(ext: AvailableExtension) {
        if (busy.containsKey(ext.pkg)) return
        busy[ext.pkg] = "Installing…"
        scope.launch {
            try {
                // Stop using the old version's sources before swapping the jar
                installed.firstOrNull { it.meta.pkg == ext.pkg }?.sources?.forEach { SourceManager.unregister(it.id) }

                val loaded = withContext(Dispatchers.IO) {
                    // Prefer the repo's ready-made desktop jar; otherwise convert the APK
                    val useJar = ext.jarUrl != null
                    val file = File.createTempFile(ext.pkg, if (useJar) ".jar" else ".apk")
                    try {
                        NetworkHelper.default.client.newCall(GET(ext.jarUrl ?: ext.apkUrl)).awaitSuccess().use { response ->
                            file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                        }
                        val meta = if (useJar) ExtensionLoader.installJar(file, ext) else ExtensionLoader.installApk(file, ext)
                        loadOne(meta)
                    } finally {
                        file.delete()
                    }
                }
                installed = installed.filterNot { it.meta.pkg == ext.pkg } + loaded
                loaded.sources.forEach { SourceManager.register(it) }
                message = loaded.error?.let { "${ext.name} installed, but failed to load: $it" }
                    ?: "Installed ${ext.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                message = "Couldn't install ${ext.name}: ${e.message ?: e}"
            } finally {
                busy.remove(ext.pkg)
            }
        }
    }

    fun uninstall(pkg: String) {
        val ext = installed.firstOrNull { it.meta.pkg == pkg } ?: return
        ext.sources.forEach { SourceManager.unregister(it.id) }
        installed = installed.filterNot { it.meta.pkg == pkg }
        scope.launch(Dispatchers.IO) { ExtensionLoader.uninstall(pkg) }
        message = "Uninstalled ${ext.meta.name}"
    }

    fun availableUpdate(ext: InstalledExtension): AvailableExtension? =
        available.firstOrNull { it.pkg == ext.meta.pkg && it.versionCode > ext.meta.versionCode }

    private suspend fun loadInstalled() {
        val loaded = withContext(Dispatchers.IO) { ExtensionLoader.installedMetas().map { loadOne(it) } }
        installed = loaded.sortedBy { it.meta.name.lowercase() }
        loaded.flatMap { it.sources }.forEach { SourceManager.register(it) }
    }

    /** Never throws: a broken extension shows its error in the list instead of crashing the app. */
    private fun loadOne(meta: InstalledMeta): InstalledExtension =
        try {
            InstalledExtension(meta, ExtensionLoader.load(meta), null)
        } catch (e: Throwable) {
            System.err.println("[ext] failed to load ${meta.pkg}:")
            e.printStackTrace()
            val cause = generateSequence(e) { it.cause }.last()
            InstalledExtension(meta, emptyList(), "${cause::class.java.simpleName}: ${cause.message}")
        }

    private suspend fun importPendingRepos() {
        val lines = withContext(Dispatchers.IO) {
            if (!pendingFile.exists()) return@withContext emptyList()
            val text = pendingFile.readText()
            pendingFile.writeText("")
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        lines.forEach { addRepo(it) }
    }

    /** Accepts https URLs and tachiyomi://add-repo?url=… / mihon://add-repo?url=… links. */
    private fun normalizeRepoUrl(input: String): String? {
        val text = input.trim()
        val url = if (text.startsWith("tachiyomi://") || text.startsWith("mihon://")) {
            val query = runCatching { URI(text).rawQuery }.getOrNull() ?: return null
            query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == "url" }
                ?.let { URLDecoder.decode(it[1], StandardCharsets.UTF_8) }
                ?: return null
        } else {
            text
        }
        return url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun loadRepos(): List<ExtensionRepo> =
        runCatching {
            if (!reposFile.exists()) return emptyList()
            AppBootstrap.json.decodeFromString(ListSerializer(ExtensionRepo.serializer()), reposFile.readText())
        }.getOrElse {
            System.err.println("Couldn't read repos.json: $it")
            emptyList()
        }

    private fun saveRepos() {
        reposFile.writeText(AppBootstrap.json.encodeToString(ListSerializer(ExtensionRepo.serializer()), repos))
    }
}
