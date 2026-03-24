package com.resonance.music.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @Inject
    lateinit var playbackManager: PlaybackManager

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Add error listener to prevent unhandled crashes
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Playback error: ${error.message}", error)
                // Try to skip to next track on error instead of crashing
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                    exoPlayer.prepare()
                }
            }
        })

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        playbackManager.initialize(exoPlayer)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure we promote to foreground quickly to avoid
        // ForegroundServiceDidNotStartInTimeException.
        // MediaSessionService normally handles this, but under heavy load it can
        // be delayed. Post a temporary notification as a safety net.
        try {
            ensureNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Resonance")
                .setContentText("Preparing playback…")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setSilent(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w("PlaybackService", "Could not promote to foreground early", e)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "resonance_playback"
        private const val NOTIFICATION_ID = 1
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        // If music is still playing, the foreground service keeps running
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        playbackManager.onServiceDestroyed()
        super.onDestroy()
    }
}
