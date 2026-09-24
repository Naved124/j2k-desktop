package dev.naved.j2kdesktop.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Listing {
    data object Popular : Listing
    data object Latest : Listing
    data class Search(val query: String) : Listing
}

/** One per source: its current list, paging and scroll position. */
class BrowseModel(
    val source: CatalogueSource,
    private val scope: CoroutineScope,
) {
    val gridState = LazyGridState()

    var listing by mutableStateOf<Listing>(Listing.Popular)
        private set
    var mangas by mutableStateOf<List<SManga>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasNextPage by mutableStateOf(true)
        private set

    private var currentPage = 0
    private var generation = 0 // bumps on every switch, so late results from the old list get ignored
    private var job: Job? = null

    fun switchListing(new: Listing) {
        if (new == listing && mangas.isNotEmpty()) return // keep what's already loaded
        generation++
        job?.cancel()
        listing = new
        mangas = emptyList()
        currentPage = 0
        hasNextPage = true
        error = null
        isLoading = false
        scope.launch { gridState.scrollToItem(0) }
    }

    fun loadNextPage() {
        if (isLoading || !hasNextPage) return
        val gen = generation
        val page = currentPage + 1
        val current = listing
        isLoading = true
        error = null
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when (current) {
                        Listing.Popular -> source.getPopularManga(page)
                        Listing.Latest -> source.getLatestUpdates(page)
                        is Listing.Search -> source.getSearchManga(page, current.query, source.getFilterList())
                    }
                }
                if (gen != generation) return@launch
                currentPage = page
                mangas = (mangas + result.mangas).distinctBy { it.url }
                hasNextPage = result.hasNextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                if (gen == generation) error = e.message ?: e.toString()
            } finally {
                if (gen == generation) isLoading = false
            }
        }
    }
}
