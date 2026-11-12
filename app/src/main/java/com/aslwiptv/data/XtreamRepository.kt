package com.aslwiptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Client minimale Xtream Codes API. Non registra mai username/password nei log. */
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
            val base = credentials.serverUrl.trim().trimEnd('/').toHttpUrlOrNull()
                ?: error("Inserisci l’URL del server Xtream, per esempio http://server:porta.")
            require(base.scheme == "http" || base.scheme == "https") {
                "Il server deve usare http:// oppure https://."
            }
            require(base.encodedQuery == null && base.encodedFragment == null) {
                "L’URL del server non deve contenere query o frammenti."
            }
            require(credentials.username.isNotBlank() && credentials.password.isNotBlank()) {
                "Inserisci username e password Xtream."
            }
            val apiUrl = base.newBuilder()
                .addPathSegment("player_api.php")
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .addQueryParameter("action", "get_live_streams")
                .build()
            val request = Request.Builder().url(apiUrl).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Server non raggiungibile (${response.code})." }
                val body = response.body?.string().orEmpty()
                // Many providers return an object, rather than an array, for invalid credentials.
                check(body.trimStart().startsWith("[")) {
                    "Risposta Xtream non valida: controlla URL, username e password."
                }
                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val streamId = obj.optString("stream_id").takeIf { it.isNotBlank() } ?: continue
                        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Canale $streamId"
                        val category = obj.optString("category_name").takeIf { it.isNotBlank() } ?: "Senza categoria"
                        val ext = obj.optString("container_extension", "ts")
                            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "ts"
                        val stream = base.newBuilder()
                            .addPathSegment("live")
                            .addPathSegment(credentials.username)
                            .addPathSegment(credentials.password)
                            .addPathSegment("$streamId.$ext")
                            .build()
                        add(
                            MediaItem(
                                id = streamId,
                                title = name,
                                streamUrl = stream.toString(),
                                category = category,
                                logoUrl = obj.optString("stream_icon").takeIf { it.isNotBlank() },
                                kind = MediaKind.LIVE,
                                epgId = obj.optString("epg_channel_id").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }.also { check(it.isNotEmpty()) { "Xtream non ha restituito canali Live TV." } }
            }
        }
    }
}

