package tachiyomi.domain.source.interactor

import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.source.repository.AnimeSourcePagingSourceType
import tachiyomi.domain.source.repository.SourceRepository

class GetRemoteAnime(
    private val repository: SourceRepository,
) {

    fun subscribe(sourceId: Long, query: String, filterList: FilterList): AnimeSourcePagingSourceType {
        return when (query) {
            QUERY_POPULAR -> repository.getPopularAnime(sourceId)
            QUERY_LATEST -> repository.getLatestAnime(sourceId)
            else -> repository.searchAnime(sourceId, query, filterList)
        }
    }

    companion object {
        const val QUERY_POPULAR = "eu.kanade.domain.source.anime.interactor.POPULAR"
        const val QUERY_LATEST = "eu.kanade.domain.source.anime.interactor.LATEST"
    }
}
