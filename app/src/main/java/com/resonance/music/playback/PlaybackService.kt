package com.resonance.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaConstants
import androidx.media3.session.DefaultMediaNotificationProvider
import com.resonance.music.R
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.download.OfflineLibrary
import com.resonance.music.data.download.OfflineStatus
import com.resonance.music.MainActivity
import com.resonance.music.tabata.TabataAudioProcessor
import com.resonance.music.tabata.TabataConfig
import com.resonance.music.tabata.TabataEngine
import com.resonance.music.tabata.TabataRepository

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var sessionCallback: LibrarySessionCallback
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var offline: OfflineLibrary
    @Inject lateinit var reporting: PlaybackReporting
    @Inject lateinit var tabataRepository: TabataRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaLibrarySession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_launcher_monochrome)
        })

        val tabataFx = tabataRepository.audioProcessor
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(tabataFx))
                    .build()
            }
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setLoadErrorHandlingPolicy(StreamLoadErrorPolicy())
            )
            .build()

        var consecutiveFailures = 0
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                    mediaSession?.let(sessionCallback::updateButtons)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Playback error: ${error.message}", error)
                if ((error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode in 300..399) {
                    Toast.makeText(this@PlaybackService, R.string.stream_redirect_error, Toast.LENGTH_LONG).show()
                    player.pause()
                    return
                }
                if (error.isConnectionError()) return
                consecutiveFailures++
                if (consecutiveFailures > 3) {
                    player.pause()
                    return
                }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) consecutiveFailures = 0
            }
        })

        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(buildContentIntent())
            .setSessionExtras(Bundle().apply {
                putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
                putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
            })
            .build()
        mediaSession?.let(sessionCallback::updateButtons)
        reporting.start(player)
        serviceScope.launch {
            offline.tracks.map { tracks -> tracks.filter { it.status == OfflineStatus.READY }.map { it.record.key } }
                .distinctUntilChanged().collect { keys -> mediaSession?.notifyChildrenChanged("downloads", keys.size, null) }
        }
        serviceScope.launch {
            var previous = auth.generation.value
            auth.generation.collect { generation ->
                if (generation != previous) { player.stop(); player.clearMediaItems() }
                previous = generation
            }
        }
    }

    fun startTabata(config: TabataConfig) {
        tabataRepository.start(config)
    }

    fun stopTabata() {
        tabataRepository.stop()
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        sessionCallback.release()
        reporting.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

private fun PlaybackException.isConnectionError(): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        ((cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0) >= 500
    else -> false
}

@OptIn(UnstableApi::class)
private class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val responseCode = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        if (responseCode != null && responseCode in 300..499) return C.TIME_UNSET
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 10
}
