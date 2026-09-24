package dev.naved.j2kdesktop.browser

import dev.naved.j2kdesktop.compat.AppDirs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Finds a Chromium-based browser installed on this computer. */
object BrowserLocator {
    private val linuxNames = listOf(
        "chromium", "chromium-browser", "google-chrome-stable", "google-chrome", "brave", "brave-browser",
        "brave-browser-stable", "microsoft-edge-stable", "vivaldi-stable", "vivaldi", "thorium-browser",
    )

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val isMac = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)

    val executable: String? by lazy { find() }

    private fun find(): String? {
        System.getenv("J2K_BROWSER")?.let { File(it) }?.takeIf { it.canExecute() }?.let { return it.path }
        if (isWindows) {
            val roots = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("LOCALAPPDATA"))
            val paths = listOf(
                "Google\\Chrome\\Application\\chrome.exe",
                "Microsoft\\Edge\\Application\\msedge.exe",
                "BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "Chromium\\Application\\chrome.exe",
            )
            return roots.flatMap { root -> paths.map { File(root, it) } }.firstOrNull { it.isFile }?.path
        }
        if (isMac) {
            return listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            ).firstOrNull { File(it).canExecute() }
        }
        val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparator) + listOf("/usr/bin", "/usr/local/bin", "/opt/google/chrome")
        for (name in linuxNames) {
            dirs.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.let { return it.path }
        }
        return null
    }

    const val NOT_FOUND =
        "This source needs a web browser to get past the site's checks. Install Chromium, Google Chrome, " +
            "Brave or Edge (on Arch: sudo pacman -S chromium) and try again. " +
            "To use a specific one, start J2K Desktop with J2K_BROWSER=/path/to/browser."
}

/**
 * The hidden (headless) browser the app drives over DevTools. Started on first use, shut down
 * after a few idle minutes. Tabs come from [newTab].
 */
object Browser {
    private val mutex = Mutex()
    private var process: Process? = null
    private var connection: CdpConnection? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val openTabs = AtomicInteger()
    private var idleJob: Job? = null

    private val uaFile get() = File(AppDirs.data, "browser-ua.txt")

    val isAvailable: Boolean get() = BrowserLocator.executable != null

    /** The installed browser's real User-Agent (as a normal window sends it), remembered across runs. */
    @Volatile
    private var savedUserAgent: String? = runCatching { uaFile.readText().trim() }.getOrNull()?.takeIf { it.startsWith("Mozilla/") }

    /** Used by OkHttp too, so the cookies a check earns in the browser also work for the app. */
    val userAgent: String
        get() = savedUserAgent ?: UserAgents.forChrome(UserAgents.FALLBACK_MAJOR)

    /** Brand for client hints ("Microsoft Edge", "Google Chrome", "Brave", "Chromium"). */
    val brand: String
        get() {
            val exe = BrowserLocator.executable.orEmpty().lowercase()
            return when {
                "Edg/" in userAgent || "edge" in exe -> "Microsoft Edge"
                "brave" in exe -> "Brave"
                "google" in exe || "chrome.exe" in exe || exe.endsWith("/google chrome") -> "Google Chrome"
                "vivaldi" in exe -> "Vivaldi"
                else -> "Chromium"
            }
        }

    /** A visible window told us the real UA (e.g. after a browser update): use it from now on. */
    fun rememberUserAgent(ua: String?) {
        val clean = ua?.replace("HeadlessChrome", "Chrome")?.takeIf { it.startsWith("Mozilla/") } ?: return
        if (clean != savedUserAgent) {
            savedUserAgent = clean
            runCatching { uaFile.writeText(clean) }
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread { process?.destroy() })
    }

    /** The hidden browser's connection (starting it if needed). */
    suspend fun connection(): CdpConnection = mutex.withLock { connection?.takeIf { it.isOpen } ?: launchBrowser() }

