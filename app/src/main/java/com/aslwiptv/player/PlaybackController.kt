package com.aslwiptv.player

import android.content.Context
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aslwiptv.data.AppNetwork
import com.aslwiptv.data.MediaItem

/** Single playback engine shared by compact and advanced control surfaces. */
class PlaybackController(context: Context, private val network: AppNetwork) {
    private val compatibleUserAgent = Util.getUserAgent(context, "ExoPlayer")
    private val appProxyHttpFactory = OkHttpDataSource.Factory(network.client).setUserAgent(compatibleUserAgent)
    private val platformHttpFactory = DefaultHttpDataSource.Factory().setUserAgent(compatibleUserAgent)
    private val routedHttpFactory = object : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val settings = network.proxySettings()
            val useAppProxy = settings.enabled && settings.validate() == null
            return if (useAppProxy) appProxyHttpFactory.createDataSource()
            else platformHttpFactory.createDataSource()
        }
    }
    private val mediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            routedHttpFactory
        )
    )

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
                .build()
        )
        .setMediaSourceFactory(mediaSourceFactory)
        .build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }

    fun play(item: MediaItem) {
        val customUserAgent = item.requestHeaders.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
        val requestHeaders = item.requestHeaders.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val userAgent = customUserAgent ?: compatibleUserAgent
        appProxyHttpFactory.setUserAgent(userAgent).setDefaultRequestProperties(requestHeaders)
        platformHttpFactory.setUserAgent(userAgent).setDefaultRequestProperties(requestHeaders)
        val mime = when {
            item.streamUrl.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            item.streamUrl.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val media = ExoMediaItem.Builder().setUri(item.streamUrl).apply {
            if (mime != null) setMimeType(mime)
        }.build()
        player.setMediaItem(media)
        player.prepare()
        player.play()
    }

    fun release() = player.release()
}


