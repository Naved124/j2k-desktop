package dev.naved.j2kdesktop.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt

private val BlankCursor: PointerIcon by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Point(0, 0),
            "blank",
        ),
    )
}

private val BarColor = Color(0xE6101010)

/** Arrow-key hold scrolling state (vertical / webtoon). */
private class ArrowHold {
    var job: kotlinx.coroutines.Job? = null
    var held = false
    var down = true
}

/** Swallows clicks so tapping a bar's empty space doesn't also reach the reader underneath. */
private fun Modifier.eatTaps() = pointerInput(Unit) { detectTapGestures { } }

/**
 * Full-window reader. Touchpad first: one page per swipe in paged mode, smooth scrolling in
 * vertical/webtoon, a click shows the bars and scrolling hides them again.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReaderScreen(request: ReaderRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(request) { ReaderModel(request, scope) }
    LaunchedEffect(model) { model.start() }
    // Remember the page (and mark chapters read) as you go
    LaunchedEffect(model) {
        snapshotFlow { Triple(model.currentChapterIndex, model.currentPageIndex, model.loaded.size) }
            .collect { (_, _, loadedCount) -> if (loadedCount > 0) model.saveProgress() }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val listState = rememberLazyListState()
    val turner = remember { SwipeTurner() }
    val arrowHold = remember { ArrowHold() }

    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(model.slotIndex, model.currentChapterIndex) {
        zoom = 1f
        pan = Offset.Zero
    }

    // Bars: a click shows them, reading on (scroll, swipe, turn) hides them
    var barsVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // Fullscreen: moving the mouse to the top edge slides down an exit bar, like a browser
    var edgeBar by remember { mutableStateOf(false) }
    LaunchedEffect(ReaderLauncher.isFullscreen) { if (!ReaderLauncher.isFullscreen) edgeBar = false }
    val uiShown = barsVisible || settingsOpen || edgeBar
    // Keys always go to the reader: take focus back after bars, settings or fullscreen change
    LaunchedEffect(barsVisible, settingsOpen, edgeBar, ReaderLauncher.isFullscreen, model.mode) {
        if (!settingsOpen) runCatching { focus.requestFocus() }
    }

    // Hide the cursor after 2 s without movement
    var lastMove by remember { mutableStateOf(System.currentTimeMillis()) }
    var cursorHidden by remember { mutableStateOf(false) }
    LaunchedEffect(lastMove) {
        cursorHidden = false
        delay(2_000)
        cursorHidden = true
    }

    fun next() {
        barsVisible = false
        model.nextPage()
    }

    fun previous() {
        barsVisible = false
        model.previousPage()
    }

    fun scrollBy(fraction: Float) {
        barsVisible = false
        scope.launch { listState.animateScrollBy(listState.layoutInfo.viewportSize.height * fraction) }
    }

    /**
     * Vertical/webtoon ↓ / ↑: a tap scrolls a step; holding the key keeps scrolling smoothly
     * until it's released (key repeats are ignored, so it doesn't stutter).
     */
    fun holdScroll(down: Boolean) {
        arrowHold.held = true
        if (arrowHold.job?.isActive == true && arrowHold.down == down) return
        arrowHold.job?.cancel()
        arrowHold.down = down
        barsVisible = false
        arrowHold.job = scope.launch {
            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
            val sign = if (down) 1f else -1f
            listState.animateScrollBy(sign * viewport * 0.35f)
            if (!arrowHold.held) return@launch
            listState.scroll {
                var last = withFrameNanos { it }
                while (arrowHold.held) {
                    val now = withFrameNanos { it }
                    // this. = the list's ScrollScope (not the reader's own scrollBy above)
                    this.scrollBy(sign * viewport * 1.6f * ((now - last) / 1_000_000_000f))
                    last = now
                }
            }
        }
    }

    /** Vertical mode keys: jump a whole page, skipping chapter dividers. */
    fun stepPage(forward: Boolean) {
        barsVisible = false
        scope.launch {
            val info = listState.layoutInfo
            val current = probeItem(listState)?.index ?: return@launch
            var target = if (forward) current + 1 else current - 1
            val keyAt = { i: Int -> info.visibleItemsInfo.firstOrNull { it.index == i }?.key as? String }
            if (keyAt(target)?.endsWith("-end") == true) target += if (forward) 1 else -1
            listState.animateScrollToItem(target.coerceIn(0, (info.totalItemsCount - 1).coerceAtLeast(0)))
        }
    }

    fun onKey(e: KeyEvent): Boolean {
        val upOrDown = e.key == Key.DirectionDown || e.key == Key.DirectionUp
        if (e.type == KeyEventType.KeyUp && upOrDown && model.mode.scrolls) {
            arrowHold.held = false
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        val forwardArrow = if (model.rightToLeft) Key.DirectionLeft else Key.DirectionRight
        val backArrow = if (model.rightToLeft) Key.DirectionRight else Key.DirectionLeft
        when (e.key) {
            Key.Escape -> when {
                settingsOpen -> settingsOpen = false
                barsVisible -> barsVisible = false
                ReaderLauncher.isFullscreen -> ReaderLauncher.isFullscreen = false
                else -> onClose()
            }
            Key.F, Key.F11 -> ReaderLauncher.isFullscreen = !ReaderLauncher.isFullscreen
            Key.M -> model.cycleMode()
            Key.S -> settingsOpen = !settingsOpen
            Key.D -> if (model.mode == ReadingMode.Paged) model.toggleSpread()
            Key.N -> model.openChapter(model.currentChapterIndex + 1)
            Key.P -> model.openChapter(model.currentChapterIndex - 1)
            Key.Zero -> {
                zoom = 1f
                pan = Offset.Zero
            }
            else -> return when (model.mode) {
                ReadingMode.Paged -> {
                    when (e.key) {
                        forwardArrow, Key.PageDown, Key.DirectionDown -> next()
                        backArrow, Key.PageUp, Key.DirectionUp -> previous()
                        Key.Spacebar -> if (e.isShiftPressed) previous() else next()
                        Key.MoveHome -> model.goToSlot(0)
                        Key.MoveEnd -> model.goToSlot(Int.MAX_VALUE)
                        Key.Equals, Key.Plus, Key.NumPadAdd -> zoom = (zoom * 1.25f).coerceAtMost(5f)
                        Key.Minus, Key.NumPadSubtract -> zoom = (zoom / 1.25f).coerceAtLeast(1f)
                        else -> return false
                    }
                    true
                }
                ReadingMode.Vertical -> {
                    when (e.key) {
                        Key.PageDown, forwardArrow -> stepPage(true)
                        Key.PageUp, backArrow -> stepPage(false)
                        Key.Spacebar -> scrollBy(if (e.isShiftPressed) -0.9f else 0.9f)
                        Key.DirectionDown -> holdScroll(true)
                        Key.DirectionUp -> holdScroll(false)
                        Key.MoveHome -> model.goToPage(0)
                        Key.MoveEnd -> model.goToPage(Int.MAX_VALUE)
                        Key.Equals, Key.Plus, Key.NumPadAdd -> model.changeVerticalScale(0.1f)
                        Key.Minus, Key.NumPadSubtract -> model.changeVerticalScale(-0.1f)
                        else -> return false
                    }
                    true
                }
                ReadingMode.Webtoon -> {
                    when (e.key) {
                        Key.Spacebar -> scrollBy(if (e.isShiftPressed) -0.9f else 0.9f)
                        Key.PageDown -> scrollBy(0.9f)
                        Key.PageUp -> scrollBy(-0.9f)
                        Key.DirectionDown -> holdScroll(true)
                        Key.DirectionUp -> holdScroll(false)
                        // Left / right: a screen at a time
                        Key.DirectionRight -> scrollBy(0.9f)
                        Key.DirectionLeft -> scrollBy(-0.9f)
                        Key.MoveHome -> model.goToPage(0)
                        Key.MoveEnd -> model.goToPage(Int.MAX_VALUE)
                        Key.Equals, Key.Plus, Key.NumPadAdd -> model.changeWebtoonWidth(100)
                        Key.Minus, Key.NumPadSubtract -> model.changeWebtoonWidth(-100)
                        else -> return false
                    }
                    true
                }
            }
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent(::onKey)
            .pointerHoverIcon(if (cursorHidden && !uiShown) BlankCursor else PointerIcon.Default)
            .onPointerEvent(PointerEventType.Move) { event ->
                lastMove = System.currentTimeMillis()
                val y = event.changes.firstOrNull()?.position?.y ?: return@onPointerEvent
                if (ReaderLauncher.isFullscreen && !barsVisible) {
                    if (y <= 3f) {
                        edgeBar = true
                    } else if (edgeBar && y > 90.dp.toPx()) {
                        edgeBar = false
                    }
                }
            },
    ) {
        // The reading area. Its own box, so scrolling over the settings sheet doesn't turn pages.
        Box(
            Modifier
                .fillMaxSize()
                // Initial pass: we see scrolls before the list does, so Ctrl+scroll can resize instead
                .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
                    val delta = event.changes.first().scrollDelta
                    if (event.keyboardModifiers.isCtrlPressed) {
                        val bigger = delta.y < 0
                        when (model.mode) {
                            ReadingMode.Paged -> {
                                zoom = (if (bigger) zoom * 1.1f else zoom / 1.1f).coerceIn(1f, 5f)
                                if (zoom == 1f) pan = Offset.Zero
                            }
                            ReadingMode.Vertical -> model.changeVerticalScale(if (bigger) 0.05f else -0.05f)
                            ReadingMode.Webtoon -> model.changeWebtoonWidth(if (bigger) 40 else -40)
                        }
                        event.changes.forEach { it.consume() }
                        return@onPointerEvent
                    }
                    barsVisible = false
                    if (model.mode == ReadingMode.Paged && zoom <= 1.01f) {
                        // Horizontal swipe: "forward" follows reading direction. Vertical: down = forward.
                        val horizontal = abs(delta.x) > abs(delta.y)
                        val forward = if (horizontal) {
                            if (model.rightToLeft) -delta.x else delta.x
                        } else {
                            delta.y
                        }
                        when (turner.onScroll(forward)) {
                            1 -> model.nextPage()
                            -1 -> model.previousPage()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
                .pointerInput(model.mode, model.rightToLeft) {
                    detectTapGestures { pos ->
                        runCatching { focus.requestFocus() }
                        if (settingsOpen) {
                            settingsOpen = false
                            return@detectTapGestures
                        }
                        val third = size.width / 3f
                        if (model.mode.scrolls || pos.x in third..(2 * third)) {
                            barsVisible = !barsVisible
                        } else {
                            // Tap zones follow the reading direction, like J2K
                            val leftSide = pos.x < third
                            val goForward = if (model.rightToLeft) leftSide else !leftSide
                            if (goForward) next() else previous()
                        }
                    }
                }
                .pointerInput(model.mode) {
                    if (model.mode == ReadingMode.Paged) {
                        detectDragGestures { change, drag ->
                            if (zoom > 1f) {
                                change.consume()
                                pan += drag
                            }
                        }
                    }
                },
        ) {
            when {
                model.loaded.isEmpty() -> ChapterStatus(model, onClose)
                model.mode == ReadingMode.Paged -> PagedView(model, zoom, pan)
                else -> ScrollView(model, listState)
            }
        }

        // Small page number, J2K style, while the bars are hidden
        if (!uiShown && model.showPageNumber && model.loaded.isNotEmpty()) {
            Text(
                model.pageLabel(),
                color = Color.White.copy(alpha = 0.85f),
                style = TextStyle(fontSize = 12.sp, shadow = Shadow(Color.Black, blurRadius = 6f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }

        if (barsVisible) {
            TopBar(model, onClose, Modifier.align(Alignment.TopCenter))
            StatusBar(model, onSettings = { settingsOpen = !settingsOpen }, Modifier.align(Alignment.BottomCenter))
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = edgeBar && !barsVisible,
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            FullscreenEdgeBar(
                model,
                onExit = {
                    edgeBar = false
                    ReaderLauncher.isFullscreen = false
                },
                onClose = onClose,
            )
        }

        if (settingsOpen) {
            SettingsSheet(model, onClose = { settingsOpen = false }, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun ChapterStatus(model: ReaderModel, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val error = model.chapterError
        if (error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't open this chapter: $error", color = MaterialTheme.colorScheme.error)
                Row {
                    TextButton(onClick = { model.openChapter(model.currentChapterIndex) }) { Text("Retry") }
                    TextButton(onClick = onClose) { Text("Back") }
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

// ---------------- Paged ----------------

@Composable
private fun PagedView(model: ReaderModel, zoom: Float, pan: Offset) {
    val slot = model.slots().getOrNull(model.slotIndex) ?: return
    // In right-to-left spreads the first page sits on the right
    val order = if (model.rightToLeft) slot.pages.reversed() else slot.pages
    Row(
        Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEach { pageIndex ->
            Box(Modifier.fillMaxHeight().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                PagedPage(model, slot.chapterIndex, pageIndex)
            }
        }
    }
}

@Composable
private fun PagedPage(model: ReaderModel, chapterIndex: Int, pageIndex: Int) {
    when (val state = model.pageState(chapterIndex, pageIndex)) {
        PageState.Loading -> Box(Modifier.fillMaxHeight().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is PageState.Failed -> PageError(state.message) { model.retry(chapterIndex, pageIndex) }
        is PageState.Ready -> {
            val page = state.page
            if (page.chunks.size == 1) {
                Image(
                    bitmap = page.chunks.first(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight().aspectRatio(page.aspect, matchHeightConstraintsFirst = true),
                )
            } else {
                // A very tall image in paged mode: let it scroll
                Column(Modifier.width(800.dp).verticalScroll(rememberScrollState())) {
                    page.chunks.forEach { chunk ->
                        Image(
                            bitmap = chunk,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(chunk.width.toFloat() / chunk.height),
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Vertical + Webtoon ----------------

/** The list item a third of the way down the screen: that's "the page you're reading". */
private fun probeItem(listState: LazyListState) = listState.layoutInfo.let { info ->
    val probe = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 3
    info.visibleItemsInfo.firstOrNull { it.offset <= probe && it.offset + it.size > probe }
        ?: info.visibleItemsInfo.firstOrNull()
}

@Composable
private fun ScrollView(model: ReaderModel, listState: LazyListState) {
    // Jump when a chapter is opened, the slider moves, or the mode changes
    LaunchedEffect(model.scrollTarget, model.loaded.size) {
        val target = model.scrollTarget ?: return@LaunchedEffect
        val index = model.itemIndexOf(target.first, target.second) ?: return@LaunchedEffect
        listState.scrollToItem(index)
        model.scrollTarget = null
    }

    // Append the next chapter near the end; keep the chapter/page number in sync with the screen
    LaunchedEffect(model, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(last >= info.totalItemsCount - 4, probeItem(listState)?.key as? String)
        }.collect { (nearEnd, key) ->
            if (nearEnd && model.scrollTarget == null) model.appendNextChapter()
            if (key != null && model.scrollTarget == null) {
                val chapterIndex = key.substringBefore('-').toIntOrNull() ?: return@collect
                val chapter = model.loaded.firstOrNull { it.index == chapterIndex } ?: return@collect
                val page = key.substringAfter('-').toIntOrNull() ?: (chapter.pages.size - 1)
                model.markVisible(chapterIndex, page)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val vertical = model.mode == ReadingMode.Vertical
        val pageHeight = maxHeight * model.verticalScale
        val listModifier = if (vertical) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxHeight().width(model.webtoonWidth.dp)
        }
        LazyColumn(state = listState, modifier = listModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            model.loaded.forEach { chapter ->
                items(chapter.pages.size, key = { "${chapter.index}-$it" }) { i ->
                    if (vertical) {
                        VerticalPage(model, chapter.index, i, pageHeight)
                    } else {
                        WebtoonPage(model, chapter.index, i)
                    }
                }
                item(key = "${chapter.index}-end") {
                    ChapterDivider(model, chapter)
                }
            }
        }
    }
}

/** Vertical mode: each page fits the window height (times the size setting), with a small gap. */
@Composable
private fun VerticalPage(model: ReaderModel, chapterIndex: Int, pageIndex: Int, height: androidx.compose.ui.unit.Dp) {
    val box = Modifier.fillMaxWidth().height(height).padding(bottom = 6.dp)
    when (val state = model.pageState(chapterIndex, pageIndex)) {
        PageState.Loading -> Box(box, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is PageState.Failed -> Box(box, contentAlignment = Alignment.Center) {
            PageError(state.message) { model.retry(chapterIndex, pageIndex) }
        }
        is PageState.Ready -> {
            val page = state.page
            if (page.chunks.size == 1) {
                Image(
                    bitmap = page.chunks.first(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = box,
                )
            } else {
                // A long strip image: show it at strip width instead of squeezing it into one screen
                Column(Modifier.width(model.webtoonWidth.dp).padding(bottom = 6.dp)) {
                    page.chunks.forEach { chunk ->
                        Image(
                            bitmap = chunk,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(chunk.width.toFloat() / chunk.height),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebtoonPage(model: ReaderModel, chapterIndex: Int, pageIndex: Int) {
    when (val state = model.pageState(chapterIndex, pageIndex)) {
        PageState.Loading -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is PageState.Failed -> Box(Modifier.fillMaxWidth().aspectRatio(1.4f), contentAlignment = Alignment.Center) {
            PageError(state.message) { model.retry(chapterIndex, pageIndex) }
        }
        is PageState.Ready -> Column {
            state.page.chunks.forEach { chunk ->
                Image(
                    bitmap = chunk,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().aspectRatio(chunk.width.toFloat() / chunk.height),
                )
            }
        }
    }
}

@Composable
private fun ChapterDivider(model: ReaderModel, chapter: LoadedChapter) {
    val next = model.chapters.getOrNull(chapter.index + 1)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Finished: ${chapter.chapter.name}", color = Color.White)
        Spacer(Modifier.height(8.dp))
        when {
            next == null -> Text("No more chapters", color = Color.Gray)
            model.isLoadingChapter -> CircularProgressIndicator()
            model.chapterError != null -> TextButton(onClick = { model.appendNextChapter() }) {
                Text("Couldn't load the next chapter. Retry")
            }
            else -> Text("Next: ${next.name}", color = Color.Gray)
        }
    }
}

@Composable
private fun PageError(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text("Couldn't load this page", color = Color.White)
        Text(message, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

// ---------------- Bars ----------------

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color.White else Color.DarkGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TopBar(model: ReaderModel, onClose: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(BarColor).eatTaps().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BarIcon(ReaderIcons.Back, "Back (Esc)", onClick = onClose)
        Column(Modifier.weight(1f)) {
            Text(model.manga.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(
                model.chapters.getOrNull(model.currentChapterIndex)?.name.orEmpty(),
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BarIcon(
            if (ReaderLauncher.isFullscreen) ReaderIcons.FullscreenExit else ReaderIcons.Fullscreen,
            "Fullscreen (F)",
        ) { ReaderLauncher.isFullscreen = !ReaderLauncher.isFullscreen }
    }
}

/** Fullscreen's exit bar: slides down when the mouse reaches the top edge of the screen. */
@Composable
private fun FullscreenEdgeBar(model: ReaderModel, onExit: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BarColor)
            .eatTaps()
            .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            listOfNotNull(
                runCatching { model.manga.title }.getOrNull(),
                model.chapters.getOrNull(model.currentChapterIndex)?.name,
            ).joinToString("  ·  "),
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onExit) {
            Icon(ReaderIcons.FullscreenExit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Exit full screen (F)", color = Color.White, fontSize = 13.sp)
        }
        ReaderLauncher.minimizeWindow?.let { minimize -> BarIcon(ReaderIcons.Minimize, "Minimize", onClick = minimize) }
        BarIcon(ReaderIcons.Close, "Close reader (Esc)", onClick = onClose)
    }
}

/** The lean bottom bar: chapter buttons, page number, slider, settings. */
@Composable
private fun StatusBar(model: ReaderModel, onSettings: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(BarColor).eatTaps().padding(horizontal = 8.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BarIcon(ReaderIcons.SkipPrevious, "Previous chapter (P)", enabled = model.hasPreviousChapter) {
            model.openChapter(model.currentChapterIndex - 1)
        }
        Text(
            model.chapters.getOrNull(model.currentChapterIndex)?.name.orEmpty(),
            color = Color.Gray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Text(model.pageLabel(), color = Color.White, fontSize = 13.sp, modifier = Modifier.widthIn(min = 64.dp))

        val count = model.pageCount
        if (count > 1) {
            Slider(
                value = model.currentPageIndex.coerceIn(0, count - 1).toFloat(),
                onValueChange = { model.goToPage(it.roundToInt()) },
                valueRange = 0f..(count - 1).toFloat(),
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        BarIcon(ReaderIcons.SkipNext, "Next chapter (N)", enabled = model.hasNextChapter) {
            model.openChapter(model.currentChapterIndex + 1)
        }
        BarIcon(ReaderIcons.Settings, "Reader settings (S)", onClick = onSettings)
    }
}

// ---------------- Settings ----------------

@Composable
private fun SettingsSheet(model: ReaderModel, onClose: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight().width(340.dp).eatTaps(),
        color = Color(0xF21A1A1A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reader settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                BarIcon(ReaderIcons.Close, "Close", onClick = onClose)
            }

            Section("Reading mode (M)")
            ReadingMode.entries.forEach { mode ->
                Choice(mode.label, mode.hint, selected = model.mode == mode) { model.changeMode(mode) }
            }

            when (model.mode) {
                ReadingMode.Paged -> {
                    Section("Page layout (D)")
                    Choice("Single page", selected = !model.spread) { model.setSpreadOn(false) }
                    Choice("Two-page spread", "Wide pages still show on their own", selected = model.spread) {
                        model.setSpreadOn(true)
                    }
                    if (model.spread) {
                        Toggle("Shift by one", "Show the first page alone so spreads line up", model.shiftSpread) {
                            model.toggleShift()
                        }
                    }
                    Section("Direction")
                    Choice("Right to left", "Manga", selected = model.rightToLeft) { model.chooseRightToLeft(true) }
                    Choice("Left to right", "Comics, most manhwa", selected = !model.rightToLeft) {
                        model.chooseRightToLeft(false)
                    }
                }
                ReadingMode.Vertical -> {
                    Section("Page size (Ctrl+scroll or +/-)")
                    Text("${(model.verticalScale * 100).roundToInt()}% of the window height", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = model.verticalScale,
                        onValueChange = { model.updateVerticalScale(it) },
                        valueRange = 1f..3f,
                    )
                }
                ReadingMode.Webtoon -> {
                    Section("Strip width (Ctrl+scroll or +/-)")
                    Text("${model.webtoonWidth} px", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = model.webtoonWidth.toFloat(),
                        onValueChange = { model.updateWebtoonWidth((it / 20).roundToInt() * 20) },
                        valueRange = 400f..1800f,
                    )
                }
            }

            Section("Display")
            Toggle("Page number", "Small counter at the bottom while reading", model.showPageNumber) {
                model.toggleShowPageNumber()
            }
            Toggle("Fullscreen (F)", null, ReaderLauncher.isFullscreen) {
                ReaderLauncher.isFullscreen = !ReaderLauncher.isFullscreen
            }

            Section("Shortcuts")
            Text(
                "Click: show or hide bars · Swipe/scroll: read · Esc: back\n" +
                    "N / P: next / previous chapter · Home / End: first / last page\n" +
                    "Arrows: turn / scroll (hold ↓ ↑ to keep scrolling) · M: mode · D: spread · S: settings · F: fullscreen · 0: reset zoom",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    Spacer(Modifier.height(10.dp))
    Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Choice(title: String, hint: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontSize = 14.sp)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
