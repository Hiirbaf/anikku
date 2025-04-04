package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.compose.ui.util.fastAny
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAddAnimeResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAnime
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMMetadata
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserListEntry
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class ShikimoriApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: Track, userId: String): Track {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Anime")
                        put("episodes", track.last_episode_seen.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMAddAnimeResponse>()
                    .let {
                        // save id of the entry for possible future delete request
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibAnime(track: Track, userId: String): Track = addLibAnime(track, userId)

    suspend fun deleteLibAnime(track: DomainTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun searchAnime(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$API_URL/animes".toUri().buildUpon()
                .appendQueryParameter("order", "popularity")
                .appendQueryParameter("search", search)
                .appendQueryParameter("limit", "20")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMAnime>>()
                    .map { it.toAnimeTrack(trackId) }
            }
        }
    }

    suspend fun findLibAnime(track: Track, userId: String): Track? {
        return withIOContext {
            val urlAnimes = "$API_URL/animes".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .build()
            val anime = with(json) {
                authClient.newCall(GET(urlAnimes.toString()))
                    .awaitSuccess()
                    .parseAs<SMAnime>()
            }

            val url = "$API_URL/v2/user_rates".toUri().buildUpon()
                .appendQueryParameter("user_id", userId)
                .appendQueryParameter("target_id", track.remote_id.toString())
                .appendQueryParameter("target_type", "Anime")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMUserListEntry>>()
                    .let { entries ->
                        if (entries.size > 1) {
                            throw Exception("Too many manga in response")
                        }
                        entries
                            .map { it.toAnimeTrack(trackId, anime) }
                            .firstOrNull()
                    }
            }
        }
    }

    suspend fun getCurrentUser(): Int {
        return with(json) {
            authClient.newCall(GET("$API_URL/users/whoami"))
                .awaitSuccess()
                .parseAs<SMUser>()
                .id
        }
    }

    suspend fun getAnimeMetadata(track: DomainTrack): TrackAnimeMetadata {
        return withIOContext {
            val query = """
                |query(${'$'}ids: String!) {
                    |animes(ids: ${'$'}ids) {
                        |id
                        |name
                        |description
                        |poster {
                            |originalUrl
                        |}
                        |studios {
                            |name
                        |}
                        |personRoles {
                            |person {
                                |name
                            |}
                            |rolesEn
                        |}
                    |}
                |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("ids", "${track.remoteId}")
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        "https://shikimori.one/api/graphql",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMMetadata>()
                    .let { dto ->
                        val anime = dto.data.animes.firstOrNull() ?: throw Exception("Could not get metadata from Shikimori")
                        TrackAnimeMetadata(
                            remoteId = anime.id.toLong(),
                            title = anime.name,
                            thumbnailUrl = anime.poster.originalUrl,
                            description = anime.description,
                            authors = (
                                anime.studios
                                    .map { it.name.trim() } +
                                    anime.personRoles
                                        .filter { roles ->
                                            roles.rolesEn.fastAny {
                                                it.contains("Story", true) ||
                                                    it.contains("Creator", true) ||
                                                    it.contains("Script", true) ||
                                                    it.contains("Writer", true)
                                            }
                                        }
                                        .map { it.person.name }
                                )
                                .joinToString()
                                .ifEmpty { null },
                            artists = anime.personRoles
                                .filter { roles ->
                                    roles.rolesEn.fastAny {
                                        it.contains("Producer", true) ||
                                            it.contains("Director", true) ||
                                            it.contains("Animation", true) ||
                                            it.contains("Art", true) ||
                                            it.contains("Design", true) ||
                                            it.contains("Music", true) ||
                                            it.contains("Song", true)
                                    }
                                }
                                .joinToString { it.person.name }
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    suspend fun accessToken(code: String): SMOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    companion object {
        const val BASE_URL = "https://shikimori.one"
        private const val API_URL = "$BASE_URL/api"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "anikku://shikimori-auth"

        private const val CLIENT_ID = "KEvIU428nCtbGJ96T_rSYbRW6qkRwN-z95AJu9MyHd8"
        private const val CLIENT_SECRET = "buKNsI8mRGS_KReIn_FrTstkrx0fn48ls_b19GAxe6I"

        fun authUrl(): Uri = LOGIN_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("response_type", "code")
            .build()

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .build(),
        )
    }
}
