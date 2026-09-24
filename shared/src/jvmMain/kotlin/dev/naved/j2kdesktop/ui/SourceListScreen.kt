package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.naved.j2kdesktop.AppSettings
import dev.naved.j2kdesktop.extension.ExtensionManager
import dev.naved.j2kdesktop.source.SourceManager
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceListScreen(
    onOpenSource: (CatalogueSource, Listing) -> Unit,
    onOpenSettings: (CatalogueSource) -> Unit,
) {
    var showExtensions by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("Browse", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !showExtensions, onClick = { showExtensions = false }, label = { Text("Sources") })
            FilterChip(selected = showExtensions, onClick = { showExtensions = true }, label = { Text("Extensions") })
            Spacer(Modifier.width(8.dp))
            LanguageFilterButton()
        }
        Spacer(Modifier.height(8.dp))

        if (showExtensions) {
            ExtensionsScreen()
        } else {
            // Grouped by language like J2K, with "Other" (Local manga) first
            val hidden = AppSettings.hiddenLanguages
            // Each source's icon is its extension's icon
            val icons = ExtensionManager.installed
                .flatMap { ext -> ext.sources.map { it.id to ext.meta.iconUrl } }
                .toMap()
            val groups = SourceManager.catalogueSources
                .filter { it.lang !in hidden }
                .groupBy { it.lang }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<String, List<CatalogueSource>>>(
                        { it.key != "other" },
                        { languageName(it.key) },
                    ),
                )

            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = ScrollbarGutter)) {
                groups.forEach { (lang, sources) ->
                    item(key = "header-$lang") {
                        Text(
                            languageName(lang),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(sources, key = { it.id }) { source ->
                        SourceRow(
                            source = source,
                            iconUrl = icons[source.id],
                            onOpen = { onOpenSource(source, Listing.Popular) },
                            onLatest = { onOpenSource(source, Listing.Latest) },
                            onSettings = { onOpenSettings(source) },
                        )
                    }
                }
            }
            ListScrollbar(listState)
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: CatalogueSource,
    iconUrl: String?,
    onOpen: () -> Unit,
    onLatest: () -> Unit,
    onSettings: () -> Unit,
) {
    ListItem(
        leadingContent = {
            // The extension's icon; a letter for built-in sources (and while it loads)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    source.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (iconUrl != null) {
                    AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        },
        headlineContent = { Text(source.name) },
        supportingContent = { Text(languageName(source.lang)) },
        trailingContent = {
            Row {
                if (source is ConfigurableSource) TextButton(onClick = onSettings) { Text("Settings") }
                if (source.supportsLatest) TextButton(onClick = onLatest) { Text("Latest") }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

internal fun languageName(lang: String): String = when (lang) {
    "", "other" -> "Other"
    "all" -> "All"
    else -> Locale.forLanguageTag(lang).getDisplayName(Locale.ENGLISH).ifBlank { lang }
}


/** "Languages" menu: pick which languages show in Browse (sources and extensions). */
@Composable
fun LanguageFilterButton() {
    val languages = (
        SourceManager.catalogueSources.map { it.lang } +
            ExtensionManager.available.map { it.lang } +
            ExtensionManager.installed.map { it.meta.lang }
        ).filter { it.isNotBlank() }.distinct().sortedWith(compareBy({ it != "all" }, { languageName(it) }))
    val hidden = AppSettings.hiddenLanguages
    Box {
        var open by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { open = true }) {
            Text(if (hidden.isEmpty()) "Languages: all" else "Languages: ${languages.count { it !in hidden }}/${languages.size}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Show all") }, onClick = { AppSettings.showAllLanguages() })
            DropdownMenuItem(text = { Text("Only English (+ All)") }, onClick = {
                languages.forEach { AppSettings.setLanguageShown(it, it == "en" || it == "all" || it == "other") }
            })
            HorizontalDivider()
            languages.forEach { lang ->
                val shown = lang !in hidden
                DropdownMenuItem(
                    leadingIcon = { Checkbox(checked = shown, onCheckedChange = null) },
                    text = { Text("${languageName(lang)}  ($lang)") },
                    onClick = { AppSettings.setLanguageShown(lang, !shown) },
                )
            }
        }
    }
}
