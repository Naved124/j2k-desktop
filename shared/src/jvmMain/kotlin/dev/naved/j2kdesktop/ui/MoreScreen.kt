package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.naved.j2kdesktop.AppSettings
import dev.naved.j2kdesktop.browser.Browser
import dev.naved.j2kdesktop.browser.BrowserLocator
import dev.naved.j2kdesktop.browser.CookieStore
import dev.naved.j2kdesktop.compat.AndroidCompat
import dev.naved.j2kdesktop.compat.AppDirs
import dev.naved.j2kdesktop.download.DownloadManager
import dev.naved.j2kdesktop.library.LibraryUpdater
import java.io.File

/** More: downloads queue, library update and browser settings, where things are stored. */
@Composable
fun MoreTab() {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("More", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            SectionTitle("Downloads")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        DownloadManager.queue.isEmpty() -> "Nothing queued"
                        DownloadManager.isPaused -> "Paused · ${DownloadManager.queue.size} queued"
                        else -> "Downloading · ${DownloadManager.queue.size} queued"
                    },
                    modifier = Modifier.weight(1f),
                )
                if (DownloadManager.queue.isNotEmpty()) {
                    if (DownloadManager.isPaused) {
                        OutlinedButton(onClick = { DownloadManager.resume() }) { Text("Resume") }
                    } else {
                        OutlinedButton(onClick = { DownloadManager.pause() }) { Text("Pause") }
                    }
                    TextButton(onClick = { DownloadManager.clearQueue() }) { Text("Clear queue") }
                }
                TextButton(onClick = { openFolder(DownloadManager.root) }) { Text("Open folder") }
            }
            Text(
                DownloadManager.root.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(DownloadManager.queue, key = { it.key }) { task ->
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${runCatching { task.manga.title }.getOrDefault("")} · ${task.chapter.name}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        task.error != null -> Text("Failed: ${task.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        task.running && task.total > 0 -> LinearProgressIndicator(
                            progress = { task.done.toFloat() / task.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        task.running -> Text("Getting the page list…", style = MaterialTheme.typography.bodySmall)
                        else -> Text("Queued", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (task.error != null) {
                    TextButton(onClick = {
                        task.error = null
                        DownloadManager.resume()
                    }) { Text("Retry") }
                }
                TextButton(onClick = { DownloadManager.cancel(task) }) { Text("✕") }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Library")
            var updateOnStart by remember { mutableStateOf(AppSettings.updateOnStart) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check for new chapters when the app starts")
                    Text(
                        "Results show in Recents → Updates, and as unread badges in the library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = updateOnStart, onCheckedChange = {
                    updateOnStart = it
                    AppSettings.updateOnStart = it
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { LibraryUpdater.start() }, enabled = !LibraryUpdater.isRunning) { Text("⟳  Update library now") }
                LibraryUpdater.status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            Spacer(Modifier.height(16.dp))
            AppearanceAndReaderSettings()

            Spacer(Modifier.height(16.dp))
            BackupSection()

            Spacer(Modifier.height(16.dp))
            TrackingSection()

            Spacer(Modifier.height(16.dp))
            SectionTitle("Browser (Cloudflare and WebView sources)")
            Text("Using: ${BrowserLocator.executable ?: "none found. Install Chromium, Chrome, Brave or Edge"}")
            Text(
                "User-Agent: ${Browser.userAgent}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var cleared by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    CookieStore.removeAll { true }
                    Browser.clearCookies()
                    cleared = true
                }) { Text("Clear cookies") }
                Spacer(Modifier.width(12.dp))
                if (cleared) Text("Cleared. Sites will run their checks again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Storage")
            var cacheCleared by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    ImageLoaderHolder.clearCache()
                    cacheCleared = true
                }) { Text("Clear cover cache") }
                Spacer(Modifier.width(12.dp))
                if (cacheCleared) Text("Cleared", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App data (library, progress, extensions): ${AppDirs.data.path}", modifier = Modifier.weight(1f))
                TextButton(onClick = { openFolder(AppDirs.data) }) { Text("Open") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Local manga: ${File(System.getProperty("user.home"), "Documents/J2KDesktop/local").path}", modifier = Modifier.weight(1f))
                TextButton(onClick = { openFolder(File(System.getProperty("user.home"), "Documents/J2KDesktop/local")) }) { Text("Open") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

private fun openFolder(dir: File) {
    dir.mkdirs()
    runCatching { ProcessBuilder("xdg-open", dir.absolutePath).start() }
        .recoverCatching { java.awt.Desktop.getDesktop().open(dir) }
}
