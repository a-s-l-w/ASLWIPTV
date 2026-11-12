package com.aslwiptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
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
            val base = credentials.serverUrl.trim().trimEnd('/')
            require(base.startsWith("https://")) { "Per sicurezza usa un server HTTPS." }
            val user = URLEncoder.encode(credentials.username, Charsets.UTF_8.name())
            val pass = URLEncoder.encode(credentials.password, Charsets.UTF_8.name())
            // Nel path di un URL lo spazio va codificato come %20: URLEncoder produce '+', che nei path non è uno spazio.
            val pathUser = user.replace("+", "%20")
            val pathPass = pass.replace("+", "%20")
            val url = "$base/player_api.php?username=$user&password=$pass&action=get_live_streams"
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Server non raggiungibile (${response.code})." }
                val body = response.body?.string().orEmpty()
                // Con credenziali errate molti server rispondono con un oggetto JSON invece che con un array.
                check(body.trimStart().startsWith("[")) { "Risposta non valida: controlla URL, username e password." }
                val array = JSONArray(body)
                buildList<MediaItem> {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val streamId = obj.optString("stream_id")
                        val name = obj.optString("name", "Canale $streamId")
                        val category = obj.optString("category_name", "Senza categoria")
                        val ext = obj.optString("container_extension", "ts").ifBlank { "ts" }
                        val stream = "$base/live/$pathUser/$pathPass/$streamId.$ext"
                        add(
                            MediaItem(
                                id = streamId,
                                title = name,
                                streamUrl = stream,
                                category = category,
                                logoUrl = obj.optString("stream_icon").takeIf { it.isNotBlank() },
                                kind = MediaKind.LIVE,
                                epgId = obj.optString("epg_channel_id").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }
        }
    }
}
