package eu.kanade.domain.extension.anime.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.plusAssign

class CreateAnimeExtensionRepo(private val preferences: SourcePreferences) {

    fun await(name: String): Result {
        // Do not allow invalid formats
        if (!name.matches(repoRegex) || name.startsWith(OFFICIAL_ANIYOMI_REPO_BASE_URL)) {
            return Result.InvalidUrl
        }

        preferences.animeExtensionRepos() += name.removeSuffix("/index.min.json")

        return Result.Success
    }

    sealed interface Result {
        data object InvalidUrl : Result
        data object Success : Result
    }
}

const val OFFICIAL_ANIYOMI_REPO_BASE_URL = "https://raw.githubusercontent.com/Dark25/animmetailv2-extensions/main"
private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()
