package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.naved.j2kdesktop.library.ReadProgress
import dev.naved.j2kdesktop.tracking.AniList
import dev.naved.j2kdesktop.tracking.MediaResult
import dev.naved.j2kdesktop.tracking.TrackEntry
import dev.naved.j2kdesktop.tracking.Tracking
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.launch

/** "AniList" button label for the manga page. */
fun trackingLabel(sourceId: Long, url: String): String {
    val entry = Tracking.get(sourceId, url) ?: return "AniList"
    val total = entry.totalChapters?.toString() ?: "?"
    return "AniList: ${Tracking.statusLabel(entry.status)} ${entry.progress}/$total"
}

@Composable
fun TrackingDialog(sourceId: Long, manga: SManga, chapters: List<SChapter>, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val linked = Tracking.get(sourceId, manga.url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tracking · AniList") },
        text = {
            Column(Modifier.width(560.dp)) {
                when {
                    !AniList.isLoggedIn -> Text(
                        "Log in to AniList first: More tab → Tracking.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    linked != null -> LinkedEntry(sourceId, manga, linked, busy, onBusy = { busy = it }, onError = { error = it })
                    else -> SearchAndLink(sourceId, manga, chapters, onBusy = { busy = it }, onError = { error = it })
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = if (linked != null) {
            {
                // Only forgets the link here; your AniList list entry stays
                TextButton(onClick = { Tracking.remove(sourceId, manga.url) }) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun LinkedEntry(
    sourceId: Long,
    manga: SManga,
    entry: TrackEntry,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onError: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember(entry) { mutableStateOf(entry.progress) }
    var status by remember(entry) { mutableStateOf(entry.status) }

    fun save() {
        val updated = entry.copy(progress = progress, status = status)
        onBusy(true)
        onError(null)
        scope.launch {
            try {
                AniList.save(updated.mediaId, updated.progress, updated.status)
                Tracking.set(sourceId, manga.url, updated)
            } catch (e: Exception) {
                onError(e.message ?: e.toString())
            } finally {
                onBusy(false)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(entry.coverUrl)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            entry.siteUrl?.let { url ->
                TextButton(onClick = { openUrl(url) }) { Text("Open on AniList") }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Status")
        Box {
            var open by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { open = true }) { Text(Tracking.statusLabel(status)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Tracking.statuses.forEach { s ->
                    DropdownMenuItem(text = { Text(Tracking.statusLabel(s)) }, onClick = {
                        status = s
                        open = false
                    })
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text("Chapters")
        TextButton(onClick = { if (progress > 0) progress-- }) { Text("−") }
        Text("$progress / ${entry.totalChapters ?: "?"}")
        TextButton(onClick = { progress++ }) { Text("+") }
        Spacer(Modifier.width(16.dp))
        OutlinedButton(onClick = { save() }, enabled = !busy) { Text("Save") }
    }
    Text(
        "Progress goes up by itself when you finish a chapter.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchAndLink(
    sourceId: Long,
    manga: SManga,
    chapters: List<SChapter>,
    onBusy: (Boolean) -> Unit,
    onError: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(runCatching { manga.title }.getOrDefault("")) }
    var results by remember { mutableStateOf<List<MediaResult>?>(null) }

    fun search() {
        onBusy(true)
        onError(null)
        scope.launch {
            try {
                results = AniList.search(query)
            } catch (e: Exception) {
                onError(e.message ?: e.toString())
            } finally {
                onBusy(false)
            }
        }
    }

    fun link(media: MediaResult) {
        onBusy(true)
        onError(null)
        scope.launch {
            try {
                // Keep what's on your AniList list; otherwise start from what you've read here
                val existing = AniList.entry(media.id)
                val readHere = chapters
                    .filter { ReadProgress.isRead(sourceId, manga.url, it.url) }
                    .maxOfOrNull { it.chapter_number.toInt() } ?: 0
                val progress = maxOf(existing?.first ?: 0, readHere)
                val status = existing?.second ?: "CURRENT"
                if (existing == null || progress != existing.first) AniList.save(media.id, progress, status)
                Tracking.set(
                    sourceId,
                    manga.url,
                    TrackEntry(media.id, media.title, media.chapters, progress, status, media.coverUrl, media.siteUrl),
                )
            } catch (e: Exception) {
                onError(e.message ?: e.toString())
            } finally {
                onBusy(false)
            }
        }
    }

    LaunchedEffect(Unit) { search() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search AniList") },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { search() }) { Text("Search") }
    }
    Spacer(Modifier.height(8.dp))
    val list = results
    if (list != null && list.isEmpty()) Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!list.isNullOrEmpty()) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(list, key = { it.id }) { media ->
                Row(
                    Modifier.fillMaxWidth().clickable { link(media) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(media.coverUrl)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(media.format, media.year?.toString(), media.chapters?.let { "$it chapters" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 46.dp, height = 66.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Opens a web page in the user's browser. */
fun openUrl(url: String) {
    runCatching { ProcessBuilder("xdg-open", url).start() }
        .recoverCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}
