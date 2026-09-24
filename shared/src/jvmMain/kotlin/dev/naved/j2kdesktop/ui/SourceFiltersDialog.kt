package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * The source's own filters (genres, status, type, sort…), like J2K's filter sheet.
 * Filter objects keep their own state, so [version] is bumped to redraw after each change.
 */
@Composable
fun SourceFiltersDialog(
    filters: FilterList,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var version by remember { mutableStateOf(0) }
    val changed: () -> Unit = { version++ }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            @Suppress("UNUSED_EXPRESSION")
            version // redraw when a filter changes
            Column(Modifier.width(720.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                if (filters.isEmpty()) {
                    Text("This source has no filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filters.forEach { FilterItem(it, changed, version) }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterItem(filter: Filter<*>, changed: () -> Unit, version: Int) {
    when (filter) {
        is Filter.Header -> Text(
            filter.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        is Filter.Separator -> HorizontalDivider(Modifier.padding(vertical = 8.dp))

        is Filter.Select<*> -> Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(filter.name, modifier = Modifier.weight(1f))
            Box {
                var open by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { open = true }) {
                    Text(filter.values.getOrNull(filter.state)?.toString() ?: "—")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    filter.values.forEachIndexed { i, value ->
                        DropdownMenuItem(
                            leadingIcon = { Text(if (i == filter.state) "●" else "○") },
                            text = { Text(value.toString()) },
                            onClick = {
                                filter.state = i
                                open = false
                                changed()
                            },
                        )
                    }
                }
            }
        }

        is Filter.Text -> OutlinedTextField(
            value = filter.state,
            onValueChange = {
                filter.state = it
                changed()
            },
            label = { Text(filter.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        is Filter.CheckBox -> Row(
            Modifier.fillMaxWidth().clickable {
                filter.state = !filter.state
                changed()
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = filter.state, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(filter.name)
        }

        is Filter.TriState -> TriStateChip(filter, changed, version)

        is Filter.Sort -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(filter.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filter.values.forEachIndexed { i, label ->
                    val selection = filter.state
                    val selected = selection?.index == i
                    FilterChip(
                        selected = selected,
                        onClick = {
                            // Tap again to flip the direction
                            filter.state = Filter.Sort.Selection(i, if (selected) !selection!!.ascending else false)
                            changed()
                        },
                        label = { Text(label + if (selected) (if (selection!!.ascending) "  ↑" else "  ↓") else "") },
                    )
                }
            }
        }

        is Filter.Group<*> -> {
            var expanded by remember { mutableStateOf(false) }
            val children = filter.state.filterIsInstance<Filter<*>>()
            val active = children.count { child ->
                when (child) {
                    is Filter.CheckBox -> child.state
                    is Filter.TriState -> !child.isIgnored()
                    else -> false
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(filter.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (active > 0) Text("$active selected  ", color = MaterialTheme.colorScheme.primary)
                Text(if (expanded) "▲" else "▼")
            }
            if (expanded) {
                // Checkboxes and tri-states (e.g. genres) as chips; anything else as a normal row
                val chips = children.filter { it is Filter.CheckBox || it is Filter.TriState }
                if (chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        chips.forEach { child ->
                            when (child) {
                                is Filter.CheckBox -> FilterChip(
                                    selected = child.state,
                                    onClick = {
                                        child.state = !child.state
                                        changed()
                                    },
                                    label = { Text(child.name) },
                                )
                                is Filter.TriState -> TriStateChip(child, changed, version)
                                else -> {}
                            }
                        }
                    }
                }
                children.filter { it !is Filter.CheckBox && it !is Filter.TriState }.forEach { FilterItem(it, changed, version) }
            }
        }
    }
}

/** Tap to cycle: ignored → include (✓) → exclude (✗). */
@Composable
private fun TriStateChip(filter: Filter.TriState, changed: () -> Unit, version: Int) {
    @Suppress("UNUSED_EXPRESSION")
    version // redraw after changes (the filter object itself stays the same)
    FilterChip(
        selected = !filter.isIgnored(),
        onClick = {
            filter.state = when (filter.state) {
                Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                else -> Filter.TriState.STATE_IGNORE
            }
            changed()
        },
        label = {
            Text(
                when {
                    filter.isIncluded() -> "✓ ${filter.name}"
                    filter.isExcluded() -> "✗ ${filter.name}"
                    else -> filter.name
                },
                color = if (filter.isExcluded()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}