    /**
     * A tab attached for DevTools. [instrument] = page/runtime/network events (WebView needs them).
     * Cloudflare's check can notice an instrumented page, so the check-solving code uses false.
     */
    suspend fun newTab(instrument: Boolean = true, url: String = "about:blank"): CdpSession {
        val conn = connection()
        idleJob?.cancel()
        openTabs.incrementAndGet()
        try {
            val targetId = conn.send("Target.createTarget", buildJsonObject { put("url", url) })
                .string("targetId") ?: throw CdpException("The browser didn't open a tab")
            val sessionId = conn.send(
                "Target.attachToTarget",
                buildJsonObject {
                    put("targetId", targetId)
                    put("flatten", true)
                },
            ).string("sessionId") ?: throw CdpException("Couldn't attach to the browser tab")
            val session = CdpSession(conn, sessionId, targetId)
            if (instrument) {
                session.send("Page.enable")
                session.send("Runtime.enable")
                session.send("Network.enable")
                setUserAgent(session, userAgent)
            }
            return session
        } catch (e: Throwable) {
            tabClosed(conn)
            throw e
        }
    }

    /** Sets the tab's User-Agent and matching client hints. */
    suspend fun setUserAgent(session: CdpSession, ua: String) {
        session.send("Emulation.setUserAgentOverride", UserAgents.overrideParams(ua, brand))
    }

    /** CookieManager.removeAllCookies: also forget what the running browser has. */
    fun clearCookies() {
        val conn = connection?.takeIf { it.isOpen } ?: return
        scope.launch { runCatching { conn.send("Storage.clearCookies") } }
    }

    internal fun tabClosed(conn: CdpConnection) {
        if (openTabs.decrementAndGet() > 0) return
        openTabs.set(0)
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(5 * 60_000L)
            mutex.withLock {
                if (openTabs.get() == 0 && connection === conn) shutdown()
            }
        }
    }

    private fun shutdown() {
        connection?.close()
        connection = null
        process?.let { p ->
            p.destroy()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        process = null
    }

    private val headlessArgs = listOf("--headless=new", "--window-size=1366,900", "--hide-scrollbars", "--mute-audio")

    private suspend fun launchBrowser(): CdpConnection {
        shutdown()
        val exe = BrowserLocator.executable ?: throw IOException(BrowserLocator.NOT_FOUND)
        val profile = File(AppDirs.data, "browser").apply { mkdirs() }
        if (savedUserAgent == null) {
            // First run: ask the browser what it is, so the hidden one can present itself as a normal window
            val (probe, probeUrl) = BrowserProcess.start(exe, profile, headlessArgs)
            try {
                val c = CdpConnection.connect(probeUrl)
                rememberUserAgent(c.send("Browser.getVersion").string("userAgent"))
                c.close()
            } finally {
                probe.destroy()
                probe.waitFor(5, TimeUnit.SECONDS)
            }
        }
        val (proc, url) = BrowserProcess.start(exe, profile, headlessArgs + "--user-agent=$userAgent")
        process = proc
        val conn = CdpConnection.connect(url)
        connection = conn
        // Browser updated since we saved its UA? Bump the version numbers in it.
        val product = conn.send("Browser.getVersion").string("product").orEmpty()
        val major = Regex("""/(\d+)\.""").find(product)?.groupValues?.get(1)
        val savedMajor = Regex("""Chrome/(\d+)""").find(userAgent)?.groupValues?.get(1)
        if (major != null && savedMajor != null && major != savedMajor) {
            rememberUserAgent(userAgent.replace(Regex("""(Chrome|Edg)/\d+"""), "$1/$major"))
        }
        return conn
    }
}

