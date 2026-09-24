package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.naved.j2kdesktop.source.LocalSource
import dev.naved.j2kdesktop.source.SourceManager
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SManga

private sealed interface BrowseRoute {
    data object Home : BrowseRoute
    data class SourceRoute(val sourceId: Long) : BrowseRoute
    data class MangaRoute(val sourceId: Long, val manga: SManga) : BrowseRoute
    data class SettingsRoute(val sourceId: Long) : BrowseRoute
}

/** The Browse tab: source list -> a source's grid -> a manga, with a back stack. */
@Composable
fun BrowseTab() {
    val scope = rememberCoroutineScope()
    val models = remember { mutableMapOf<Long, BrowseModel>() } // one per source, so each keeps its list
    val stack = remember { mutableStateListOf<BrowseRoute>(BrowseRoute.Home) }

    fun push(route: BrowseRoute) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    when (val route = stack.last()) {
        BrowseRoute.Home -> SourceListScreen(
            onOpenSource = { source, listing ->
                models.getOrPut(source.id) { BrowseModel(source, scope) }.switchListing(listing)
                push(BrowseRoute.SourceRoute(source.id))
            },
            onOpenSettings = { source -> push(BrowseRoute.SettingsRoute(source.id)) },
        )

        is BrowseRoute.SourceRoute -> {
            val model = models[route.sourceId]
            if (model == null) {
                Text("Source not found")
            } else {
                SourceScreen(
                    model = model,
                    onBack = { pop() },
                    onMangaClick = { push(BrowseRoute.MangaRoute(route.sourceId, it)) },
                )
            }
        }

        is BrowseRoute.MangaRoute -> {
            val source = SourceManager.get(route.sourceId)
            if (source == null) {
                Text("Source not found")
            } else {
                MangaScreen(source = source, manga = route.manga, onBack = { pop() })
            }
        }

        is BrowseRoute.SettingsRoute -> {
            val source = SourceManager.get(route.sourceId) as? ConfigurableSource
            if (source == null) {
                Text("Source not found")
            } else {
                SourceSettingsScreen(source = source, onBack = { pop() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(
    model: BrowseModel,
    onBack: () -> Unit,
    onMangaClick: (SManga) -> Unit,
) {
    val gridState = model.gridState

    // Endless scroll: load more when within 6 covers of the end.
    // Includes `listing` so switching Popular/Latest/Search always triggers a fresh first load.
    LaunchedEffect(model) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = lastVisible >= model.mangas.size - 6 &&
                model.hasNextPage && !model.isLoading && model.error == null
            model.listing to nearEnd
        }.collect { (_, nearEnd) ->
            if (nearEnd) model.loadNextPage()
        }
    }

    var query by remember(model) {
        mutableStateOf((model.listing as? Listing.Search)?.query ?: "")
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text(model.source.name, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = model.listing == Listing.Popular,
                onClick = { model.switchListing(Listing.Popular) },
                label = { Text("Popular") },
            )
            if (model.source.supportsLatest) {
                FilterChip(
                    selected = model.listing == Listing.Latest,
                    onClick = { model.switchListing(Listing.Latest) },
                    label = { Text("Latest") },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search ${model.source.name} (Enter)") },
                modifier = Modifier
                    .width(360.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            if (query.isNotBlank()) model.switchListing(Listing.Search(query.trim()))
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(end = ScrollbarGutter),
        ) {
            items(model.mangas, key = { it.url }) { manga ->
                MangaCard(
                    manga,
                    onClick = { onMangaClick(manga) },
                    headers = (model.source as? eu.kanade.tachiyomi.source.online.HttpSource)?.headers,
                )
            }

            val error = model.error
            val isEmpty = !model.isLoading && error == null && model.mangas.isEmpty() && !model.hasNextPage

            if (model.isLoading || error != null || isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Couldn't load: $error", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { model.loadNextPage() }) { Text("Retry") }
                            }
                            isEmpty -> Text(
                                (model.source as? LocalSource)
                                    ?.let { "No local manga yet. Put series folders in ${it.rootDir}" }
                                    ?: "Nothing found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        GridScrollbar(gridState)
        }
    }
}
