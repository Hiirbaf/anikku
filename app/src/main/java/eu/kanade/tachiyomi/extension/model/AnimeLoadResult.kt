package eu.kanade.tachiyomi.extension.model

sealed interface AnimeLoadResult {
    data class Success(val extension: AnimeExtension.Installed) : AnimeLoadResult
    data class Untrusted(val extension: AnimeExtension.Untrusted) : AnimeLoadResult
    data object Error : AnimeLoadResult
}
