package dev.naved.j2kdesktop.source

import androidx.compose.runtime.mutableStateListOf
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source

/**
 * Every source the app knows about (J2K's SourceManager).
 * Built-ins are registered here; installed extensions will register in phase 7.
 * It's Compose state, so the Browse list updates by itself when sources change.
 */
object SourceManager {
    private val _sources = mutableStateListOf<Source>()
    val sources: List<Source> get() = _sources

    init {
        register(LocalSource())
        register(MangaDex())
    }

    fun register(source: Source) {
        if (_sources.none { it.id == source.id }) _sources.add(source)
    }

    fun unregister(id: Long) {
        _sources.removeAll { it.id == id }
    }

    fun get(id: Long): Source? = _sources.firstOrNull { it.id == id }

    val catalogueSources: List<CatalogueSource>
        get() = _sources.filterIsInstance<CatalogueSource>()
}
