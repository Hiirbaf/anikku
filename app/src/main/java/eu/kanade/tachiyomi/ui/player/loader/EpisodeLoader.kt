package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.domain.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Loader used to retrieve the hosters for a given episode.
 */
class EpisodeLoader {
    companion object {

        /**
         * Returns a list of hosters of an [episode] based on the type of [source] used.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         */
        suspend fun getHosters(
            episode: Episode,
            anime: Anime,
            source: Source,
            sourceManager: SourceManager? = null,
            mergedReferences: List<MergedMangaReference> = emptyList(),
            mergedManga: Map<Long, Anime> = emptyMap(),
        ): List<Hoster> {
            val isDownloaded = isDownload(episode, anime)
            return when {
                // SY -->
                source is MergedSource -> {
                    val mangaReference = mergedReferences.firstOrNull {
                        it.animeId == episode.animeId
                    } ?: error("Merge reference null")
                    val actualSource = sourceManager?.get(mangaReference.animeSourceId)
                        ?: error("Source ${mangaReference.animeSourceId} was null")
                    val manga = mergedManga[episode.animeId] ?: error("Manga for merged episode was null")
                    val isMergedMangaDownloaded = isDownload(episode, manga)
                    when {
                        isMergedMangaDownloaded -> getHostersOnDownloaded(
                            episode = episode,
                            anime = manga,
                            source = actualSource,
                        )
                        actualSource is HttpSource -> getHostersOnHttp(episode, actualSource)
                        actualSource is LocalSource -> getHostersOnLocal(episode)
                        else -> error("Source not found")
                    }
                }
                // SY <--
                isDownloaded -> getHostersOnDownloaded(episode, anime, source)
                source is HttpSource -> getHostersOnHttp(episode, source)
                source is LocalSource -> getHostersOnLocal(episode)
                else -> error("source not supported")
            }
        }

        /**
         * Returns true if the given [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         */
        fun isDownload(episode: Episode, anime: Anime): Boolean {
            val downloadManager: DownloadManager = Injekt.get()
            return downloadManager.isEpisodeDownloaded(
                episodeName = episode.name,
                episodeScanlator = episode.scanlator,
                // SY -->
                animeTitle = anime.ogTitle,
                // SY <--
                sourceId = anime.source,
                skipCache = true,
            )
        }

        /**
         * Returns a list of hosters when the [episode] is online.
         *
         * @param episode the episode being parsed.
         * @param source the online source of the episode.
         */
        private suspend fun getHostersOnHttp(episode: Episode, source: HttpSource): List<Hoster> {
            // TODO(1.6): Remove else block when dropping support for ext lib <1.6
            return if (source.javaClass.declaredMethods.any { it.name == "getHosterList" }) {
                source.getHosterList(episode.toSEpisode())
            } else {
                source.getVideoList(episode.toSEpisode()).toHosterList()
            }
        }

        /**
         * Returns the hoster when the [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         */
        private fun getHostersOnDownloaded(
            episode: Episode,
            anime: Anime,
            source: Source,
        ): List<Hoster> {
            val downloadManager: DownloadManager = Injekt.get()
            return try {
                val video = downloadManager.buildVideo(source, anime, episode)
                listOf(video).toHosterList()
            } catch (e: Throwable) {
                emptyList()
            }
        }

        /**
         * Returns the hoster when the [episode] is from local source.
         *
         * @param episode the episode being parsed.
         */
        private fun getHostersOnLocal(
            episode: Episode,
        ): List<Hoster> {
            return try {
                val (animeDirName, episodeName) = episode.url.split('/', limit = 2)
                val fileSystem: LocalSourceFileSystem = Injekt.get()
                val videoFile = fileSystem.getBaseDirectory()
                    ?.findFile(animeDirName)
                    ?.findFile(episodeName)
                val videoUri = videoFile!!.uri

                val video = Video(
                    videoUri.toString(),
                    "Local source: ${episode.url}",
                )
                listOf(video).toHosterList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Returns a list of videos of a [hoster] based on the type of [source] used.
         * Note that for every type of episode except non-downloaded online, `videoList`
         * will be set to null.
         *
         * @param source the source of the anime.
         * @param hoster the hoster.
         */
        private suspend fun getVideos(source: AnimeSource, hoster: Hoster): List<Video> {
            return when {
                hoster.videoList != null && source is AnimeHttpSource -> hoster.videoList!!.parseVideoUrls(source)
                hoster.videoList != null -> hoster.videoList!!
                source is AnimeHttpSource -> getVideosOnHttp(source, hoster)
                else -> error("source not supported")
            }
        }

        /**
         * Returns a list of hosters when the [Episode] is online.
         *
         * @param source the online source of the episode.
         * @param hoster the hoster.
         */
        private suspend fun getVideosOnHttp(source: AnimeHttpSource, hoster: Hoster): List<Video> {
            return source.getVideoList(hoster).parseVideoUrls(source)
        }

        // TODO(1.6): Remove after ext lib bump
        private suspend fun List<Video>.parseVideoUrls(source: AnimeHttpSource): List<Video> {
            return this.map { video ->
                if (video.videoUrl != "null") return@map video

                val newVideoUrl = source.getVideoUrl(video)
                video.copy(videoUrl = newVideoUrl)
            }
        }

        suspend fun loadHosterVideos(source: AnimeSource, hoster: Hoster): HosterState {
            return try {
                val videos = getVideos(source, hoster)
                HosterState.Ready(hoster.hosterName, videos, List(videos.size) { Video.State.QUEUE })
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }

                HosterState.Error(hoster.hosterName)
            }
        }
    }
}
