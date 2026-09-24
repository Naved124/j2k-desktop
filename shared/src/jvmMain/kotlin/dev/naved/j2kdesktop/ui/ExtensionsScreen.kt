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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.naved.j2kdesktop.extension.AvailableExtension
import dev.naved.j2kdesktop.extension.ExtensionManager
import dev.naved.j2kdesktop.extension.InstalledExtension

/** J2K-style Extensions tab: repos, installed (with updates), and everything installable. */
@Composable
fun ExtensionsScreen() {
    val manager = ExtensionManager
    var query by remember { mutableStateOf("") }
    var showNsfw by remember { mutableStateOf(true) }
    var repoInput by remember { mutableStateOf("") }
    var showRepos by remember { mutableStateOf(manager.repos.isEmpty()) }

    val installedPkgs = manager.installed.map { it.meta.pkg }.toSet()
    val hiddenLanguages = dev.naved.j2kdesktop.AppSettings.hiddenLanguages
    fun matches(name: String, lang: String, nsfw: Boolean) =
        (showNsfw || !nsfw) && lang !in hiddenLanguages &&
            (query.isBlank() || name.contains(query, ignoreCase = true) || lang.equals(query, ignoreCase = true))

    val installed = manager.installed.filter { matches(it.meta.name, it.meta.lang, it.meta.nsfw) }
    val available = manager.available.filter { it.pkg !in installedPkgs && matches(it.name, it.lang, it.nsfw) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search extensions or a language code (en, ja…)") },
                modifier = Modifier.width(420.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showNsfw, onCheckedChange = { showNsfw = it })
                Text("Show 18+")
            }
            TextButton(onClick = { manager.refresh() }, enabled = !manager.isRefreshing) {
                Text(if (manager.isRefreshing) "Refreshing…" else "Refresh")
            }
            TextButton(onClick = { showRepos = !showRepos }) {
                Text(if (showRepos) "Hide repos" else "Repos (${manager.repos.size})")
            }
        }

        manager.message?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(msg, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { manager.message = null }) { Text("Dismiss") }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (showRepos) {
                item(key = "repos") {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        SectionHeader("Extension repos")
                        manager.repos.forEach { repo ->
                            ListItem(
                                headlineContent = { Text(repo.name) },
                                supportingContent = { Text(repo.url) },
                                trailingContent = {
                                    TextButton(onClick = { manager.removeRepo(repo.url) }) { Text("Remove") }
                                },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = repoInput,
                                onValueChange = { repoInput = it },
                                singleLine = true,
                                placeholder = { Text("Repo URL (…/index.min.json) or a tachiyomi:// link") },
                                modifier = Modifier.width(520.dp),
                            )
                            Button(
                                onClick = {
                                    manager.addRepo(repoInput)
                                    repoInput = ""
                                },
                                enabled = repoInput.isNotBlank(),
                            ) { Text("Add") }
                        }
                        Text(
                            "Tip: run scripts/install-link-handler.sh once, then the \"Add\" buttons on repo " +
                                "websites (tachiyomi:// and mihon:// links) add repos here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (manager.repos.isEmpty()) {
                item(key = "no-repos") {
                    Text(
                        "No repos yet. Add one above to see extensions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            if (installed.isNotEmpty()) {
                item(key = "h-installed") { SectionHeader("Installed (${installed.size})") }
                items(installed, key = { "i-" + it.meta.pkg }) { ext -> InstalledRow(ext) }
            }

            if (available.isNotEmpty()) {
                item(key = "h-available") { SectionHeader("Available (${available.size})") }
                items(available, key = { "a-" + it.pkg }) { ext -> AvailableRow(ext) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InstalledRow(ext: InstalledExtension) {
    val manager = ExtensionManager
    val update = manager.availableUpdate(ext)
    // A broken install can be retried with the same version (e.g. after an app fix)
    val reinstall = if (ext.error != null) manager.available.firstOrNull { it.pkg == ext.meta.pkg } else null
    val busy = manager.busy[ext.meta.pkg]
    ListItem(
        leadingContent = { ExtensionIcon(ext.meta.iconUrl, ext.meta.name) },
        headlineContent = { Text(ext.meta.name) },
        supportingContent = {
            Column {
                Text(extensionSubtitle(ext.meta.lang, ext.meta.versionName, ext.meta.nsfw))
                ext.error?.let {
                    Text("Failed to load: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    busy != null -> Text(busy)
                    update != null -> Button(onClick = { manager.install(update) }) { Text("Update") }
                    reinstall != null -> Button(onClick = { manager.install(reinstall) }) { Text("Reinstall") }
                }
                if (busy == null) OutlinedButton(onClick = { manager.uninstall(ext.meta.pkg) }) { Text("Uninstall") }
            }
        },
    )
}

@Composable
private fun AvailableRow(ext: AvailableExtension) {
    val manager = ExtensionManager
    val busy = manager.busy[ext.pkg]
    ListItem(
        leadingContent = { ExtensionIcon(ext.iconUrl, ext.name) },
        headlineContent = { Text(ext.name) },
        supportingContent = { Text(extensionSubtitle(ext.lang, ext.versionName, ext.nsfw)) },
        trailingContent = {
            if (busy != null) Text(busy) else Button(onClick = { manager.install(ext) }) { Text("Install") }
        },
    )
}

@Composable
private fun ExtensionIcon(url: String?, name: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

private fun extensionSubtitle(lang: String, version: String, nsfw: Boolean) =
    buildString {
        append(languageName(lang))
        append(" • v").append(version)
        if (nsfw) append(" • 18+")
    }
