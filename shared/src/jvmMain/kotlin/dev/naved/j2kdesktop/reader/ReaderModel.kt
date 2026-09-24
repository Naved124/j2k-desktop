package dev.naved.j2kdesktop.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.naved.j2kdesktop.compat.AndroidCompat
import dev.naved.j2kdesktop.download.DownloadManager
import dev.naved.j2kdesktop.library.ReadProgress
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class ReadingMode(val label: String, val hint: String) {
    Paged("Paged", "One page at a time, turn with swipes or taps"),
    Vertical("Vertical", "Whole pages stacked, scroll smoothly"),
    Webtoon("Webtoon", "One long strip, no gaps"),
    ;

    val scrolls get() = this != Paged
}

sealed interface PageState {
    data object Loading : PageState
    data class Ready(val page: DecodedPage) : PageState
    data class Failed(val message: String) : PageState
}

/** A chapter that's open in the reader, with its pages. */
class LoadedChapter(val index: Int, val chapter: SChapter, val pages: List<Page>)

/** One screen in paged mode: one page, or two side by side when spreads are on. */
data class Slot(val chapterIndex: Int, val pages: List<Int>)

class ReaderModel(
    private val request: ReaderRequest,
    private val scope: CoroutineScope,
) {
    val source = request.source
    val manga = request.manga
    val chapters = request.chapters

    private val prefs = AndroidCompat.sharedPreferences("reader")
    private val mangaKey = "${source.id}_${manga.url}"

    // ----- Settings (remembered per manga; spread/width are global) -----

    var mode by mutableStateOf(loadMode())
        private set
    var rightToLeft by mutableStateOf(prefs.getBoolean("rtl_$mangaKey", dev.naved.j2kdesktop.AppSettings.defaultRightToLeft))
        private set
    var spread by mutableStateOf(prefs.getBoolean("spread", false))
        private set
    var shiftSpread by mutableStateOf(prefs.getBoolean("shift_$mangaKey", false))
        private set
    var webtoonWidth by mutableStateOf(prefs.getInt("webtoon_width", 800))
        private set
    /** Vertical mode: page height as a multiple of the window height. */
    var verticalScale by mutableStateOf(prefs.getFloat("vertical_scale", 1f))
        private set
    var showPageNumber by mutableStateOf(prefs.getBoolean("show_page_number", true))
        private set

    // ----- Content -----

    /** Paged mode shows one chapter; webtoon mode appends chapters as you scroll. */
    val loaded = mutableStateListOf<LoadedChapter>()
    var currentChapterIndex by mutableStateOf(request.startIndex)
        private set
    var slotIndex by mutableStateOf(0)

    /** Scroll modes: the page (of the current chapter) that's on screen. */
    var scrollPage by mutableStateOf(0)
        private set
    /** Scroll modes: where the list should jump next (chapter index to page index). */
    var scrollTarget by mutableStateOf<Pair<Int, Int>?>(null)
    var isLoadingChapter by mutableStateOf(false)
        private set
    var chapterError by mutableStateOf<String?>(null)
        private set

    private val pageStates = mutableStateMapOf<String, PageState>()
    private val downloads = Semaphore(3)

    fun start() {
        openChapter(request.startIndex)
    }

    // ----- Chapters -----

    val currentChapter: LoadedChapter? get() = loaded.firstOrNull { it.index == currentChapterIndex }
    val hasNextChapter get() = currentChapterIndex + 1 < chapters.size
    val hasPreviousChapter get() = currentChapterIndex > 0

    /** Replaces what's open with chapter [index] (used by paged mode and chapter buttons). */
    fun openChapter(index: Int, atEnd: Boolean = false) {
        if (index !in chapters.indices) return
        scope.launch {
            val chapter = fetchChapter(index) ?: return@launch
            loaded.clear()
            loaded += chapter
            currentChapterIndex = index
            // Unfinished chapter: continue where you stopped
            val saved = ReadProgress.chapter(source.id, manga.url, chapter.chapter.url)
            val resume = saved?.lastPage?.takeIf { !atEnd && saved?.read != true && it in 1 until chapter.pages.size }
            slotIndex = when {
                atEnd -> (slots().size - 1).coerceAtLeast(0)
                resume != null -> slots().indexOfFirst { resume in it.pages }.coerceAtLeast(0)
                else -> 0
            }
            scrollPage = when {
                atEnd -> chapter.pages.size - 1
                resume != null -> resume
                else -> 0
            }
            scrollTarget = index to scrollPage
            preload()
        }
    }

    /** Webtoon mode: add the next chapter below the current ones. */
    fun appendNextChapter() {
        val last = loaded.lastOrNull() ?: return
        val next = last.index + 1
        if (next !in chapters.indices || isLoadingChapter) return
        scope.launch {
            val chapter = fetchChapter(next) ?: return@launch
            if (loaded.none { it.index == next }) loaded += chapter
        }
    }

    /** Scroll modes: called as the list scrolls, with what's on screen. */
    fun markVisible(chapterIndex: Int, pageIndex: Int) {
        val changed = chapterIndex != currentChapterIndex || pageIndex != scrollPage
        currentChapterIndex = chapterIndex
        scrollPage = pageIndex
        if (changed) preload()
    }

    /** Scroll modes: position of a page in the list (each chapter is its pages plus a divider). */
    fun itemIndexOf(chapterIndex: Int, pageIndex: Int): Int? {
        var index = 0
        for (chapter in loaded) {
            if (chapter.index == chapterIndex) {
                return index + pageIndex.coerceIn(0, (chapter.pages.size - 1).coerceAtLeast(0))
            }
            index += chapter.pages.size + 1
        }
        return null
    }

    /** Page (0-based) shown now, in any mode. */
    val currentPageIndex: Int
        get() = if (mode.scrolls) scrollPage else slots().getOrNull(slotIndex)?.pages?.firstOrNull() ?: 0

    val pageCount: Int get() = currentChapter?.pages?.size ?: 0

    /** "5 / 20", or "5-6 / 20" for a spread. */
    fun pageLabel(): String {
        val count = pageCount
        if (count == 0) return ""
        val shown = if (mode.scrolls) {
            (scrollPage + 1).toString()
        } else {
            slots().getOrNull(slotIndex)?.pages?.joinToString("-") { (it + 1).toString() } ?: "1"
        }
        return "$shown / $count"
    }

    /** Jump to a page of the current chapter, in any mode (used by the slider). */
    fun goToPage(pageIndex: Int) {
        if (mode.scrolls) {
            scrollPage = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            scrollTarget = currentChapterIndex to scrollPage
            preload()
        } else {
            goToSlot(slots().indexOfFirst { pageIndex in it.pages }.coerceAtLeast(0))
        }
    }

    private suspend fun fetchChapter(index: Int): LoadedChapter? {
        isLoadingChapter = true
        chapterError = null
        return try {
            val chapter = chapters[index]
            // Downloaded chapters read from disk (works offline)
            val pages = withContext(Dispatchers.IO) {
                DownloadManager.localPages(source, manga, chapter) ?: source.getPageList(chapter)
            }
            if (pages.isEmpty()) error("This chapter has no pages")
            LoadedChapter(index, chapter, pages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            chapterError = e.message ?: e.toString()
            null
        } finally {
            isLoadingChapter = false
        }
    }

    /** Saves the page you're on; the last page (or webtoon's end-of-chapter) marks the chapter read. */
    fun saveProgress() {
        val chapter = currentChapter ?: return
        val first = currentPageIndex
        val lastShown = if (mode.scrolls) scrollPage else slots().getOrNull(slotIndex)?.pages?.lastOrNull() ?: first
        ReadProgress.onPage(source.id, manga, chapter.chapter, first, lastShown, chapter.pages.size)
    }

    // ----- Pages -----

    private fun key(chapterIndex: Int, pageIndex: Int) = "$chapterIndex:$pageIndex"

    fun pageState(chapterIndex: Int, pageIndex: Int): PageState {
        val k = key(chapterIndex, pageIndex)
        val state = pageStates[k] ?: return PageState.Loading.also { requestPage(chapterIndex, pageIndex) }
        if (state is PageState.Ready) touch(k)
        return state
    }

    // Decoded pages use a lot of memory (webtoon strips especially), so keep only the most
    // recently shown ones. Pages on screen are touched on every draw, so they're never dropped.
    private val recent = LinkedHashSet<String>()
    private val maxDecodedPages = 24

    private fun touch(k: String) {
        recent.remove(k)
        recent.add(k)
    }

    private fun evictOld() {
        while (recent.size > maxDecodedPages) {
            val oldest = recent.first()
            recent.remove(oldest)
            pageStates.remove(oldest)
        }
    }

    fun retry(chapterIndex: Int, pageIndex: Int) {
        pageStates.remove(key(chapterIndex, pageIndex))
        requestPage(chapterIndex, pageIndex)
    }

    private val requested = HashSet<String>()

    private fun requestPage(chapterIndex: Int, pageIndex: Int) {
        val k = key(chapterIndex, pageIndex)
        if (!requested.add(k)) return
        val page = loaded.firstOrNull { it.index == chapterIndex }?.pages?.getOrNull(pageIndex) ?: return
        scope.launch {
            val state = try {
                downloads.withPermit { PageState.Ready(PageLoader.load(source, page)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                PageState.Failed(e.message ?: e.toString())
            }
            pageStates[k] = state
            requested.remove(k)
            if (state is PageState.Ready) {
                touch(k)
                evictOld()
            }
        }
    }

    /** Keep the next few pages ready so turning a page is instant. */
    fun preload() {
        val chapter = currentChapter ?: return
        val first = currentPageIndex
        for (i in first until minOf(first + 5, chapter.pages.size)) {
            if (pageStates[key(chapter.index, i)] == null) requestPage(chapter.index, i)
        }
    }

    // ----- Paged navigation -----

    /** Screens for the current chapter: single pages, or pairs when spreads are on. */
    fun slots(): List<Slot> {
        val chapter = currentChapter ?: return emptyList()
        val count = chapter.pages.size
        if (!spread) return (0 until count).map { Slot(chapter.index, listOf(it)) }

        fun isWide(i: Int) = (pageStates[key(chapter.index, i)] as? PageState.Ready)?.page?.isWide == true
        val result = mutableListOf<Slot>()
        var i = 0
        if (shiftSpread && count > 0) {
            result += Slot(chapter.index, listOf(0)) // cover on its own
            i = 1
        }
        while (i < count) {
            if (isWide(i) || i + 1 >= count || isWide(i + 1)) {
                result += Slot(chapter.index, listOf(i))
                i += 1
            } else {
                result += Slot(chapter.index, listOf(i, i + 1))
                i += 2
            }
        }
        return result
    }

    fun nextPage() {
        val count = slots().size
        if (slotIndex + 1 < count) {
            slotIndex++
            preload()
        } else if (hasNextChapter) {
            openChapter(currentChapterIndex + 1)
        }
    }

    fun previousPage() {
        if (slotIndex > 0) {
            slotIndex--
        } else if (hasPreviousChapter) {
            openChapter(currentChapterIndex - 1, atEnd = true)
        }
    }

    fun goToSlot(index: Int) {
        slotIndex = index.coerceIn(0, (slots().size - 1).coerceAtLeast(0))
        preload()
    }

    // ----- Settings changes -----

    /** M key: Paged → Vertical → Webtoon → Paged. */
    fun cycleMode() {
        val all = ReadingMode.entries
        changeMode(all[(mode.ordinal + 1) % all.size])
    }

    fun changeMode(newMode: ReadingMode) {
        if (newMode == mode) return
        val page = currentPageIndex
        mode = newMode
        if (!dev.naved.j2kdesktop.Incognito.enabled) prefs.edit().putString("mode_$mangaKey", mode.name).apply()
        if (newMode.scrolls) {
            // Keep your place: the list jumps to the page you were on
            scrollPage = page
            scrollTarget = currentChapterIndex to page
        } else {
            // Scroll modes may have appended chapters; paged mode shows just the current one
            val current = currentChapter
            if (loaded.size > 1 && current != null) {
                loaded.clear()
                loaded += current
            }
            goToSlot(slots().indexOfFirst { page in it.pages }.coerceAtLeast(0))
        }
    }

    fun toggleDirection() {
        rightToLeft = !rightToLeft
        if (!dev.naved.j2kdesktop.Incognito.enabled) prefs.edit().putBoolean("rtl_$mangaKey", rightToLeft).apply()
    }

    fun toggleSpread() {
        val firstPage = slots().getOrNull(slotIndex)?.pages?.firstOrNull() ?: 0
        spread = !spread
        prefs.edit().putBoolean("spread", spread).apply()
        slotIndex = slots().indexOfFirst { firstPage in it.pages }.coerceAtLeast(0)
    }

    fun toggleShift() {
        shiftSpread = !shiftSpread
        if (!dev.naved.j2kdesktop.Incognito.enabled) prefs.edit().putBoolean("shift_$mangaKey", shiftSpread).apply()
    }

    fun changeWebtoonWidth(delta: Int) = updateWebtoonWidth(webtoonWidth + delta)

    fun updateWebtoonWidth(width: Int) {
        webtoonWidth = width.coerceIn(400, 1800)
        prefs.edit().putInt("webtoon_width", webtoonWidth).apply()
    }

    fun changeVerticalScale(delta: Float) = updateVerticalScale(verticalScale + delta)

    fun updateVerticalScale(scale: Float) {
        verticalScale = (Math.round(scale.coerceIn(1f, 3f) * 20) / 20f)
        prefs.edit().putFloat("vertical_scale", verticalScale).apply()
    }

    fun chooseRightToLeft(value: Boolean) {
        if (value != rightToLeft) toggleDirection()
    }

    fun setSpreadOn(value: Boolean) {
        if (value != spread) toggleSpread()
    }

    fun toggleShowPageNumber() {
        showPageNumber = !showPageNumber
        prefs.edit().putBoolean("show_page_number", showPageNumber).apply()
    }

    private fun loadMode(): ReadingMode {
        prefs.getString("mode_$mangaKey", null)?.let { saved ->
            return runCatching { ReadingMode.valueOf(saved) }.getOrDefault(ReadingMode.Paged)
        }
        // First time: long-strip series open as webtoon, everything else in your default mode
        val genre = manga.genre.orEmpty().lowercase()
        val longStrip = listOf("long strip", "webtoon", "web comic", "manhwa").any { it in genre }
        if (longStrip && dev.naved.j2kdesktop.AppSettings.autoWebtoon) return ReadingMode.Webtoon
        return runCatching { ReadingMode.valueOf(dev.naved.j2kdesktop.AppSettings.defaultReadingMode) }
            .getOrDefault(ReadingMode.Paged)
    }
}
