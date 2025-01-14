package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.model.AnimeSource
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.repository.AnimeSourceRepository
import tachiyomi.source.local.LocalSource

class GetEnabledSources(
    private val repository: AnimeSourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<AnimeSource>> {
        return combine(
            preferences.pinnedAnimeSources().changes(),
            preferences.enabledLanguages().changes(),
            preferences.disabledAnimeSources().changes(),
            preferences.lastUsedAnimeSource().changes(),
            repository.getAnimeSources(),
        ) { pinnedSourceIds, enabledLanguages, disabledSources, lastUsedSource, sources ->
            sources
                .filter { it.lang in enabledLanguages || it.id == LocalSource.ID }
                .filterNot { it.id.toString() in disabledSources }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val source = it.copy(pin = flag)
                    val toFlatten = mutableListOf(source)
                    if (source.id == lastUsedSource) {
                        toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
