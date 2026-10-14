package com.mink.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.mink.app.MainActivity
import com.mink.app.R
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.EventBus
import com.mink.app.core.Logger
import com.mink.app.data.model.Episode
import com.mink.app.data.model.Podcast

class PlaybackService : Service() {

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null

    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "TunedMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { PlaybackManager.resume() }
                override fun onPause() { PlaybackManager.pause() }
                override fun onFastForward() { PlaybackManager.seekRelative(30000) }
                override fun onRewind() { PlaybackManager.seekRelative(-15000) }
                override fun onStop() { PlaybackManager.stop() }
            })
            isActive = true
        }
    }

    fun play(url: String, startMs: Long, speed: Float) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { mp ->
                    if (startMs > 0) mp.seekTo(startMs.toInt())
                    setSpeed(speed)
                    mp.start()
                    PlaybackManager.onPlaybackStarted(mp.duration.toLong())
                    updateNotification(true)
                }
                setOnCompletionListener {
                    PlaybackManager.onEpisodeCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    Logger.e("PlaybackService", "MediaPlayer error: $what, $extra")
                    false
                }
            }
        } catch (e: Exception) {
            Logger.e("PlaybackService", "Error starting playback", e)
        }
    }

    fun resume() {
        mediaPlayer?.start()
        updateNotification(true)
    }

    fun pause() {
        mediaPlayer?.pause()
        updateNotification(false)
    }

    fun seekTo(ms: Long) {
        mediaPlayer?.seekTo(ms.toInt())
    }

    fun setSpeed(speed: Float) {
        try {
            mediaPlayer?.playbackParams = PlaybackParams().apply { this.speed = speed }
        } catch (e: Exception) {
            Logger.w("PlaybackService", "Could not set playback speed", e)
        }
    }

    val currentPosition: Long get() = mediaPlayer?.currentPosition?.toLong() ?: 0L
    val duration: Long get() = mediaPlayer?.duration?.toLong() ?: 0L
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying ?: false

    private fun updateNotification(isPlaying: Boolean) {
        val ep = PlaybackManager.currentEpisode ?: return
        val pod = PlaybackManager.currentPodcast

        val notifIntent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_IMMUTABLE)

        val notifBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val notif = notifBuilder
            .setContentTitle(ep.title)
            .setContentText(pod?.title ?: "Tuned Podcast")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pIntent)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession?.sessionToken))
            .setOngoing(isPlaying)
            .build()

        startForeground(NOTIFICATION_ID, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "Playback Controls", NotificationManager.IMPORTANCE_LOW)
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession?.release()
    }

    companion object {
        const val CHANNEL_ID = "tuned_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
