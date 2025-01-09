package tachiyomi.domain.track.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.model.AnimeTrack

interface AnimeTrackRepository {

    suspend fun getTrackByAnimeId(id: Long): AnimeTrack?

    // SY -->
    suspend fun getAnimeTracks(): List<AnimeTrack>

    suspend fun getTracksByAnimeIds(animeIds: List<Long>): List<AnimeTrack>
    // SY <--

    suspend fun getTracksByAnimeId(animeId: Long): List<AnimeTrack>

    fun getAnimeTracksAsFlow(): Flow<List<AnimeTrack>>

    fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeTrack>>

    suspend fun delete(animeId: Long, trackerId: Long)

    suspend fun insertAnime(track: AnimeTrack)

    suspend fun insertAllAnime(tracks: List<AnimeTrack>)
}
