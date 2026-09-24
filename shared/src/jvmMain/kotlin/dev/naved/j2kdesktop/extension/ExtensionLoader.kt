package dev.naved.j2kdesktop.extension

import com.googlecode.d2j.dex.BaseDexExceptionHandler
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import dev.naved.j2kdesktop.AppBootstrap
import dev.naved.j2kdesktop.compat.AppDirs
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import net.dongliu.apk.parser.ApkFile
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Turns an Android extension APK into something the JVM can run:
 * read its manifest → convert its dex bytecode to a .jar (dex2jar) → load it with a class loader.
 */
object ExtensionLoader {
    private const val META_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val META_NSFW = "tachiyomi.extension.nsfw"

    private val loaders = mutableMapOf<String, URLClassLoader>()

    private fun jarFile(pkg: String) = File(AppDirs.extensions, "$pkg.jar")
    private fun metaFile(pkg: String) = File(AppDirs.extensions, "$pkg.json")

    /** Converts and stores an APK. Blocking; call from Dispatchers.IO. */
    fun installApk(apk: File, info: AvailableExtension): InstalledMeta {
        checkSignature(apk, info)
        val manifest = readApkManifest(apk)
        return finishInstall(manifest, info) { tmpJar ->
            dex2jar(apk, tmpJar)
            copyAssets(apk, tmpJar)
        }
    }

    /** Stores a ready-made desktop jar (has a plain-text AndroidManifest.xml inside). */
    fun installJar(jar: File, info: AvailableExtension): InstalledMeta {
        checkSignature(jar, info)
        val manifest = readJarManifest(jar)
        return finishInstall(manifest, info) { tmpJar -> jar.copyTo(tmpJar, overwrite = true) }
    }

    private fun finishInstall(manifest: Manifest, info: AvailableExtension, writeJar: (File) -> Unit): InstalledMeta {
        val classes = manifest.metaData[META_SOURCE_CLASS]
            ?.split(';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { if (it.startsWith(".")) manifest.packageName + it else it }
            ?: error("Not a Tachiyomi extension (the manifest has no $META_SOURCE_CLASS)")

        val pkg = manifest.packageName
        close(pkg) // release the old version's jar before replacing it

        val tmpJar = File(AppDirs.extensions, "$pkg.jar.tmp")
        tmpJar.delete()
        writeJar(tmpJar)
        Files.move(tmpJar.toPath(), jarFile(pkg).toPath(), StandardCopyOption.REPLACE_EXISTING)

        val meta = InstalledMeta(
            pkg = pkg,
            name = manifest.metaData["tachiyomix.name"] ?: info.name,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            lang = info.lang,
            nsfw = info.nsfw || manifest.metaData[META_NSFW] == "1",
            iconUrl = info.iconUrl,
            classNames = classes,
            repoUrl = info.repoUrl,
        )
        metaFile(pkg).writeText(AppBootstrap.json.encodeToString(InstalledMeta.serializer(), meta))
        return meta
    }

    /** Refuse files that weren't signed by the repo's key (when both are known). */
    private fun checkSignature(file: File, info: AvailableExtension) {
        val fingerprint = signerFingerprint(file)
        val expected = info.signingKey
        if (expected != null && fingerprint != null && !fingerprint.equals(expected, ignoreCase = true)) {
            error("The file's signature doesn't match the repo's signing key, so it wasn't installed.")
        }
        if (fingerprint == null) System.err.println("[ext] ${info.pkg}: couldn't read a v1 signature, skipping the check")
    }

    /** Every extension installed on disk. */
    fun installedMetas(): List<InstalledMeta> =
        AppDirs.extensions.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching { AppBootstrap.json.decodeFromString(InstalledMeta.serializer(), f.readText()) }
                .onFailure { System.err.println("[ext] bad metadata ${f.name}: $it") }
                .getOrNull()
                ?.takeIf { jarFile(it.pkg).exists() }
        }

