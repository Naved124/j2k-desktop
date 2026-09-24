package dev.naved.j2kdesktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.setSingletonImageLoaderFactory
import dev.naved.j2kdesktop.ui.BrowseTab
import dev.naved.j2kdesktop.ui.DownloadsTab
import dev.naved.j2kdesktop.download.DownloadManager
import dev.naved.j2kdesktop.ui.LibraryTab
import dev.naved.j2kdesktop.ui.MoreTab
import dev.naved.j2kdesktop.ui.RecentsTab
import dev.naved.j2kdesktop.library.LibraryUpdater
import kotlinx.coroutines.flow.first
import dev.naved.j2kdesktop.ui.buildImageLoader
import dev.naved.j2kdesktop.compat.AndroidCompat
import dev.naved.j2kdesktop.extension.ExtensionManager
import dev.naved.j2kdesktop.reader.ReaderLauncher
import dev.naved.j2kdesktop.reader.ReaderScreen
import kotlinx.coroutines.launch

enum class Tab(val label: String, val glyph: String) {
    Library("Library", "▤"),
    Recents("Recents", "◷"),
    Browse("Browse", "⌕"),
    Downloads("Downloads", "↓"),
    More("More", "⋯"),
}

@Composable
fun App() {
    setSingletonImageLoaderFactory { context -> buildImageLoader(context) }

    // Extension toasts show up as snackbars; installed extensions load in the background
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        val scope = this
        AndroidCompat.toastHandler = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        ExtensionManager.start(scope)
        // Once installed extensions are loaded, check the library for new chapters
        if (AppSettings.updateOnStart) {
            snapshotFlow { ExtensionManager.installedLoaded }.first { it }
            LibraryUpdater.start()
        }
    }

    val colors = AppSettings.theme.colors()
    MaterialTheme(colorScheme = colors) {
      // Scrollbars in the theme's colours (the default ones are invisible on dark backgrounds)
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.foundation.LocalScrollbarStyle provides androidx.compose.foundation.ScrollbarStyle(
              minimalHeight = 48.dp,
              thickness = 10.dp,
              shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
              hoverDurationMillis = 250,
              unhoverColor = colors.onSurface.copy(alpha = 0.25f),
              hoverColor = colors.onSurface.copy(alpha = 0.6f),
          ),
      ) {
        var current by remember { mutableStateOf(Tab.Library) }

        Surface(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(12.dp))
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = current == tab,
                            onClick = { current = tab },
                            icon = {
                                val queued = DownloadManager.queue.size
                                if (tab == Tab.Downloads && queued > 0) {
                                    BadgedBox(badge = { Badge { Text(if (queued > 99) "99+" else "$queued") } }) {
                                        Text(tab.glyph, style = MaterialTheme.typography.titleLarge)
                                    }
                                } else {
                                    Text(tab.glyph, style = MaterialTheme.typography.titleLarge)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                    // Incognito: a switch for this session only (never saved, off at every start)
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = Incognito.enabled,
                        onClick = { Incognito.toggle() },
                        icon = { Text(if (Incognito.enabled) "◉" else "◌", style = MaterialTheme.typography.titleLarge) },
                        label = { Text("Incognito") },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (Incognito.enabled) IncognitoBanner()
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                        when (current) {
                            Tab.Library   -> LibraryTab()
                            Tab.Recents   -> RecentsTab()
                            Tab.Browse    -> BrowseTab()
                            Tab.Downloads -> DownloadsTab()
                            Tab.More      -> MoreTab()
                        }
                    }
                }
            }
            // The reader draws over everything; Browse stays underneath so it keeps its place
            ReaderLauncher.request?.let { request ->
                ReaderScreen(request, onClose = { ReaderLauncher.close() })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
          }
        }
      }
    }
}

@Composable
private fun IncognitoBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Incognito is on: nothing you read is saved (no history, no progress, no tracking). It turns off when you close the app.",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { Incognito.enabled = false }) {
            Text("Turn off", color = MaterialTheme.colorScheme.onTertiaryContainer)
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