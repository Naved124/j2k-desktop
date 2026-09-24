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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.naved.j2kdesktop.library.Categories
import dev.naved.j2kdesktop.library.ChapterCache
import dev.naved.j2kdesktop.library.Library
import dev.naved.j2kdesktop.library.LibraryEntry
import dev.naved.j2kdesktop.library.LibraryUpdater
import dev.naved.j2kdesktop.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource

private enum class LibrarySort(val label: String) { Title("A-Z"), Added("Recently added"), Unread("Unread") }

/** Which tab of the library: everything, one category, or manga without a category ("Default"). */
private sealed interface Shelf {
    data object All : Shelf
    data object Default : Shelf
    data class InCategory(val id: Long) : Shelf
}

@Composable
fun LibraryTab() {
    var open by remember { mutableStateOf<LibraryEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.Title) }
    var shelf by remember { mutableStateOf<Shelf>(Shelf.All) }
    var editCategories by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf(emptySet<Int>()) }
    var filterGenres by remember { mutableStateOf(emptySet<String>()) }
    var filterLangs by remember { mutableStateOf(emptySet<String>()) }
    val gridState = rememberLazyGridState()

    val entry = open
    if (entry != null) {
        val source = SourceManager.get(entry.sourceId)
        if (source == null) {
            Column {
                TextButton(onClick = { open = null }) { Text("← Back") }
                Text(
                    "The extension for \"${entry.title}\" isn't installed (or is still loading).",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            MangaScreen(source = source, manga = entry.toSManga(), onBack = { open = null })
        }
        return
    }

    if (editCategories) EditCategoriesDialog(onDismiss = { editCategories = false })

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${Library.entries.size} manga",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (LibraryUpdater.isRunning) {
                TextButton(onClick = { LibraryUpdater.cancel() }) { Text("Stop update") }
            } else {
                OutlinedButton(onClick = {
                    LibraryUpdater.start((shelf as? Shelf.InCategory)?.id)
                }) { Text("⟳  Update") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search library") },
                singleLine = true,
                modifier = Modifier.width(240.dp),
            )
        }

        if (LibraryUpdater.isRunning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (LibraryUpdater.total == 0) 0f else LibraryUpdater.done.toFloat() / LibraryUpdater.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LibraryUpdater.status?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                if (LibraryUpdater.isRunning) "Checking ${LibraryUpdater.done}/${LibraryUpdater.total}…" else it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Category tabs + sort
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = shelf == Shelf.All, onClick = { shelf = Shelf.All }, label = { Text("All") })
            if (Categories.list.isNotEmpty()) {
                FilterChip(selected = shelf == Shelf.Default, onClick = { shelf = Shelf.Default }, label = { Text("Default") })
            }
            Categories.list.forEach { category ->
                val count = Library.entries.count { category.id in it.categories }
                FilterChip(
                    selected = shelf == Shelf.InCategory(category.id),
                    onClick = { shelf = Shelf.InCategory(category.id) },
                    label = { Text("${category.name} ($count)") },
                )
            }
            TextButton(onClick = { editCategories = true }) { Text("Edit categories") }
            LibraryFilterButton(
                status = filterStatus,
                genres = filterGenres,
                langs = filterLangs,
                onStatus = { filterStatus = it },
                onGenres = { filterGenres = it },
                onLangs = { filterLangs = it },
            )
            Spacer(Modifier.width(24.dp))
            Text("Sort:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LibrarySort.entries.forEach { option ->
                FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (Library.entries.isEmpty()) {
            Text(
                "Nothing here yet. Open a manga from Browse and press \"Add to library\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val currentShelf = shelf
        val shown = Library.entries
            .filter {
                when (currentShelf) {
                    Shelf.All -> true
                    Shelf.Default -> it.categories.isEmpty()
                    is Shelf.InCategory -> currentShelf.id in it.categories
                }
            }
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            .filter { filterStatus.isEmpty() || it.status in filterStatus }
            .filter { e -> filterGenres.isEmpty() || genresOf(e).let { g -> filterGenres.all { it in g } } }
            .filter { e -> filterLangs.isEmpty() || (SourceManager.get(e.sourceId)?.lang ?: "?") in filterLangs }
            .let { list ->
                when (sort) {
                    LibrarySort.Title -> list.sortedBy { it.title.lowercase() }
                    LibrarySort.Added -> list.sortedByDescending { it.addedAt }
                    LibrarySort.Unread -> list.sortedByDescending { ChapterCache.unreadCount(it.sourceId, it.url) ?: 0 }
                }
            }

        if (shown.isEmpty()) {
            Text("No manga here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(dev.naved.j2kdesktop.AppSettings.gridColumns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().padding(end = ScrollbarGutter),
        ) {
            items(shown, key = { it.key }) { item ->
                val headers = (SourceManager.get(item.sourceId) as? HttpSource)?.headers
                val unread = ChapterCache.unreadCount(item.sourceId, item.url)
                MangaCard(
                    manga = item.toSManga(),
                    onClick = { open = item },
                    headers = headers,
                    badge = unread?.takeIf { it > 0 }?.toString(),
                )
            }
        }
        GridScrollbar(gridState)
        }
    }
}

@Composable
private fun EditCategoriesDialog(onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (Categories.list.isEmpty()) {
                    Text("No categories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Categories.list.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (renaming == category.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                Categories.rename(category.id, renameText)
                                renaming = null
                            }) { Text("Save") }
                        } else {
                            Text(category.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { Categories.move(category.id, up = true) }) { Text("↑") }
                            TextButton(onClick = { Categories.move(category.id, up = false) }) { Text("↓") }
                            TextButton(onClick = {
                                renaming = category.id
                                renameText = category.name
                            }) { Text("Rename") }
                            TextButton(onClick = { Categories.delete(category.id) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        Categories.add(newName)
                        newName = ""
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}


private fun genresOf(entry: LibraryEntry): Set<String> =
    entry.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

private val statusNames = listOf(
    eu.kanade.tachiyomi.source.model.SManga.ONGOING to "Ongoing",
    eu.kanade.tachiyomi.source.model.SManga.COMPLETED to "Completed",
    eu.kanade.tachiyomi.source.model.SManga.PUBLISHING_FINISHED to "Publishing finished",
    eu.kanade.tachiyomi.source.model.SManga.ON_HIATUS to "On hiatus",
    eu.kanade.tachiyomi.source.model.SManga.CANCELLED to "Cancelled",
    eu.kanade.tachiyomi.source.model.SManga.LICENSED to "Licensed",
    eu.kanade.tachiyomi.source.model.SManga.UNKNOWN to "Unknown",
)

/** Filter the library by status, genre (all selected must match) and source language. */
@Composable
private fun LibraryFilterButton(
    status: Set<Int>,
    genres: Set<String>,
    langs: Set<String>,
    onStatus: (Set<Int>) -> Unit,
    onGenres: (Set<String>) -> Unit,
    onLangs: (Set<String>) -> Unit,
) {
    val active = status.size + genres.size + langs.size
    androidx.compose.foundation.layout.Box {
        var open by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { open = true }) { Text(if (active == 0) "Filter" else "Filter ($active)") }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Clear filters") }, onClick = {
                onStatus(emptySet())
                onGenres(emptySet())
                onLangs(emptySet())
            })
            androidx.compose.material3.HorizontalDivider()
            MenuHeader("Status")
            val presentStatus = Library.entries.map { it.status }.toSet()
            statusNames.filter { it.first in presentStatus }.forEach { (code, name) ->
                MenuCheck(name, code in status) { onStatus(if (code in status) status - code else status + code) }
            }
            val langList = Library.entries.mapNotNull { SourceManager.get(it.sourceId)?.lang }.distinct().sorted()
            if (langList.size > 1) {
                androidx.compose.material3.HorizontalDivider()
                MenuHeader("Language")
                langList.forEach { lang ->
                    MenuCheck(languageName(lang), lang in langs) { onLangs(if (lang in langs) langs - lang else langs + lang) }
                }
            }
            val genreCounts = Library.entries.flatMap { genresOf(it) }.groupingBy { it }.eachCount()
                .toList().sortedByDescending { it.second }
            if (genreCounts.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                MenuHeader("Genres (manga must have all ticked)")
                genreCounts.forEach { (genre, count) ->
                    MenuCheck("$genre ($count)", genre in genres) { onGenres(if (genre in genres) genres - genre else genres + genre) }
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun MenuCheck(label: String, checked: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        leadingIcon = { androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null) },
        text = { Text(label) },
        onClick = onClick,
    )
}