    /** Instantiates the extension's sources. Blocking; call from Dispatchers.IO. */
    fun load(meta: InstalledMeta): List<Source> {
        close(meta.pkg)
        // Parent-first: app classes (eu.kanade.*, okhttp, kotlin, android stand-ins) always come from the app
        val loader = URLClassLoader(arrayOf(jarFile(meta.pkg).toURI().toURL()), ExtensionLoader::class.java.classLoader)
        loaders[meta.pkg] = loader
        return meta.classNames.flatMap { className ->
            when (val instance = Class.forName(className, false, loader).getDeclaredConstructor().newInstance()) {
                is Source -> listOf(instance)
                is SourceFactory -> instance.createSources()
                else -> error("$className is neither a Source nor a SourceFactory")
            }
        }
    }

    fun uninstall(pkg: String) {
        close(pkg)
        jarFile(pkg).delete()
        metaFile(pkg).delete()
    }

    private fun close(pkg: String) {
        loaders.remove(pkg)?.let { runCatching { it.close() } }
    }

    private data class Manifest(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val metaData: Map<String, String>,
    )

    private fun readApkManifest(apk: File): Manifest = ApkFile(apk).use { parsed ->
        val apkMeta = parsed.apkMeta
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(parsed.manifestXml)))
        Manifest(
            packageName = apkMeta.packageName,
            versionName = apkMeta.versionName ?: "",
            versionCode = apkMeta.versionCode ?: 0L,
            metaData = metaDataOf(doc),
        )
    }

    private fun readJarManifest(jar: File): Manifest = ZipFile(jar).use { zip ->
        val entry = zip.getEntry("AndroidManifest.xml") ?: error("The extension jar has no AndroidManifest.xml")
        val doc = zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val root = doc.documentElement
        Manifest(
            packageName = root.getAttribute("package"),
            versionName = root.getAttribute("android:versionName"),
            versionCode = root.getAttribute("android:versionCode").toLongOrNull() ?: 0L,
            metaData = metaDataOf(doc),
        )
    }

    private fun metaDataOf(doc: Document): Map<String, String> {
        val metaData = mutableMapOf<String, String>()
        val application = doc.getElementsByTagName("application").item(0) as? Element
        val nodes = application?.getElementsByTagName("meta-data")
        for (i in 0 until (nodes?.length ?: 0)) {
            val el = nodes!!.item(i) as? Element ?: continue
            val name = el.getAttribute("android:name")
            if (name.isNotEmpty()) metaData[name] = el.getAttribute("android:value")
        }
        return metaData
    }

    /** Same options Suwayomi uses (adapted from dex2jar's command-line tool). */
    private fun dex2jar(apk: File, outJar: File) {
        val reader = MultiDexFileReader.open(apk.readBytes())
        Dex2jar.from(reader)
            .withExceptionHandler(BaseDexExceptionHandler())
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .dontSanitizeNames(true)
            .computeFrames(true)
            .to(outJar.toPath())
    }

    /** Some extensions read files from their APK's assets/ via getResourceAsStream. */
    private fun copyAssets(apk: File, jar: File) {
        ZipFile(apk).use { zip ->
            val assets = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("assets/") }.toList()
            if (assets.isEmpty()) return
            FileSystems.newFileSystem(jar.toPath()).use { fs ->
                for (entry in assets) {
                    val target = fs.getPath("/" + entry.name)
                    target.parent?.let { Files.createDirectories(it) }
                    zip.getInputStream(entry).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }

    /** SHA-256 of the APK's (v1) signing certificate, or null if it can't be read. */
    private fun signerFingerprint(apk: File): String? = runCatching {
        JarFile(apk, true).use { jar ->
            val entry = jar.getJarEntry("classes.dex")
                ?: jar.entries().asSequence().firstOrNull { it.name.endsWith(".class") }
                ?: return@use null
            jar.getInputStream(entry).use { it.readBytes() } // must be read fully before certs are available
            val cert = entry.certificates?.firstOrNull() ?: return@use null
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()
}
