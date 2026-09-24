package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import dev.naved.j2kdesktop.compat.AndroidCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey

/** Renders an extension's androidx.preference screen with Compose. */
@Composable
fun SourceSettingsScreen(source: ConfigurableSource, onBack: () -> Unit) {
    val prefs = remember(source) { AndroidCompat.sharedPreferences(source.preferenceKey()) }
    var setupError by remember(source) { mutableStateOf<String?>(null) }
    val screen = remember(source) {
        PreferenceScreen(AndroidCompat.application).also { screen ->
            screen.sharedPreferences = prefs
            runCatching { source.setupPreferenceScreen(screen) }
                .onFailure {
                    it.printStackTrace()
                    setupError = "${it::class.java.simpleName}: ${it.message}"
                }
        }
    }
    // Bumped after every change so the values on screen are re-read
    var version by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Preference?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text("${source.name} settings", style = MaterialTheme.typography.headlineSmall)
        }
        setupError?.let {
            Text("This extension's settings failed to load: $it", color = MaterialTheme.colorScheme.error)
        }

        val rows = remember(screen) { flatten(screen) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows.size) { i ->
                val (pref, isHeader) = rows[i]
                version // read so rows recompose after changes
                if (!pref.isVisible) return@items
                if (isHeader) {
                    Text(
                        pref.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                } else {
                    PreferenceRow(pref, prefs, onEdit = { editing = it }, onChanged = { version++ })
                }
            }
        }
    }

    editing?.let { pref ->
        PreferenceDialog(
            pref = pref,
            prefs = prefs,
            onDismiss = { editing = null },
            onChanged = {
                version++
                editing = null
            },
        )
    }
}

/** Categories become headers; everything else becomes a row. */
private fun flatten(group: PreferenceGroup): List<Pair<Preference, Boolean>> =
    group.children().flatMap { p ->
        if (p is PreferenceGroup) listOf(p to true) + flatten(p) else listOf(p to false)
    }

@Composable
private fun PreferenceRow(
    pref: Preference,
    prefs: android.content.SharedPreferences,
    onEdit: (Preference) -> Unit,
    onChanged: () -> Unit,
) {
    val key = pref.key
    val enabled = pref.isEnabled
    val title = pref.title?.toString().orEmpty()

    when (pref) {
        is TwoStatePreference -> {
            val checked = key?.let { prefs.getBoolean(it, pref.defaultValueForUi() as? Boolean ?: false) } ?: pref.isChecked
            val summary = (if (checked) pref.summaryOn else pref.summaryOff) ?: pref.summary
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = summary?.let { { Text(it.toString()) } },
                trailingContent = {
                    Switch(
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = { new ->
                            if (key != null && safeChange(pref, new)) {
                                prefs.edit().putBoolean(key, new).apply()
                                onChanged()
                            }
                        },
                    )
                },
                modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
            )
        }

        else -> {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = summaryFor(pref, prefs)?.let { { Text(it) } },
                modifier = Modifier
                    .alpha(if (enabled) 1f else 0.5f)
                    .clickable(enabled = enabled) {
                        when (pref) {
                            is EditTextPreference, is ListPreference, is MultiSelectListPreference -> onEdit(pref)
                            else -> runCatching { pref.onPreferenceClickListener?.onPreferenceClick(pref) }
                        }
                    },
            )
        }
    }
}

private fun summaryFor(pref: Preference, prefs: android.content.SharedPreferences): String? {
    val key = pref.key
    val summary = pref.summary?.toString()
    return when (pref) {
        is ListPreference -> {
            val value = key?.let { prefs.getString(it, pref.defaultValueForUi() as? String) } ?: pref.value
            val entry = pref.findIndexOfValue(value).takeIf { it >= 0 }?.let { pref.entries.getOrNull(it)?.toString() }
            when {
                summary == null -> entry
                summary.contains("%s") -> summary.replace("%s", entry.orEmpty())
                else -> summary
            }
        }
        is EditTextPreference -> {
            val value = key?.let { prefs.getString(it, pref.defaultValueForUi() as? String) } ?: pref.text
            summary ?: value
        }
        else -> summary
    }
}

@Composable
private fun PreferenceDialog(
    pref: Preference,
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val key = pref.key ?: return onDismiss()
    val title = (pref as? androidx.preference.DialogPreference)?.dialogTitle ?: pref.title

    when (pref) {
        is EditTextPreference -> {
            var text by remember { mutableStateOf(prefs.getString(key, pref.defaultValueForUi() as? String) ?: pref.text.orEmpty()) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title?.toString().orEmpty()) },
                text = {
                    Column {
                        pref.dialogMessage?.let { Text(it.toString()); Spacer(Modifier.height(8.dp)) }
                        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (safeChange(pref, text)) {
                            prefs.edit().putString(key, text).apply()
                            onChanged()
                        } else {
                            onDismiss()
                        }
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }

        is ListPreference -> {
            val current = prefs.getString(key, pref.defaultValueForUi() as? String) ?: pref.value
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title?.toString().orEmpty()) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        pref.entries.forEachIndexed { i, entry ->
                            val value = pref.entryValues.getOrNull(i)?.toString() ?: return@forEachIndexed
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (safeChange(pref, value)) {
                                        prefs.edit().putString(key, value).apply()
                                        onChanged()
                                    } else {
                                        onDismiss()
                                    }
                                },
                            ) {
                                RadioButton(selected = value == current, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(entry.toString())
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }

        is MultiSelectListPreference -> {
            @Suppress("UNCHECKED_CAST")
            val initial = prefs.getStringSet(key, pref.defaultValueForUi() as? Set<String>) ?: pref.values
            var selected by remember { mutableStateOf(initial) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title?.toString().orEmpty()) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        pref.entries.forEachIndexed { i, entry ->
                            val value = pref.entryValues.getOrNull(i)?.toString() ?: return@forEachIndexed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = value in selected,
                                    onCheckedChange = { on -> selected = if (on) selected + value else selected - value },
                                )
                                Text(entry.toString())
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (safeChange(pref, selected)) {
                            prefs.edit().putStringSet(key, selected).apply()
                            onChanged()
                        } else {
                            onDismiss()
                        }
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }

        else -> onDismiss()
    }
}

/** Runs the extension's change listener; if it throws or says no, the value isn't saved. */
private fun safeChange(pref: Preference, newValue: Any?): Boolean =
    runCatching { pref.callChangeListener(newValue) }
        .onFailure { it.printStackTrace() }
        .getOrDefault(false)
