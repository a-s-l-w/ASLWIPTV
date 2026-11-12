package com.aslwiptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream

/** Downloads remote M3U playlists with the same proxy and VPN-aware client used for playback. */
class M3uRepository(private val client: OkHttpClient) {
    suspend fun load(urlText: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = urlText.trim().toHttpUrlOrNull()
                ?: error("Inserisci un URL M3U valido che inizi con http:// o https://.")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, */*")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download playlist non riuscito (${response.code})." }
                val body = response.body ?: error("Il server ha restituito una playlist vuota.")
                val maximumBytes = 32 * 1024 * 1024
                check(body.contentLength() <= maximumBytes || body.contentLength() < 0) {
                    "La playlist supera il limite di 32 MB."
                }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= maximumBytes) { "La playlist supera il limite di 32 MB." }
                        output.write(buffer, 0, count)
                    }
                }
                val text = output.toString(Charsets.UTF_8.name())
                val channels = M3uParser.parse(text)
                check(channels.isNotEmpty()) { "La playlist non contiene canali M3U validi." }
                channels
            }
        }
    }
}

