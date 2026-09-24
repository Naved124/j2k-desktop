package dev.naved.j2kdesktop.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import java.io.File

/** A decoded page. Very tall images (webtoon strips) are cut into [chunks] so the GPU can draw them. */
class DecodedPage(val width: Int, val height: Int, val chunks: List<ImageBitmap>) {
    val aspect: Float get() = if (height == 0) 0.7f else width.toFloat() / height
    val isWide: Boolean get() = width > height
}

object PageLoader {
    private const val MAX_CHUNK_HEIGHT = 3000

    /** Downloads (or reads) and decodes one page. Runs on Dispatchers.IO. */
    suspend fun load(source: Source, page: Page): DecodedPage = withContext(Dispatchers.IO) {
        val bytes = fetchBytes(source, page)
        decode(bytes)
    }

    private suspend fun fetchBytes(source: Source, page: Page): ByteArray {
        if (page.imageUrl.isNullOrBlank() && source is HttpSource) {
            page.imageUrl = source.getImageUrl(page)
        }
        val url = page.imageUrl ?: error("This page has no image URL")
        return when {
            url.startsWith("file://") -> File(url.removePrefix("file://")).readBytes()
            // Goes through the source's own client: its headers, cookies and image interceptors
            source is HttpSource -> source.getImage(page).use { it.body.bytes() }
            else -> NetworkHelper.default.client.newCall(GET(url)).awaitSuccess().use { it.body.bytes() }
        }
    }

    private fun decode(bytes: ByteArray): DecodedPage {
        val image = Image.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        if (h <= MAX_CHUNK_HEIGHT) {
            val bmp = Bitmap.makeFromImage(image)
            return DecodedPage(w, h, listOf(bmp.asComposeImageBitmap()))
        }
        // Split tall strips into pieces the GPU can handle
        val full = Bitmap.makeFromImage(image)
        val chunks = mutableListOf<ImageBitmap>()
        var top = 0
        while (top < h) {
            val bottom = minOf(top + MAX_CHUNK_HEIGHT, h)
            val part = Bitmap()
            if (full.extractSubset(part, IRect.makeLTRB(0, top, w, bottom))) {
                chunks += part.asComposeImageBitmap()
            }
            top = bottom
        }
        return DecodedPage(w, h, chunks)
    }
}
