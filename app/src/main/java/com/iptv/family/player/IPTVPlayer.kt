package com.iptv.family.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlayerBuilder
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class IPTVPlayer private constructor(
    private val exoPlayer: ExoPlayer,
    private val context: Context,
) {
    companion object {
        private const val TAG = "IPTVPlayer"

        fun create(context: Context): IPTVPlayer {
            val trackSelector = DefaultTrackSelector(context)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs = 15000,
                    maxBufferMs = 50000,
                    bufferForPlaybackMs = 5000,
                    bufferForPlaybackAfterRebufferMs = 5000,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val exoPlayer = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setHandleAudioBecomingNoisy(true)
                .build()

            return IPTVPlayer(exoPlayer, context)
        }
    }

    suspend fun prepare(channel: com.iptv.family.domain.model.Channel) {
        val mediaItem = buildMediaItem(channel)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    private fun buildMediaItem(channel: com.iptv.family.domain.model.Channel): MediaItem {
        val mediaItemBuilder = MediaItem.fromUri(channel.url)

        // Add headers if present
        if (channel.headers.isNotEmpty()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(channel.headers)
                .setUserAgent(channel.userAgent ?: "IPTVFamily/1.0")
                .setAllowCrossProtocolRedirects(channel.httpOptions?.allowCrossProtocolRedirects ?: true)
                .setConnectTimeoutMs(channel.httpOptions?.connectTimeout ?: 10000)
                .setReadTimeoutMs(channel.httpOptions?.readTimeout ?: 30000)

            mediaItemBuilder.adsConfiguration = null // No ads
        }

        // Set metadata
        val metadata = MediaMetadata.Builder()
            .setTitle(channel.displayName)
            .setArtworkUri(channel.displayLogo?.let { Uri.parse(it) })
            .build()
        mediaItemBuilder.mediaMetadata = metadata

        return mediaItemBuilder.build()
    }

    fun play() {
        exoPlayer.playWhenReady = true
    }

    fun pause() {
        exoPlayer.playWhenReady = false
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }

    fun getDuration(): Long {
        return exoPlayer.duration
    }

    fun isPlaying(): Boolean {
        return exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_READY
    }

    fun getPlaybackState(): Int {
        return exoPlayer.playbackState
    }

    fun getBufferedPosition(): Long {
        return exoPlayer.bufferedPosition
    }

    fun getAvailableQualities(): List<QualityOption> {
        val trackGroups = exoPlayer.currentTrackGroups
        val qualities = mutableListOf<QualityOption>()

        if (trackGroups != null) {
            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                for (j in 0 until group.length) {
                    val format = group.getFormat(j)
                    if (format.width > 0 && format.height > 0) {
                        qualities.add(QualityOption(
                            width = format.width,
                            height = format.height,
                            bitrate = format.bitrate.toLong(),
                            format = format,
                        ))
                    }
                }
            }
        }

        return qualities.distinctBy { "${it.width}x${it.height}" }.sortedByDescending { it.height }
    }

    fun setQuality(quality: QualityOption) {
        val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSizeSd(quality.width * quality.height)
                .setPreferredVideoQualities(listOf(quality.format))
        )
    }

    fun selectAudioTrack(index: Int) {
        val trackSelector = exoPlayer.trackSelector as? MappingTrackSelector
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setRendererDisabled(MappingTrackSelector.RENDERER_AUDIO, false)
        )
        // ExoPlayer doesn't easily allow programmatic audio track selection without custom TrackSelector
    }

    fun selectSubtitleTrack(index: Int) {
        val trackSelector = exoPlayer.trackSelector as? MappingTrackSelector
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setRendererDisabled(MappingTrackSelector.RENDERER_TEXT, false)
        )
    }

    fun getAudioTracks(): List<TrackOption> {
        // Implementation would require accessing track selector
        return emptyList()
    }

    fun getSubtitleTracks(): List<TrackOption> {
        // Implementation would require accessing track selector
        return emptyList()
    }

    fun addListener(listener: Player.Listener) {
        exoPlayer.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        exoPlayer.removeListener(listener)
    }

    fun release() {
        exoPlayer.release()
    }

    val player: ExoPlayer
        get() = exoPlayer
}

data class QualityOption(
    val width: Int,
    val height: Int,
    val bitrate: Long,
    val format: androidx.media3.common.Format,
) {
    val label: String
        get() = "${height}p (${(bitrate / 1000).toString()} kbps)"
}

data class TrackOption(
    val index: Int,
    val language: String?,
    val label: String,
    val format: androidx.media3.common.Format,
)