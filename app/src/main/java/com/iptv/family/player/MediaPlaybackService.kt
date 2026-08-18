package com.iptv.family.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommandGroup
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaPlaybackService : MediaSessionService() {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "iptv_playback_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onGetSession(controllerInfo: MediaController.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            exoPlayer = createExoPlayer()
            mediaSession = createMediaSession(exoPlayer!!)
        }
        return mediaSession
    }

    private fun createExoPlayer(): ExoPlayer {
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)
        val loadControl = androidx.media3.exoplayer.upstream.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs = 15000,
                maxBufferMs = 50000,
                bufferForPlaybackMs = 5000,
                bufferForPlaybackAfterRebufferMs = 5000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun createMediaSession(player: ExoPlayer): MediaSession {
        val session = MediaSession.Builder(this, player)
            .setSessionCallback(MySessionCallback())
            .setSessionCommands(
                SessionCommandGroup(
                    SessionCommand.COMMAND_PLAY,
                    SessionCommand.COMMAND_PAUSE,
                    SessionCommand.COMMAND_SEEK_TO,
                    SessionCommand.COMMAND_SEEK_FORWARD,
                    SessionCommand.COMMAND_SEEK_BACKWARD,
                    SessionCommand.COMMAND_SET_PLAYBACK_SPEED,
                    SessionCommand.COMMAND_GET_MEDIA_ITEMS,
                    SessionCommand.COMMAND_ADD_MEDIA_ITEM,
                    SessionCommand.COMMAND_REMOVE_MEDIA_ITEM,
                    SessionCommand.COMMAND_REPLACE_MEDIA_ITEM,
                )
            )
            .build()

        return session
    }

    private inner class MySessionCallback : MediaSession.Callback() {
        override fun onAddMediaItems(
            controller: MediaController,
            mediaItems: List<MediaItem>,
        ): SessionResult {
            controller.mediaItems?.let { currentItems ->
                exoPlayer?.mediaSourceFactory?.createMediaSource(MediaItem.fromUri(currentItems[0].mediaUri))
            }
            return super.onAddMediaItems(controller, mediaItems)
        }

        override fun onReplaceMediaItems(
            controller: MediaController,
            mediaItems: List<MediaItem>,
        ): SessionResult {
            mediaItems.firstOrNull()?.let { mediaItem ->
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
                updateNotification(mediaItem)
            }
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaController,
            customCommand: SessionCommand,
            args: Bundle,
        ): SessionResult {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    fun playChannel(channel: com.iptv.family.domain.model.Channel) {
        val mediaItem = buildMediaItem(channel)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
        updateNotification(mediaItem)
        showNotification()
    }

    private fun buildMediaItem(channel: com.iptv.family.domain.model.Channel): MediaItem {
        val mediaItemBuilder = MediaItem.fromUri(channel.url)

        // Add metadata
        val metadata = MediaMetadata.Builder()
            .setTitle(channel.displayName)
            .setArtworkUri(channel.displayLogo?.let { android.net.Uri.parse(it) })
            .build()
        mediaItemBuilder.mediaMetadata = metadata

        return mediaItemBuilder.build()
    }

    private fun updateNotification(mediaItem: MediaItem) {
        val notification = buildNotification(mediaItem)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(mediaItem: MediaItem): Notification {
        val title = mediaItem.mediaMetadata?.title ?: "IPTV Family"
        val artist = mediaItem.mediaMetadata?.artist ?: "Canal en vivo"

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.iptv.family.ui.main.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bitmap = getNotificationIcon()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(bitmap)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_pause,
                    "Pausar",
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_stop,
                    "Detener",
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
                ).build()
            )
            .setStyle(
                androidx.media3.session.MediaNotificationHelper.MediaStyle()
                    .setMediaSession(mediaSession!!.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun getNotificationIcon(): Bitmap {
        return BitmapFactory.decodeResource(resources, R.drawable.ic_launcher)
    }

    private fun showNotification() {
        startForeground(NOTIFICATION_ID, buildNotification(exoPlayer?.mediaItem ?: MediaItem.fromUri("")), Build.VERSION_CODES.R)
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción IPTV",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación de reproducción en curso"
                importance = NotificationManager.IMPORTANCE_LOW
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.iptv.family.ACTION_PAUSE"
        const val ACTION_STOP = "com.iptv.family.ACTION_STOP"
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(true)
        coroutineScope.cancel()
        super.onDestroy()
    }
}