package tachiyomi.domain.track.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class GetTracks(
    private val trackRepository: TrackRepository,
) {

    suspend fun awaitOne(id: Long): Track? {
        return try {
            trackRepository.getTrackById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    // SY -->
    suspend fun await(): List<Track> {
        return try {
            trackRepository.getTracks()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun await(mangaIds: List<Long>): Map<Long, List<Track>> {
        return try {
            trackRepository.getTracksByAnimeIds(mangaIds)
                .groupBy { it.animeId }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }
    // SY <--

    suspend fun await(mangaId: Long): List<Track> {
        return try {
            trackRepository.getTracksByAnimeId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    fun subscribe(mangaId: Long): Flow<List<Track>> {
        return trackRepository.getTracksByAnimeIdAsFlow(mangaId)
    }
}
