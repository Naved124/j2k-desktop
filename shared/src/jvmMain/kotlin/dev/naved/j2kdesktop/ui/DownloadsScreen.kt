package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.naved.j2kdesktop.download.DownloadManager
import dev.naved.j2kdesktop.download.DownloadTask
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Downloads: every queued chapter in the order it will download.
 * The queue is first-come, first-served (the first chapter you clicked goes first);
 * the arrows let you change the order.
 */
@Composable
fun DownloadsTab() {
    val queue = DownloadManager.queue
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                Text(
                    statusLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (queue.isNotEmpty()) {
                if (DownloadManager.isPaused) {
                    OutlinedButton(onClick = { DownloadManager.resume() }) { Text("▶  Resume") }
                } else {
                    OutlinedButton(onClick = { DownloadManager.pause() }) { Text("❚❚  Pause") }
                }
                if (queue.any { it.error != null }) {
                    TextButton(onClick = { DownloadManager.retryFailed() }) { Text("Retry failed") }
                }
                TextButton(onClick = { DownloadManager.clearQueue() }) { Text("Clear queue") }
            }
            TextButton(onClick = { openFolder(DownloadManager.root) }) { Text("Open folder") }
        }
        Spacer(Modifier.height(16.dp))

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("↓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing downloading", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Queue chapters from a manga's page (↓ Download, or the ↓ on a chapter).\n" +
                            "They download one at a time, in the order you clicked them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        DownloadManager.root.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = ScrollbarGutter),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(queue, key = { _, task -> task.key }) { index, task ->
                    DownloadRow(task, index, queue.size)
                }
            }
            ListScrollbar(listState)
        }
    }
}

private fun statusLine(): String {
    val queue = DownloadManager.queue
    if (queue.isEmpty()) return "Downloaded chapters are saved for offline reading"
    val failed = queue.count { it.error != null }
    val waiting = queue.size - failed
    val parts = mutableListOf<String>()
    parts += when {
        DownloadManager.isPaused -> "Paused"
        waiting == 0 -> "Stopped"
        else -> "Downloading"
    }
    parts += "${queue.size} chapter${if (queue.size == 1) "" else "s"} in queue"
    if (failed > 0) parts += "$failed failed"
    return parts.joinToString(" · ")
}

@Composable
private fun DownloadRow(task: DownloadTask, index: Int, count: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (task.running) colors.secondaryContainer else colors.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Place in line
        Text(
            "${index + 1}",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (task.running) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        AsyncImage(
            model = rememberImageRequest(task.manga.thumbnail_url, (task.source as? HttpSource)?.headers),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 44.dp, height = 62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                runCatching { task.manga.title }.getOrDefault(""),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                task.chapter.name + "  ·  " + task.source.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            when {
                task.error != null -> Text(
                    "Failed: ${task.error}",
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                task.running && task.total > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { task.done.toFloat() / task.total },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("${task.done} / ${task.total}", style = MaterialTheme.typography.bodySmall)
                }
                task.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("Getting pages…", style = MaterialTheme.typography.bodySmall)
                }
                DownloadManager.isPaused -> Text("Paused", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                else -> Text("Waiting", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (task.error != null) {
            TextButton(onClick = {
                task.error = null
                DownloadManager.resume()
            }) { Text("Retry") }
        }
        // Reordering
        TextButton(onClick = { DownloadManager.moveToTop(task) }, enabled = index > 0) { Text("⤒") }
        TextButton(onClick = { DownloadManager.moveUp(task) }, enabled = index > 0) { Text("↑") }
        TextButton(onClick = { DownloadManager.moveDown(task) }, enabled = index < count - 1) { Text("↓") }
        TextButton(onClick = { DownloadManager.moveToBottom(task) }, enabled = index < count - 1) { Text("⤓") }
        TextButton(onClick = { DownloadManager.cancel(task) }) { Text("✕") }
    }
}
