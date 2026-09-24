package eu.kanade.tachiyomi.source.model

/** How library updates treat a manga. @since extensions-lib 1.4 */
enum class UpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}