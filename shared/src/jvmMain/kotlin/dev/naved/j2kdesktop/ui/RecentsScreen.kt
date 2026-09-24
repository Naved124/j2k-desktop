package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.naved.j2kdesktop.library.LibraryUpdater
import dev.naved.j2kdesktop.library.ReadProgress
import dev.naved.j2kdesktop.library.Updates
import dev.naved.j2kdesktop.reader.ReaderOpener
import dev.naved.j2kdesktop.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Recents: reading history and new chapters from library updates. */
@Composable
fun RecentsTab() {
    var open by remember { mutableStateOf<Pair<Long, SManga>?>(null) }
    var showUpdates by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val historyState = androidx.compose.foundation.lazy.rememberLazyListState()
    val updatesState = androidx.compose.foundation.lazy.rememberLazyListState()

    open?.let { (sourceId, manga) ->
        val source = SourceManager.get(sourceId)
        if (source == null) {
            Column {
                TextButton(onClick = { open = null }) { Text("← Back") }
                Text("This manga's extension isn't installed (or is still loading).", color = MaterialTheme.colorScheme.error)
            }
        } else {
            MangaScreen(source = source, manga = manga, onBack = { open = null })
        }
        return
    }

    /** Opens the reader directly (the chapter, or where you left off). */
    fun readNow(key: String, sourceId: Long, manga: SManga, chapterUrl: String?) {
        val source = SourceManager.get(sourceId) ?: run {
            error = "The extension for ${manga.title} isn't installed"
            return
        }
        scope.launch {
            opening = key
            error = null
            try {
                ReaderOpener.open(source, manga, chapterUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = "Couldn't open ${manga.title}: ${e.message ?: e}"
            } finally {
                opening = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recents", style = MaterialTheme.typography.headlineMedium)
            FilterChip(selected = !showUpdates, onClick = { showUpdates = false }, label = { Text("History") })
            FilterChip(selected = showUpdates, onClick = { showUpdates = true }, label = { Text("Updates") })
            Spacer(Modifier.weight(1f))
            if (showUpdates) {
                if (LibraryUpdater.isRunning) {
                    Text("Checking ${LibraryUpdater.done}/${LibraryUpdater.total}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedButton(onClick = { LibraryUpdater.start() }) { Text("⟳  Check for new chapters") }
                }
                TextButton(onClick = { Updates.clear() }) { Text("Clear") }
            } else {
                TextButton(onClick = { ReadProgress.clearHistory() }) { Text("Clear history") }
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (showUpdates) LibraryUpdater.status?.takeIf { !LibraryUpdater.isRunning }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))

        if (!showUpdates) {
            val history = ReadProgress.history()
            if (history.isEmpty()) {
                Text("Nothing read yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Box(Modifier.fillMaxSize()) {
            LazyColumn(state = historyState, modifier = Modifier.fillMaxSize().padding(end = ScrollbarGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { "${it.sourceId}|${it.url}" }) { item ->
                    val key = "h|${item.sourceId}|${item.url}"
                    val chapterState = item.lastChapterUrl?.let { item.chapters[it] }
                    val detail = buildString {
                        append(item.lastChapterName ?: "")
                        if (chapterState != null && !chapterState.read && chapterState.pageCount > 0) {
                            append(" · page ${chapterState.lastPage + 1}/${chapterState.pageCount}")
                        }
                        append(" · ")
                        append(timeAgo(item.lastReadAt))
                    }
                    RecentRow(
                        sourceId = item.sourceId,
                        thumbnailUrl = item.thumbnailUrl,
                        title = item.title,
                        detail = detail,
                        dimmed = false,
                        busy = opening == key,
                        actionLabel = "Resume",
                        onOpen = { open = item.sourceId to item.toSManga() },
                        onAction = { readNow(key, item.sourceId, item.toSManga(), null) },
                        onRemove = { ReadProgress.removeFromHistory(item.sourceId, item.url) },
                    )
                }
            }
            ListScrollbar(historyState)
            }
        } else {
            val updates = Updates.list
            if (updates.isEmpty()) {
                Text(
                    "No new chapters yet. Library updates (⟳) list new chapters here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val byDay = updates.groupBy { Instant.ofEpochMilli(it.foundAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            Box(Modifier.fillMaxSize()) {
            LazyColumn(state = updatesState, modifier = Modifier.fillMaxSize().padding(end = ScrollbarGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                byDay.forEach { (day, entries) ->
                    item(key = "day-$day") {
                        Text(
                            dayLabel(day),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(entries, key = { "u|${it.sourceId}|${it.mangaUrl}|${it.chapterUrl}|${it.foundAt}" }) { u ->
                        val key = "u|${u.sourceId}|${u.chapterUrl}"
                        val manga = SManga.create().also {
                            it.url = u.mangaUrl
                            it.title = u.mangaTitle
                            it.thumbnail_url = u.thumbnailUrl
                        }
                        RecentRow(
                            sourceId = u.sourceId,
                            thumbnailUrl = u.thumbnailUrl,
                            title = u.mangaTitle,
                            detail = u.chapterName,
                            dimmed = ReadProgress.isRead(u.sourceId, u.mangaUrl, u.chapterUrl),
                            busy = opening == key,
                            actionLabel = "Read",
                            onOpen = { open = u.sourceId to manga },
                            onAction = { readNow(key, u.sourceId, manga, u.chapterUrl) },
                            onRemove = null,
                        )
                    }
                }
            }
            ListScrollbar(updatesState)
            }
        }
    }
}

@Composable
private fun RecentRow(
    sourceId: Long,
    thumbnailUrl: String?,
    title: String,
    detail: String,
    dimmed: Boolean,
    busy: Boolean,
    actionLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).alpha(if (dimmed) 0.5f else 1f).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = rememberImageRequest(thumbnailUrl, (SourceManager.get(sourceId) as? HttpSource)?.headers),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 74.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
        onRemove?.let { TextButton(onClick = it) { Text("✕") } }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMM")

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dayFormat)
    }
}

internal fun timeAgo(ms: Long): String {
    val minutes = (System.currentTimeMillis() - ms) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} d ago"
        else -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
