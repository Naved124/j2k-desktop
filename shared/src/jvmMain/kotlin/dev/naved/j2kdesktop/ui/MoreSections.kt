package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.naved.j2kdesktop.AppSettings
import dev.naved.j2kdesktop.AppTheme
import dev.naved.j2kdesktop.backup.BackupManager
import dev.naved.j2kdesktop.reader.ReadingMode
import dev.naved.j2kdesktop.tracking.AniList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun AppearanceAndReaderSettings() {
    Title("Appearance")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Theme")
        AppTheme.entries.forEach { theme ->
            FilterChip(
                selected = AppSettings.theme == theme,
                onClick = { AppSettings.changeTheme(theme) },
                label = { Text(theme.label) },
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Title("Reader defaults")
    Hint("For manga you haven't opened yet. Each manga remembers its own mode and direction after that.")
    var mode by remember { mutableStateOf(AppSettings.defaultReadingMode) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mode")
        ReadingMode.entries.forEach { m ->
            FilterChip(selected = mode == m.name, onClick = {
                mode = m.name
                AppSettings.defaultReadingMode = m.name
            }, label = { Text(m.label) })
        }
    }
    var rtl by remember { mutableStateOf(AppSettings.defaultRightToLeft) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Direction")
        FilterChip(selected = rtl, onClick = {
            rtl = true
            AppSettings.defaultRightToLeft = true
        }, label = { Text("Right to left (manga)") })
        FilterChip(selected = !rtl, onClick = {
            rtl = false
            AppSettings.defaultRightToLeft = false
        }, label = { Text("Left to right") })
    }
    var autoWebtoon by remember { mutableStateOf(AppSettings.autoWebtoon) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Open long-strip series in webtoon mode")
            Hint("Manhwa, webtoons and web comics (from the manga's genres)")
        }
        Switch(checked = autoWebtoon, onCheckedChange = {
            autoWebtoon = it
            AppSettings.autoWebtoon = it
        })
    }
}

@Composable
fun BackupSection() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    Title("Backup")
    Hint(
        "Import a backup from Tachiyomi, TachiyomiJ2K or Mihon (.tachibk / .proto.gz): library, categories, " +
            "read chapters, history, hidden groups and your extension repos. It merges; nothing is deleted. " +
            "Backups made here import into those apps too.",
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(enabled = !working, onClick = {
            val file = chooseFile("Choose a backup", save = false, suggested = null) ?: return@OutlinedButton
            working = true
            status = "Importing ${file.name}…"
            scope.launch {
                status = try {
                    val backup = withContext(Dispatchers.IO) { BackupManager.read(file) }
                    "Imported. " + BackupManager.import(backup).toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Couldn't import ${file.name}: ${e.message ?: e}"
                } finally {
                    working = false
                }
            }
        }) { Text("Import backup…") }
        OutlinedButton(enabled = !working, onClick = {
            val file = chooseFile("Save backup", save = true, suggested = BackupManager.defaultBackupName())
                ?: return@OutlinedButton
            working = true
            status = "Saving…"
            scope.launch {
                status = try {
                    val backup = BackupManager.create()
                    withContext(Dispatchers.IO) { BackupManager.write(backup, file) }
                    "Saved ${backup.backupManga.size} manga to ${file.path}"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Couldn't save the backup: ${e.message ?: e}"
                } finally {
                    working = false
                }
            }
        }) { Text("Create backup…") }
    }
    status?.let {
        Spacer(Modifier.height(6.dp))
        Text(it)
    }
}

@Composable
fun TrackingSection() {
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf(AniList.clientId) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Title("Tracking (AniList)")
    if (AniList.isLoggedIn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logged in as ${AniList.userName}", modifier = Modifier.weight(1f))
            TextButton(onClick = { AniList.logout() }) { Text("Log out") }
        }
        Hint("Link a manga from its page (AniList button). Finishing chapters updates your progress.")
        return
    }
    Hint(
        "One-time setup: on anilist.co go to Settings → Developer → Create new client, name it anything and set the " +
            "redirect URL to ${AniList.PIN_REDIRECT}. Paste its Client ID here, open the login page, approve, " +
            "then copy the token AniList shows and paste it below.",
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it },
            label = { Text("Client ID") },
            singleLine = true,
            modifier = Modifier.width(200.dp),
        )
        OutlinedButton(enabled = clientId.isNotBlank(), onClick = {
            AniList.saveClientId(clientId)
            openUrl(AniList.loginPageUrl())
        }) { Text("Open login page") }
        TextButton(onClick = { openUrl("https://anilist.co/settings/developer") }) { Text("AniList developer settings") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token from AniList") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(enabled = token.isNotBlank(), onClick = {
            status = "Checking…"
            scope.launch {
                status = try {
                    "Logged in as ${AniList.login(token)}"
                } catch (e: Exception) {
                    "Login failed: ${e.message ?: e}"
                }
            }
        }) { Text("Log in") }
    }
    status?.let { Text(it) }
}

/** A native file dialog (blocking, on the UI thread). */
private fun chooseFile(title: String, save: Boolean, suggested: String?): File? {
    val dialog = FileDialog(null as Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    dialog.directory = System.getProperty("user.home")
    if (suggested != null) dialog.file = suggested
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
