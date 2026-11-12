package com.aslwiptv.player

import android.content.Context
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aslwiptv.data.MediaItem
import okhttp3.OkHttpClient

/** Single playback engine shared by compact and advanced control surfaces. */
class PlaybackController(context: Context, networkClient: OkHttpClient) {
    private val mediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(networkClient).setUserAgent("ASLWIPTV/0.3.0")
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

