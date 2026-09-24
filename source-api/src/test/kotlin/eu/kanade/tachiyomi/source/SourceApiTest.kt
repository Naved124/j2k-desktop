package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import rx.Observable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SourceApiTest {
    private class OldStyleSource : CatalogueSource {
        override val id = 1L
        override val name = "Old style"
        override val lang = "en"
        override val supportsLatest = false
        override fun getFilterList() = FilterList()

        override fun fetchPopularManga(page: Int): Observable<MangasPage> {
            val manga = SManga.create().apply {
                url = "/manga/1"
                title = "Test Manga"
            }
            return Observable.just(MangasPage(listOf(manga), hasNextPage = false))
        }
    }

    @Test
    fun rxSourceWorksThroughSuspendApi() = runBlocking {
        val page = OldStyleSource().getPopularManga(1)
        assertEquals("Test Manga", page.mangas.single().title)
        assertFalse(page.hasNextPage)
    }
}