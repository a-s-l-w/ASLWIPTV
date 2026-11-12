package com.aslwiptv.data

import org.json.JSONObject

/** Parser permissivo per playlist M3U/M3U8 standard. Usa solo playlist che sei autorizzato a consultare. */
object M3uParser {
    private val attribute = Regex("([\\w-]+)=\\\"([^\\\"]*)\\\"")

    fun parse(text: String, kind: MediaKind = MediaKind.LIVE): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        var title = ""
        var group = "Senza categoria"
        var logo: String? = null
        var epg: String? = null
        var typeHint = ""
        val requestHeaders = linkedMapOf<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.removePrefix("\uFEFF").trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    title = line.substringAfter(',', "Canale senza nome").trim().ifBlank { "Canale senza nome" }
                    group = attrs["group-title"] ?: "Senza categoria"
                    logo = attrs["tvg-logo"]
                    epg = attrs["tvg-id"]
                    typeHint = attrs["tvg-type"] ?: attrs["type"] ?: attrs["content-type"].orEmpty()
                    requestHeaders.clear()
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substringAfter(':')
                    val name = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim().removeSurrounding("\"")
                    val header = when (name) {
                        "http-user-agent" -> "User-Agent"
                        "http-referrer", "http-referer" -> "Referer"
                        "http-origin" -> "Origin"
                        "http-cookie" -> "Cookie"
                        else -> null
                    }
                    if (header != null && validHeaderValue(value)) requestHeaders[header] = value
                }
                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    runCatching { JSONObject(line.substringAfter(':')) }.getOrNull()?.let { objectHeaders ->
                        listOf("User-Agent", "Referer", "Origin", "Cookie").forEach { header ->
                            objectHeaders.optString(header).takeIf(::validHeaderValue)?.let { requestHeaders[header] = it }
                        }
                    }
                }
                line.isNotEmpty() && !line.startsWith('#') && (line.startsWith("http://") || line.startsWith("https://")) -> {
                    result += MediaItem(
                        id = "${result.size}:${line.hashCode()}", title = title.ifBlank { line.substringAfterLast('/').ifBlank { "Stream" } },
                        streamUrl = line,
                        category = group,
                        logoUrl = logo,
                        kind = inferKind(group, title, line, kind, typeHint),
                        epgId = epg,
                        requestHeaders = requestHeaders.toMap()
                    )
                    title = ""; group = "Senza categoria"; logo = null; epg = null; typeHint = ""; requestHeaders.clear()
                }
            }
        }
        return result
    }

    private fun inferKind(group: String, title: String, url: String, fallback: MediaKind, typeHint: String): MediaKind {
        val metadata = "$typeHint $group $title".lowercase()
        return when {
            Regex("\\b(serie|series|tv show|tv shows|episodi|episode|stagione|season)\\b").containsMatchIn(metadata) -> MediaKind.SERIES
            Regex("\\b(film|movie|movies|vod|cinema)\\b").containsMatchIn(metadata) -> MediaKind.MOVIE
            Regex("\\.(mp4|mkv|avi|mov|m4v)(?:[?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> MediaKind.MOVIE
            else -> fallback
        }
    }

    private fun validHeaderValue(value: String): Boolean =
        value.isNotBlank() && value.length <= 4096 && '\r' !in value && '\n' !in value
}


