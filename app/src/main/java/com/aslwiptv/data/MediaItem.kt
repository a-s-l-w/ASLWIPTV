package com.aslwiptv.data

data class MediaItem(
    val id: String,
    val title: String,
    val streamUrl: String,
    val category: String = "Senza categoria",
    val logoUrl: String? = null,
    val kind: MediaKind = MediaKind.LIVE,
    val epgId: String? = null
)

enum class MediaKind { LIVE, MOVIE, SERIES, UNKNOWN }

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)
