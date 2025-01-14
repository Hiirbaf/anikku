package eu.kanade.tachiyomi.ui.browse.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.AnimeSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeSearchScreenModel(
    val animeId: Long,
    initialExtensionFilter: String = "",
    getAnime: GetAnime = Injekt.get(),
) : AnimeSearchScreenModel() {

    init {
        extensionFilter = initialExtensionFilter
        screenModelScope.launch {
            val anime = getAnime.await(animeId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = anime.source,
                    searchQuery = anime.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
