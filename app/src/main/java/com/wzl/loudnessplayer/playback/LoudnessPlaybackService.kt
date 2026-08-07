package com.wzl.loudnessplayer.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wzl.loudnessplayer.audio.ApeStreamingDataSource

/**
 * Owns the Media3 session used by Android's notification and lock-screen media controls.
 * The player survives activity recreation; Android stops this foreground service after playback
 * becomes idle, so no background process is kept for a stopped queue.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class LoudnessPlaybackService : MediaSessionService() {
    override fun onCreate() {
        super.onCreate()
        PlaybackSessionHolder.session(applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        PlaybackSessionHolder.session(applicationContext)

    companion object {
        const val SERVICE_CLASS_NAME = "com.wzl.loudnessplayer.playback.LoudnessPlaybackService"

        fun player(context: Context): ExoPlayer = PlaybackSessionHolder.player(context)

        fun ensureStarted(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LoudnessPlaybackService::class.java),
            )
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private object PlaybackSessionHolder {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    @Synchronized
    fun player(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        return player ?: ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(appContext, ApeStreamingDataSource.Factory(appContext)),
                ),
            )
            .build()
            .also { created -> player = created }
    }

    @Synchronized
    fun session(context: Context): MediaSession =
        session ?: MediaSession.Builder(context.applicationContext, player(context)).build()
            .also { created -> session = created }
}
