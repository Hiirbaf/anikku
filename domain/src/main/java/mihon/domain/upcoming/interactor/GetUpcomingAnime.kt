package mihon.domain.upcoming.interactor

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId

class GetUpcomingAnime(
    private val mangaRepository: MangaRepository,
) {
    // KMK -->
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    // KMK <--

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaRepository.getUpcomingAnime(includedStatuses)
    }

    // KMK -->
    suspend fun updatingMangas(): List<Manga> {
        val libraryManga = getLibraryManga.await()

        val categoriesToUpdate = libraryPreferences.updateCategories().get().map(String::toLong)
        val includedManga = if (categoriesToUpdate.isNotEmpty()) {
            libraryManga.filter { it.category in categoriesToUpdate }
        } else {
            libraryManga
        }

        val categoriesToExclude = libraryPreferences.updateCategoriesExclude().get().map { it.toLong() }
        val excludedMangaIds = if (categoriesToExclude.isNotEmpty()) {
            libraryManga.filter { it.category in categoriesToExclude }.map { it.manga.id }
        } else {
            emptyList()
        }

        val listToUpdate = includedManga
            .filterNot { it.manga.id in excludedMangaIds }

        val restrictions = libraryPreferences.autoUpdateAnimeRestrictions().get()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

        return listToUpdate
            .distinctBy { it.manga.id }
            .filter {
                when {
                    it.manga.updateStrategy != UpdateStrategy.ALWAYS_UPDATE -> false

                    ANIME_NON_COMPLETED in restrictions && it.manga.status.toInt() == SManga.COMPLETED -> false

                    ANIME_HAS_UNSEEN in restrictions && it.unseenCount != 0L -> false

                    ANIME_NON_SEEN in restrictions && it.totalEpisodes > 0L && !it.hasStarted -> false

                    ANIME_OUTSIDE_RELEASE_PERIOD in restrictions && it.manga.nextUpdate < today -> false

                    else -> true
                }
            }
            .map { it.manga }
            .sortedBy { it.nextUpdate }
    }
    // KMK <--
}
