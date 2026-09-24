package dev.naved.j2kdesktop.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads manga you already have on disk, like J2K's "Local manga".
 *
 * ~/Documents/J2KDesktop/local/
 *   Some Manga/
 *     cover.jpg                         (optional)
 *     Chapter 1/   001.jpg 002.jpg ...  <- a folder of images
 *     Chapter 2.cbz                     <- or a .cbz / .zip of images
 */
class LocalSource(
    val rootDir: File = File(System.getProperty("user.home"), "Documents/J2KDesktop/local"),
) : CatalogueSource, UnmeteredSource {
    override val id = 0L
    override val name = "Local manga"
    override val lang = "other"
    override val supportsLatest = true

    private val extractDir = File(dev.naved.j2kdesktop.compat.AppDirs.cache, "local-pages")

    init {
        rootDir.mkdirs()
    }

    override fun getFilterList() = FilterList()

    override suspend fun getPopularManga(page: Int) =
        MangasPage(mangaDirs().sortedWith(compareBy(natural) { it.name }).map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int) =
        MangasPage(mangaDirs().sortedByDescending { it.lastModified() }.map { it.toSManga() }, false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
        MangasPage(
            mangaDirs()
                .filter { it.name.contains(query, ignoreCase = true) }
                .sortedWith(compareBy(natural) { it.name })
                .map { it.toSManga() },
            false,
        )

    override suspend fun getMangaDetails(manga: SManga): SManga =
        mangaDir(manga)?.toSManga() ?: manga

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val dir = mangaDir(manga) ?: return emptyList()
        return chapterEntries(dir)
            .sortedWith(compareBy(natural) { it.name })
            .reversed() // newest (highest number) first, like J2K
            .map { entry ->
                SChapter.create().apply {
                    url = "${dir.name}/${entry.name}"
                    name = if (entry.isArchive()) entry.nameWithoutExtension else entry.name
                    date_upload = entry.lastModified()
                    chapter_number = chapterNumber(name)
                }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val entry = File(rootDir, chapter.url)
        val images = if (entry.isDirectory) {
            entry.listFiles().orEmpty()
                .filter { it.isFile && it.isImage() }
                .sortedWith(compareBy(natural) { it.name })
        } else {
            extractArchive(entry)
        }
        return images.mapIndexed { i, file -> Page(i, imageUrl = file.toFileUrl()) }
    }

    /** Coil can't read inside a zip, so unpack a chapter's images into the cache once. */
    private fun extractArchive(archive: File): List<File> {
        val target = File(extractDir, "${archive.parentFile.name}/${archive.nameWithoutExtension}")
        if (!target.exists()) {
            val tmp = File(target.path + ".tmp").apply {
                deleteRecursively()
                mkdirs()
            }
            ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.isImageName() }
                    .forEach { zipEntry ->
                        // Only keep the file name, so a bad zip can't write outside the cache
                        val out = File(tmp, zipEntry.name.substringAfterLast('/'))
                        zip.getInputStream(zipEntry).use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        }
                    }
            }
            tmp.renameTo(target)
        }
        return target.listFiles().orEmpty()
            .filter { it.isFile }
            .sortedWith(compareBy(natural) { it.name })
    }

    private fun mangaDirs(): List<File> =
        rootDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }

    private fun mangaDir(manga: SManga): File? =
        File(rootDir, manga.url).takeIf { it.isDirectory }

    private fun chapterEntries(dir: File): List<File> =
        dir.listFiles().orEmpty().filter {
            (it.isDirectory && !it.name.startsWith(".")) || it.isArchive()
        }

    private fun File.toSManga(): SManga {
        val dir = this
        return SManga.create().apply {
            url = dir.name
            title = dir.name
            thumbnail_url = findCover(dir)?.toFileUrl()
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    /** cover.jpg/png/... if present, otherwise the first image of the first folder chapter. */
    private fun findCover(dir: File): File? {
        dir.listFiles().orEmpty()
            .firstOrNull { it.isFile && it.isImage() && it.nameWithoutExtension.equals("cover", true) }
            ?.let { return it }
        val firstFolder = chapterEntries(dir)
            .filter { it.isDirectory }
            .minWithOrNull(compareBy(natural) { it.name }) ?: return null
        return firstFolder.listFiles().orEmpty()
            .filter { it.isFile && it.isImage() }
            .minWithOrNull(compareBy(natural) { it.name })
    }
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

private fun String.isImageName() = substringAfterLast('.', "").lowercase() in imageExtensions

private fun File.isImage() = name.isImageName()

private fun File.isArchive() = isFile && extension.lowercase() in setOf("cbz", "zip")

private fun File.toFileUrl() = "file://$absolutePath"

private fun chapterNumber(name: String): Float =
    Regex("""\d+(?:\.\d+)?""").find(name)?.value?.toFloatOrNull() ?: -1f

/** "Chapter 2" sorts before "Chapter 10" (plain text sorting would put 10 first). */
private val natural = Comparator<String> { a, b ->
    val chunk = Regex("""\d+|\D+""")
    val pa = chunk.findAll(a.lowercase()).map { it.value }.toList()
    val pb = chunk.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(pa.size, pb.size)) {
        val x = pa[i]
        val y = pb[i]
        val cmp = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
        if (cmp != 0) return@Comparator cmp
    }
    pa.size - pb.size
}
