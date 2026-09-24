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
import dev.naved.j2kdesktop.source.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceListScreen(onOpenSource: (CatalogueSource, Listing) -> Unit) {
    var showExtensions by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("Browse", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showExtensions, onClick = { showExtensions = false }, label = { Text("Sources") })
            FilterChip(selected = showExtensions, onClick = { showExtensions = true }, label = { Text("Extensions") })
        }
        Spacer(Modifier.height(8.dp))

        if (showExtensions) {
            Column(Modifier.padding(top = 16.dp)) {
                Text("No extensions yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Extension repos and installing extensions come in phase 7. " +
                        "Installed extensions will show up under Sources automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Grouped by language like J2K, with "Other" (Local manga) first
            val groups = SourceManager.catalogueSources
                .groupBy { it.lang }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<String, List<CatalogueSource>>>(
                        { it.key != "other" },
                        { languageName(it.key) },
                    ),
                )

            LazyColumn(Modifier.fillMaxSize()) {
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
                            onOpen = { onOpenSource(source, Listing.Popular) },
                            onLatest = { onOpenSource(source, Listing.Latest) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: CatalogueSource, onOpen: () -> Unit, onLatest: () -> Unit) {
    ListItem(
        leadingContent = {
            // Letter icon for now; extension icons come with phase 7
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
            }
        },
        headlineContent = { Text(source.name) },
        supportingContent = { Text(languageName(source.lang)) },
        trailingContent = {
            if (source.supportsLatest) TextButton(onClick = onLatest) { Text("Latest") }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

internal fun languageName(lang: String): String = when (lang) {
    "", "other" -> "Other"
    "all" -> "All"
    else -> Locale.forLanguageTag(lang).getDisplayName(Locale.ENGLISH).ifBlank { lang }
}
