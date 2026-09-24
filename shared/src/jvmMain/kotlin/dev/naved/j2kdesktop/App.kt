package dev.naved.j2kdesktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.setSingletonImageLoaderFactory
import dev.naved.j2kdesktop.ui.BrowseTab
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

    val colors = when (AppSettings.theme) {
        AppTheme.Dark -> darkColorScheme()
        AppTheme.Black -> darkColorScheme(
            background = androidx.compose.ui.graphics.Color.Black,
            surface = androidx.compose.ui.graphics.Color.Black,
            surfaceContainer = androidx.compose.ui.graphics.Color(0xFF0E0E0E),
        )
        AppTheme.Light -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
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
                            icon = { Text(tab.glyph, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(tab.label) },
                        )
                    }
                }

                Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                    when (current) {
                        Tab.Library -> LibraryTab()
                        Tab.Recents -> RecentsTab()
                        Tab.Browse  -> BrowseTab()
                        Tab.More    -> MoreTab()
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