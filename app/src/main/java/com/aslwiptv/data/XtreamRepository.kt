package com.aslwiptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Xtream Codes API client. Credentials and stream URLs are never written to logs. */
class XtreamRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    suspend fun loadLive(credentials: XtreamCredentials): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            fetchLive(credentials).also { check(it.isNotEmpty()) { "Xtream non ha restituito canali Live TV." } }
        }
    }

    /** Loads the three Xtream libraries without failing Live TV when VOD or series are unavailable. */
    suspend fun loadCatalog(credentials: XtreamCredentials): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val live = fetchLive(credentials)
            val movies = runCatching { fetchMovies(credentials) }.getOrDefault(emptyList())
            val series = runCatching { fetchSeries(credentials) }.getOrDefault(emptyList())
            (live + movies + series).also { check(it.isNotEmpty()) { "Il provider non ha restituito canali, film o serie." } }
        }
    }

    suspend fun loadSeriesEpisodes(credentials: XtreamCredentials, seriesId: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            require(seriesId.isNotBlank()) { "Identificativo della serie non valido." }
            val root = requestObject(credentials, "get_series_info", "series_id" to seriesId)
            val episodes = root.optJSONObject("episodes") ?: error("Il provider non ha restituito gli episodi di questa serie.")
            val base = validatedBase(credentials)
            buildList {
                val seasons = episodes.keys()
                while (seasons.hasNext()) {
                    val season = seasons.next()
                    val seasonEpisodes = episodes.optJSONArray(season) ?: continue
                    for (i in 0 until seasonEpisodes.length()) {
                        val episode = seasonEpisodes.optJSONObject(i) ?: continue
                        val episodeId = episode.optString("id").takeUnless { it.isBlank() || it == "null" } ?: continue
                        val title = episode.optString("title").takeUnless { it.isBlank() || it == "null" }
                            ?: "Episodio ${episode.optString("episode_num", (i + 1).toString())}"
                        val ext = safeExtension(episode.optString("container_extension"), "mp4")
                        add(
                            MediaItem(
                                id = "episode:$seriesId:$episodeId",
                                title = title,
                                streamUrl = buildStreamUrl(base, "series", credentials, "$episodeId.$ext").toString(),
                                category = "Stagione $season",
                                logoUrl = episode.optNullableString("movie_image"),
                                kind = MediaKind.SERIES
                            )
                        )
                    }
                }
            }.also { check(it.isNotEmpty()) { "La serie non contiene episodi riproducibili." } }
        }
    }

    private fun fetchLive(credentials: XtreamCredentials): List<MediaItem> {
        val base = validatedBase(credentials)
        val array = requestArray(credentials, "get_live_streams")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val streamId = obj.optString("stream_id").takeUnless { it.isBlank() || it == "null" } ?: continue
                val name = obj.optString("name").takeUnless { it.isBlank() || it == "null" } ?: "Canale $streamId"
                val ext = safeExtension(obj.optString("container_extension"), "ts")
                add(
                    MediaItem(
                        id = "live:$streamId",
                        title = name,
                        streamUrl = buildStreamUrl(base, "live", credentials, "$streamId.$ext").toString(),
                        category = obj.optNullableString("category_name") ?: "Senza categoria",
                        logoUrl = obj.optNullableString("stream_icon"),
                        kind = MediaKind.LIVE,
                        epgId = obj.optNullableString("epg_channel_id")
                    )
                )
            }
        }
    }

    private fun fetchMovies(credentials: XtreamCredentials): List<MediaItem> {
        val base = validatedBase(credentials)
        val array = requestArray(credentials, "get_vod_streams")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val streamId = obj.optString("stream_id").takeUnless { it.isBlank() || it == "null" } ?: continue
                val name = obj.optString("name").takeUnless { it.isBlank() || it == "null" } ?: "Film $streamId"
                val ext = safeExtension(obj.optString("container_extension"), "mp4")
                add(
                    MediaItem(
                        id = "movie:$streamId",
                        title = name,
                        streamUrl = buildStreamUrl(base, "movie", credentials, "$streamId.$ext").toString(),
                        category = obj.optNullableString("category_name") ?: "Senza categoria",
                        logoUrl = obj.optNullableString("stream_icon"),
                        kind = MediaKind.MOVIE
                    )
                )
            }
        }
    }

    private fun fetchSeries(credentials: XtreamCredentials): List<MediaItem> {
        val array = requestArray(credentials, "get_series")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val seriesId = obj.optString("series_id").takeUnless { it.isBlank() || it == "null" } ?: continue
                val name = obj.optString("name").takeUnless { it.isBlank() || it == "null" } ?: "Serie $seriesId"
                add(
                    MediaItem(
                        id = "series:$seriesId",
                        title = name,
                        streamUrl = "",
                        category = obj.optNullableString("category_name") ?: "Senza categoria",
                        logoUrl = obj.optNullableString("cover"),
                        kind = MediaKind.SERIES,
                        seriesId = seriesId
                    )
                )
            }
        }
    }

    private fun requestArray(credentials: XtreamCredentials, action: String): JSONArray {
        val body = execute(credentials, action)
        check(body.trimStart().startsWith("[")) {
            "Risposta Xtream non valida: controlla URL, username e password."
        }
        return JSONArray(body)
    }

    private fun requestObject(credentials: XtreamCredentials, action: String, vararg parameters: Pair<String, String>): JSONObject {
        val body = execute(credentials, action, *parameters)
        check(body.trimStart().startsWith("{")) { "Risposta Xtream non valida per il dettaglio della serie." }
        return JSONObject(body)
    }

    private fun execute(credentials: XtreamCredentials, action: String, vararg parameters: Pair<String, String>): String {
        val base = validatedBase(credentials)
        val builder = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .addQueryParameter("action", action)
        parameters.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        val request = Request.Builder().url(builder.build()).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Server non raggiungibile (${response.code})." }
            return response.body?.string().orEmpty()
        }
    }

    private fun validatedBase(credentials: XtreamCredentials): HttpUrl {
        val base = credentials.serverUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: error("Inserisci l’URL del server Xtream, per esempio http://server:porta.")
        require(base.scheme == "http" || base.scheme == "https") { "Il server deve usare http:// oppure https://." }
        require(base.encodedQuery == null && base.encodedFragment == null) { "L’URL del server non deve contenere query o frammenti." }
        require(credentials.username.isNotBlank() && credentials.password.isNotBlank()) { "Inserisci username e password Xtream." }
        return base
    }

    private fun buildStreamUrl(base: HttpUrl, folder: String, credentials: XtreamCredentials, fileName: String): HttpUrl =
        base.newBuilder()
            .addPathSegment(folder)
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment(fileName)
            .build()

    private fun safeExtension(value: String, fallback: String): String =
        value.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: fallback

    private fun JSONObject.optNullableString(name: String): String? =
        optString(name).takeUnless { it.isBlank() || it == "null" }
}

