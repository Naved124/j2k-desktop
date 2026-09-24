package eu.kanade.tachiyomi.source

/** A factory for creating sources at runtime. */
interface SourceFactory {
    fun createSources(): List<Source>
}