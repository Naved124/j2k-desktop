package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.naved.j2kdesktop.download.DownloadManager
import dev.naved.j2kdesktop.library.Categories
import dev.naved.j2kdesktop.library.ChapterCache
import dev.naved.j2kdesktop.library.ChapterFilters
import dev.naved.j2kdesktop.library.Library
import dev.naved.j2kdesktop.library.ReadProgress
import dev.naved.j2kdesktop.reader.ReaderLauncher
import dev.naved.j2kdesktop.reader.ReaderRequest
import dev.naved.j2kdesktop.source.MangaCalls
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaScreen(source: Source, manga: SManga, onBack: () -> Unit) {
    var details by remember(manga.url) { mutableStateOf(manga) }
    var chapters by remember(manga.url) { mutableStateOf<List<SChapter>?>(null) }
    var error by remember(manga.url) { mutableStateOf<String?>(null) }

    LaunchedEffect(manga.url) {
        // Library manga: show the saved chapter list right away while the fresh one loads
        ChapterCache.get(source.id, manga.url)?.let { cached -> chapters = cached.map { it.toSChapter() } }

        // Extensions often return details without url (or even title) set, so merge into our copy
        fun merge(fetched: SManga) = manga.copy().apply {
            copyFrom(fetched)
            runCatching { fetched.title }.getOrNull()?.takeIf { it.isNotBlank() }?.let { title = it }
        }
        fun gotChapters(list: List<SChapter>) {
            chapters = list
            if (Library.contains(source.id, manga.url)) ChapterCache.put(source.id, manga.url, list)
        }
        try {
            // Newer extensions (Keiyoushi's KeiSource) only implement getMangaUpdate: details + chapters in one call.
            val update = MangaCalls.update(source, manga, fetchDetails = true, fetchChapters = true)
            details = merge(update.manga)
            gotChapters(update.chapters)
            Library.refresh(source.id, details)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace() // shows up in the terminal
            // Try the parts separately, so a details error doesn't hide the chapters (or the other way round)
            try {
                coroutineScope {
                    launch {
                        runCatching { MangaCalls.update(source, manga, true, false) }
                            .getOrNull()?.let { details = merge(it.manga) }
                    }
                    launch {
                        val list = MangaCalls.update(source, manga, false, true).chapters
                        gotChapters(list)
                    }
                }
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Throwable) {
                e2.printStackTrace()
                error = e2.message ?: e2.toString()
            }
        }
    }

    // Scanlation groups in this chapter list, with how many chapters each has (most first)
    var hiddenGroups by remember(source.id, manga.url) { mutableStateOf(ChapterFilters.hiddenGroups(source.id, manga.url)) }
    fun updateHidden(hidden: Set<String>) {
        hiddenGroups = hidden
        ChapterFilters.setHiddenGroups(source.id, manga.url, hidden)
    }
    val groups = remember(chapters) {
        chapters.orEmpty().groupingBy { ChapterFilters.groupOf(it) }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val visibleChapters = remember(chapters, hiddenGroups) {
        chapters.orEmpty().filter { ChapterFilters.groupOf(it) !in hiddenGroups }
    }
    // Reading order (oldest first) for the reader and "continue"
    val ordered = remember(visibleChapters) { visibleChapters.reversed() }
    fun read(index: Int) {
        if (index in ordered.indices) ReaderLauncher.open(ReaderRequest(source, details, ordered, index))
    }

    var categoriesDialog by remember { mutableStateOf(false) }
    var trackingDialog by remember { mutableStateOf(false) }
    if (trackingDialog) {
        TrackingDialog(source.id, details, chapters.orEmpty(), onDismiss = { trackingDialog = false })
    }
    if (categoriesDialog) {
        CategoriesDialog(source.id, manga.url, onDismiss = { categoriesDialog = false })
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.height(8.dp))

            Row {
                AsyncImage(
                    model = rememberImageRequest(
                        details.thumbnail_url,
                        (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.headers,
                    ),
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(details.title, style = MaterialTheme.typography.headlineSmall)
                    details.author?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${details.statusText()} • ${source.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Continue / start reading
                        if (ordered.isNotEmpty()) {
                            val next = ReadProgress.continueIndex(source.id, manga.url, ordered)
                            val started = ReadProgress.manga(source.id, manga.url)?.chapters?.isNotEmpty() == true
                            Button(onClick = { read(next) }) {
                                Text(
                                    if (started) "▶  Continue: ${ordered.getOrNull(next)?.name.orEmpty()}" else "▶  Start reading",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        val inLibrary = Library.contains(source.id, manga.url)
                        if (inLibrary) {
                            FilledTonalButton(onClick = { Library.remove(source.id, manga.url) }) { Text("♥  In library") }
                            OutlinedButton(onClick = { categoriesDialog = true }) {
                                val names = Library.get(source.id, manga.url)?.categories.orEmpty()
                                    .mapNotNull { id -> Categories.list.firstOrNull { it.id == id }?.name }
                                Text(if (names.isEmpty()) "Categories" else names.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            OutlinedButton(onClick = { Library.add(source.id, details, chapters) }) { Text("♡  Add to library") }
                        }

                        if (ordered.isNotEmpty()) DownloadMenuButton(source, details, ordered)
                        OutlinedButton(onClick = { trackingDialog = true }) { Text(trackingLabel(source.id, manga.url)) }
                    }
                }
            }

            details.description?.let { description ->
                var expanded by remember { mutableStateOf(false) }
                Spacer(Modifier.height(16.dp))
                Text(
                    description,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }

            val genres = details.genre?.split(", ")?.filter { it.isNotBlank() }.orEmpty()
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        error != null && chapters == null -> "Chapters"
                        chapters == null -> "Loading chapters…"
                        chapters!!.isEmpty() -> "No readable chapters (they may only be on official sites)"
                        visibleChapters.size != chapters!!.size ->
                            "${visibleChapters.size} of ${chapters!!.size} chapters (filtered by group)"
                        else -> "${chapters!!.size} chapters"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(16.dp))
                if (groups.size > 1 || hiddenGroups.isNotEmpty()) {
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { menuOpen = true }) {
                            Text(if (hiddenGroups.isEmpty()) "Filter groups" else "Groups: ${groups.count { it.first !in hiddenGroups }}/${groups.size}")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Show all groups") }, onClick = { updateHidden(emptySet()) })
                            HorizontalDivider()
                            groups.forEach { (group, count) ->
                                val shown = group !in hiddenGroups
                                DropdownMenuItem(
                                    leadingIcon = { Checkbox(checked = shown, onCheckedChange = null) },
                                    text = { Text("${group.ifEmpty { "No group listed" }}  ($count)") },
                                    onClick = { updateHidden(if (shown) hiddenGroups + group else hiddenGroups - group) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Done") }, onClick = { menuOpen = false })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (visibleChapters.isNotEmpty()) {
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        TextButton(onClick = { menuOpen = true }) { Text("⋮ Mark") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Mark all as read") }, onClick = {
                                ReadProgress.setRead(source.id, details, visibleChapters, true)
                                menuOpen = false
                            })
                            DropdownMenuItem(text = { Text("Mark all as unread") }, onClick = {
                                ReadProgress.setRead(source.id, details, visibleChapters, false)
                                menuOpen = false
                            })
                        }
                    }
                }
            }
        }

        error?.let {
            item { Text("Couldn't load: $it", color = MaterialTheme.colorScheme.error) }
        }

        items(visibleChapters, key = { it.url }) { chapter ->
            ChapterRow(source, details, chapter, ordered, onRead = { read(ordered.indexOf(chapter)) })
        }
    }
}

@Composable
private fun ChapterRow(source: Source, manga: SManga, chapter: SChapter, ordered: List<SChapter>, onRead: () -> Unit) {
    val progress = ReadProgress.chapter(source.id, manga.url, chapter.url)
    val isRead = progress?.read == true
    ListItem(
        modifier = Modifier.clickable(onClick = onRead).alpha(if (isRead) 0.45f else 1f),
        colors = ListItemDefaults.colors(),
        headlineContent = { Text(chapter.name) },
        supportingContent = {
            val inProgress = progress?.takeIf { !it.read && it.lastPage > 0 && it.pageCount > 0 }
                ?.let { "Page ${it.lastPage + 1} of ${it.pageCount}" }
            Text(listOfNotNull(chapter.date_upload.toDateString(), chapter.scanlator, inProgress).joinToString(" • "))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadIndicator(source, manga, chapter)
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isRead) "Mark as unread" else "Mark as read") },
                            onClick = {
                                ReadProgress.setRead(source.id, manga, listOf(chapter), !isRead)
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark previous as read") },
                            onClick = {
                                val index = ordered.indexOfFirst { it.url == chapter.url }
                                if (index > 0) ReadProgress.setRead(source.id, manga, ordered.subList(0, index), true)
                                menuOpen = false
                            },
                        )
                        if (DownloadManager.isDownloaded(source, manga, chapter)) {
                            DropdownMenuItem(text = { Text("Delete download") }, onClick = {
                                DownloadManager.delete(source, manga, listOf(chapter))
                                menuOpen = false
                            })
                        } else {
                            DropdownMenuItem(text = { Text("Download") }, onClick = {
                                DownloadManager.enqueue(source, manga, listOf(chapter))
                                menuOpen = false
                            })
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DownloadIndicator(source: Source, manga: SManga, chapter: SChapter) {
    val task = DownloadManager.taskFor(source, manga, chapter)
    when {
        DownloadManager.isDownloaded(source, manga, chapter) ->
            Text("✓ Offline", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        task?.error != null -> TextButton(onClick = {
            task.error = null
            DownloadManager.resume()
        }) { Text("Failed · retry", color = MaterialTheme.colorScheme.error) }
        task?.running == true -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(if (task.total > 0) "${task.done}/${task.total}" else "…", style = MaterialTheme.typography.labelMedium)
        }
        task != null -> Text("Queued", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> TextButton(onClick = { DownloadManager.enqueue(source, manga, listOf(chapter)) }) { Text("↓") }
    }
}

@Composable
private fun DownloadMenuButton(source: Source, manga: SManga, ordered: List<SChapter>) {
    Box {
        var open by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { open = true }) { Text("↓  Download") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val unread = ordered.filter { !ReadProgress.isRead(source.id, manga.url, it.url) }
            DropdownMenuItem(text = { Text("Next chapter") }, onClick = {
                DownloadManager.enqueue(source, manga, unread.take(1))
                open = false
            })
            DropdownMenuItem(text = { Text("Next 5 chapters") }, onClick = {
                DownloadManager.enqueue(source, manga, unread.take(5))
                open = false
            })
            DropdownMenuItem(text = { Text("All unread (${unread.size})") }, onClick = {
                DownloadManager.enqueue(source, manga, unread)
                open = false
            })
            DropdownMenuItem(text = { Text("All chapters (${ordered.size})") }, onClick = {
                DownloadManager.enqueue(source, manga, ordered)
                open = false
            })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Delete all downloads") }, onClick = {
                DownloadManager.delete(source, manga, ordered)
                open = false
            })
        }
    }
}

@Composable
private fun CategoriesDialog(sourceId: Long, url: String, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(Library.get(sourceId, url)?.categories.orEmpty().toSet()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column {
                if (Categories.list.isEmpty()) {
                    Text("No categories yet. Add one below (or from the Library tab).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Categories.list.forEach { category ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (category.id in selected) selected - category.id else selected + category.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = category.id in selected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(category.name)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        Categories.add(newName)
                        Categories.list.firstOrNull { it.name.equals(newName.trim(), ignoreCase = true) }?.let { selected = selected + it.id }
                        newName = ""
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Keep the order of the category list
                Library.setCategories(sourceId, url, Categories.list.map { it.id }.filter { it in selected })
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun SManga.statusText() = when (status) {
    SManga.ONGOING -> "Ongoing"
    SManga.COMPLETED -> "Completed"
    SManga.ON_HIATUS -> "On hiatus"
    SManga.CANCELLED -> "Cancelled"
    else -> "Unknown"
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

internal fun Long.toDateString(): String? =
    if (this <= 0) null else Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFormat)
