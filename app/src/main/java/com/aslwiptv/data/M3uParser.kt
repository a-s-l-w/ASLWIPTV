package com.aslwiptv.data

/** Parser permissivo per playlist M3U/M3U8 standard. Usa solo playlist che sei autorizzato a consultare. */
object M3uParser {
    private val attribute = Regex("([\\w-]+)=\\\"([^\\\"]*)\\\"")

    fun parse(text: String, kind: MediaKind = MediaKind.LIVE): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        var title = ""
        var group = "Senza categoria"
        var logo: String? = null
        var epg: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    title = line.substringAfter(',', "Canale senza nome").trim().ifBlank { "Canale senza nome" }
                    group = attrs["group-title"] ?: "Senza categoria"
                    logo = attrs["tvg-logo"]
                    epg = attrs["tvg-id"]
                }
                line.isNotEmpty() && !line.startsWith('#') && (line.startsWith("http://") || line.startsWith("https://")) -> {
                    result += MediaItem(
                        id = "${result.size}:${line.hashCode()}", title = title.ifBlank { line.substringAfterLast('/').ifBlank { "Stream" } },
                        streamUrl = line, category = group, logoUrl = logo, kind = kind, epgId = epg
                    )
                    title = ""; group = "Senza categoria"; logo = null; epg = null
                }
            }
        }
        return result
    }
}
