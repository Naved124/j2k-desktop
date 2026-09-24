package dev.naved.j2kdesktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.setSingletonImageLoaderFactory
import dev.naved.j2kdesktop.ui.BrowseTab
import dev.naved.j2kdesktop.ui.buildImageLoader

enum class Tab(val label: String, val glyph: String) {
    Library("Library", "▤"),
    Recents("Recents", "◷"),
    Browse("Browse", "⌕"),
    More("More", "⋯"),
}

@Composable
fun App() {
    setSingletonImageLoaderFactory { context -> buildImageLoader(context) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        var current by remember { mutableStateOf(Tab.Library) }

        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(12.dp))
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = current == tab,
                            onClick = { current = tab },
                            icon = { Text(tab.glyph, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(tab.label) },
                        )
                    }
                }

                Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                    when (current) {
                        Tab.Library -> PlaceholderScreen("Library", "Your manga will show up here.")
                        Tab.Recents -> PlaceholderScreen("Recents", "Recently read and new chapters.")
                        Tab.Browse  -> BrowseTab()
                        Tab.More    -> PlaceholderScreen("More", "Settings, backups, about.")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}