/** Starting a browser process with DevTools enabled. */
internal object BrowserProcess {
    fun start(exe: String, profile: File, extraArgs: List<String>, startUrl: String = "about:blank"): Pair<Process, String> {
        clearStaleLock(profile, exe)
        val args = listOf(
            exe,
            "--remote-debugging-port=0",
            "--user-data-dir=${profile.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-features=Translate,MediaRouter,OptimizationHints",
            "--password-store=basic",
        ) + extraArgs + startUrl
        val process = ProcessBuilder(args)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val reader = process.errorStream.bufferedReader()
        val found = java.util.concurrent.CompletableFuture<String>()
        Thread({
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    Regex("""DevTools listening on (ws://\S+)""").find(line)?.let { found.complete(it.groupValues[1]) }
                }
            } catch (_: IOException) {
            }
            found.completeExceptionally(IOException("The browser exited before it was ready"))
        }, "browser-stderr").apply { isDaemon = true }.start()
        val url = try {
            found.get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            process.destroy()
            throw IOException("Couldn't start the browser ($exe): ${e.cause?.message ?: e.message}")
        }
        return process to url
    }

    /** If J2K crashed, an old browser may still hold the profile; Chrome would then refuse to start a second copy. */
    private fun clearStaleLock(profile: File, exe: String) {
        val lock = File(profile, "SingletonLock").toPath()
        val target = runCatching { Files.readSymbolicLink(lock).toString() }.getOrNull() ?: return
        val pid = target.substringAfterLast('-').toLongOrNull() ?: return
        ProcessHandle.of(pid).ifPresent { handle ->
            val command = handle.info().command().orElse("")
            val name = File(exe).name
            if (command.contains(name) || command.contains("chrom", ignoreCase = true) || command.contains("brave", ignoreCase = true)) {
                handle.destroy()
                runCatching { handle.onExit().get(3, TimeUnit.SECONDS) }
            }
        }
    }
}

/** User-Agent strings and matching client hints, so the browser and OkHttp look like the same Chrome. */
object UserAgents {
    const val FALLBACK_MAJOR = 140

    private val os = System.getProperty("os.name").orEmpty()

    fun forChrome(major: Int): String {
        val platform = when {
            os.startsWith("Windows", ignoreCase = true) -> "Windows NT 10.0; Win64; x64"
            os.contains("Mac", ignoreCase = true) -> "Macintosh; Intel Mac OS X 10_15_7"
            else -> "X11; Linux x86_64"
        }
        return "Mozilla/5.0 ($platform) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    /** Emulation.setUserAgentOverride parameters, with client hints derived from the UA string. */
    fun overrideParams(ua: String, brand: String): JsonObject {
        val major = Regex("""Chrome/(\d+)""").find(ua)?.groupValues?.get(1)
        val platform = when {
            "Windows" in ua -> "Windows"
            "Android" in ua -> "Android"
            "Mac OS" in ua || "Macintosh" in ua -> "macOS"
            "iPhone" in ua || "iPad" in ua -> "iOS"
            else -> "Linux"
        }
        val mobile = "Mobile" in ua
        return buildJsonObject {
            put("userAgent", ua)
            put("acceptLanguage", "en-US,en;q=0.9")
            put("platform", if (platform == "Windows") "Win32" else if (platform == "macOS") "MacIntel" else "Linux x86_64")
            if (major != null) {
                put(
                    "userAgentMetadata",
                    buildJsonObject {
                        putJsonArray("brands") {
                            addJsonObject {
                                put("brand", "Chromium")
                                put("version", major)
                            }
                            if (brand != "Chromium") {
                                addJsonObject {
                                    put("brand", brand)
                                    put("version", major)
                                }
                            }
                            addJsonObject {
                                put("brand", "Not.A/Brand")
                                put("version", "99")
                            }
                        }
                        putJsonArray("fullVersionList") {
                            addJsonObject {
                                put("brand", "Chromium")
                                put("version", "$major.0.0.0")
                            }
                            if (brand != "Chromium") {
                                addJsonObject {
                                    put("brand", brand)
                                    put("version", "$major.0.0.0")
                                }
                            }
                            addJsonObject {
                                put("brand", "Not.A/Brand")
                                put("version", "99.0.0.0")
                            }
                        }
                        put("fullVersion", "$major.0.0.0")
                        put("platform", platform)
                        put("platformVersion", if (platform == "Windows") "10.0.0" else "")
                        put("architecture", "x86")
                        put("model", "")
                        put("mobile", mobile)
                        put("bitness", "64")
                        put("wow64", false)
                    },
                )
            }
        }
    }
}
