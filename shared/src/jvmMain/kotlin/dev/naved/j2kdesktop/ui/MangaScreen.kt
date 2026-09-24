package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaScreen(source: Source, manga: SManga, onBack: () -> Unit) {
    var details by remember(manga.url) { mutableStateOf(manga) }
    var chapters by remember(manga.url) { mutableStateOf<List<SChapter>?>(null) }
    var error by remember(manga.url) { mutableStateOf<String?>(null) }

    LaunchedEffect(manga.url) {
        try {
            coroutineScope {
                launch { details = withContext(Dispatchers.IO) { source.getMangaDetails(manga) } }
                launch { chapters = withContext(Dispatchers.IO) { source.getChapterList(manga) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace() // shows up in the terminal
            error = e.message ?: e.toString()
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.height(8.dp))

            Row {
                AsyncImage(
                    model = rememberImageRequest(
                        details.thumbnail_url,
                        (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.headers,
                    ),
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(details.title, style = MaterialTheme.typography.headlineSmall)
                    details.author?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${details.statusText()} • ${source.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            details.description?.let { description ->
                var expanded by remember { mutableStateOf(false) }
                Spacer(Modifier.height(16.dp))
                Text(
                    description,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }

            val genres = details.genre?.split(", ")?.filter { it.isNotBlank() }.orEmpty()
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    error != null -> "Chapters"
                    chapters == null -> "Loading chapters…"
                    chapters!!.isEmpty() -> "No readable chapters (they may only be on official sites)"
                    else -> "${chapters!!.size} chapters"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }

        error?.let {
            item { Text("Couldn't load: $it", color = MaterialTheme.colorScheme.error) }
        }

        items(chapters.orEmpty(), key = { it.url }) { chapter ->
            ListItem(
                headlineContent = { Text(chapter.name) },
                supportingContent = {
                    Text(listOfNotNull(chapter.date_upload.toDateString(), chapter.scanlator).joinToString(" • "))
                },
            )
        }
    }
}

internal fun SManga.statusText() = when (status) {
    SManga.ONGOING -> "Ongoing"
    SManga.COMPLETED -> "Completed"
    SManga.ON_HIATUS -> "On hiatus"
    SManga.CANCELLED -> "Cancelled"
    else -> "Unknown"
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun Long.toDateString(): String? =
    if (this <= 0) null else Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFormat)