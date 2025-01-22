package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

@Suppress("OverridingDeprecatedMember")
class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime =
        throw AnimeSourceNotInstalledException()

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
        throw AnimeSourceNotInstalledException()

    override suspend fun getVideoList(episode: SEpisode): List<Video> =
        throw AnimeSourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: Source): StubSource {
            return StubSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}
class AnimeSourceNotInstalledException : Exception()
