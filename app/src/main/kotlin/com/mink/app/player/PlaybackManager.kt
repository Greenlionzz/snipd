package com.mink.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.core.EventBus
import com.mink.app.data.model.Episode
import com.mink.app.data.model.Podcast

object PlaybackManager {

    private var playbackService: PlaybackService? = null
    private var isBound = false

    var currentEpisode: Episode? = null
        private set
    var currentPodcast: Podcast? = null
        private set

    var playbackSpeed: Float = 1.0f
        set(value) {
            field = value
            playbackService?.setSpeed(value)
            postState()
        }

    private var tickerJob: Cancelable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.service
            isBound = true
            startTicker()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
            stopTicker()
        }
    }

    fun init(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun release(context: Context) {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
            playbackService = null
        }
    }

    fun playEpisode(episode: Episode, podcast: Podcast? = null) {
        currentEpisode = episode
        currentPodcast = podcast
        val startMs = episode.positionSec * 1000

        playbackService?.play(episode.audioUrl, startMs, playbackSpeed)
        postState()
    }

    fun resume() {
        playbackService?.resume()
        postState()
    }

    fun pause() {
        playbackService?.pause()
        saveCurrentPosition()
        postState()
    }

    fun togglePlayPause() {
        if (isPlaying()) pause() else resume()
    }

    fun seekTo(positionMs: Long) {
        playbackService?.seekTo(positionMs)
        postState()
    }

    fun seekRelative(deltaMs: Long) {
        val newPos = (currentPositionMs() + deltaMs).coerceAtLeast(0)
        seekTo(newPos)
    }

    fun stop() {
        playbackService?.pause()
        saveCurrentPosition()
        postState()
    }

    fun isPlaying(): Boolean = playbackService?.isPlaying == true
    fun currentPositionMs(): Long = playbackService?.currentPosition ?: 0L
    fun durationMs(): Long = playbackService?.duration ?: 0L

    fun onPlaybackStarted(durationMs: Long) {
        postState()
    }

    fun onEpisodeCompleted() {
        val ep = currentEpisode ?: return
        AppExecutors.executeIo {
            AppGraph.get().episodeDao.updatePosition(ep.id, ep.durationSec, true)
        }
        postState()
    }

    private fun saveCurrentPosition() {
        val ep = currentEpisode ?: return
        val posSec = currentPositionMs() / 1000
        AppExecutors.executeIo {
            AppGraph.get().episodeDao.updatePosition(ep.id, posSec, false)
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = AppExecutors.mainDelayed(1000) {
            if (isPlaying()) {
                saveCurrentPosition()
                postState()
            }
            startTicker()
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun postState() {
        EventBus.post(
            PlaybackStateEvent(
                episode = currentEpisode,
                podcast = currentPodcast,
                isPlaying = isPlaying(),
                positionMs = currentPositionMs(),
                durationMs = durationMs(),
                speed = playbackSpeed
            )
        )
    }
}

data class PlaybackStateEvent(
    val episode: Episode?,
    val podcast: Podcast?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float
)
