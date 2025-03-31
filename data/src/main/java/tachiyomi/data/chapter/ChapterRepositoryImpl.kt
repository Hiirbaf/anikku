package tachiyomi.data.chapter

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

class ChapterRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { episode ->
                    episodesQueries.insert(
                        episode.animeId,
                        episode.url,
                        episode.name,
                        episode.scanlator,
                        episode.seen,
                        episode.bookmark,
                        // AM (FILLERMARK) -->
                        episode.fillermark,
                        // <-- AM (FILLERMARK)
                        episode.lastSecondSeen,
                        episode.totalSeconds,
                        episode.episodeNumber,
                        episode.sourceOrder,
                        episode.dateFetch,
                        episode.dateUpload,
                        episode.version,
                    )
                    val lastInsertId = episodesQueries.selectLastInsertedRowId().executeAsOne()
                    episode.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        handler.await(inTransaction = true) {
            chapterUpdates.forEach { episodeUpdate ->
                episodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = episodeUpdate.fillermark,
                    // <-- AM (FILLERMARK)
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    totalSeconds = episodeUpdate.totalSeconds,
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    episodeId = episodeUpdate.id,
                    version = episodeUpdate.version,
                    isSyncing = 0,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { episodesQueries.removeEpisodesWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getEpisodesByAnimeId(mangaId, applyScanlatorFilter.toLong(), ChapterMapper::mapChapter)
        }
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByAnimeId(mangaId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByAnimeId(mangaId) { it.orEmpty() }
        }
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getBookmarkedEpisodesByAnimeId(
                mangaId,
                ChapterMapper::mapChapter,
            )
        }
    }

    // AM (FILLERMARK) -->
    override suspend fun getFillermarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return handler.awaitList { episodesQueries.getFillermarkedEpisodesByAnimeId(mangaId, ChapterMapper::mapChapter) }
    }
    // <-- AM (FILLERMARK)

    override suspend fun getChapterById(id: Long): Chapter? {
        return handler.awaitOneOrNull { episodesQueries.getEpisodeById(id, ChapterMapper::mapChapter) }
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return handler.subscribeToList {
            episodesQueries.getEpisodesByAnimeId(mangaId, applyScanlatorFilter.toLong(), ChapterMapper::mapChapter)
        }
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return handler.awaitOneOrNull {
            episodesQueries.getEpisodeByUrlAndAnimeId(
                url,
                mangaId,
                ChapterMapper::mapChapter,
            )
        }
    }

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return handler.awaitList { episodesQueries.getEpisodeByUrl(url, ChapterMapper::mapChapter) }
    }

    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return handler.awaitList {
            episodesQueries.getMergedEpisodesByAnimeId(
                mangaId,
                applyScanlatorFilter.toLong(),
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyScanlatorFilter: Boolean,
    ): Flow<List<Chapter>> {
        return handler.subscribeToList {
            episodesQueries.getMergedEpisodesByAnimeId(
                mangaId,
                applyScanlatorFilter.toLong(),
                ChapterMapper::mapChapter,
            )
        }
    }

    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }
        }
    }
    // SY <--
}
