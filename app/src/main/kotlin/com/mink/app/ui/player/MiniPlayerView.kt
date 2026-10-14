package com.mink.app.ui.player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.core.EventBus
import com.mink.app.data.model.Snip
import com.mink.app.net.ImageLoader
import com.mink.app.player.PlaybackManager
import com.mink.app.player.PlaybackStateEvent
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class MiniPlayerView(
    context: Context,
    private val imageLoader: ImageLoader,
    private val navigator: ScreenHost,
    private val aiService: AiService
) : FrameLayout(context) {

    private var titleTv: TextView? = null
    private var subtitleTv: TextView? = null
    private var playPauseIcon: IconView? = null
    private var artIv: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var eventSub: Cancelable? = null

    init {
        background = context.roundedDrawable(
            color = Theme.c.surfaceHigh,
            radiusDp = 16,
            strokeColor = Theme.c.outline,
            strokeWidthDp = 1
        )
        elevation = dp(6).toFloat()
        minimumHeight = dp(64)

        val outer = context.vstack {}

        // Progress hairline at top
        val bgDr = GradientDrawable().apply {
            setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(2).toFloat()
        }
        val pgDr = GradientDrawable().apply {
            setColor(Theme.c.accent); cornerRadius = dp(2).toFloat()
        }
        val pgClip = ClipDrawable(pgDr, Gravity.START, ClipDrawable.HORIZONTAL)
        val layer = LayerDrawable(arrayOf(bgDr, pgClip)).apply {
            setId(0, android.R.id.background); setId(1, android.R.id.progress)
        }
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
            lp.bottomMargin = dp(4)
            layoutParams = lp
            isIndeterminate = false; max = 1000; progress = 0
            progressDrawable = layer
        }
        outer.addView(progressBar)

        // Row: artwork | texts | controls
        val row = context.hstack {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(8), dp(6))
        }

        // Artwork
        artIv = context.artworkView(sizeDp = 44, radiusDp = 10).apply {
            val alp = LinearLayout.LayoutParams(dp(44), dp(44))
            alp.marginEnd = dp(12)
            layoutParams = alp
        }
        row.addView(artIv)

        // Texts
        val textStack = context.vstack {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleTv = context.labelText("Not playing").apply {
            setTextColor(Theme.c.text); maxLines = 1
        }
        textStack.addView(titleTv)
        subtitleTv = context.captionText("Tap to open player").apply {
            setTextColor(Theme.c.textSecondary); maxLines = 1
        }
        textStack.addView(subtitleTv)
        row.addView(textStack)

        // Snip button
        val snipBtn = context.iconButton(Icon.SNIP, sizeDp = 20, tint = Theme.c.textSecondary) {
            val ep = PlaybackManager.currentEpisode
            if (ep != null) {
                val pos = PlaybackManager.currentPositionMs()
                val start = (pos - 30_000).coerceAtLeast(0)
                AppExecutors.executeIo {
                    AppGraph.get().miscDao.insertSnip(
                        Snip(
                            episodeId = ep.id, startMs = start, endMs = pos,
                            title = "Snip: ${ep.title}",
                            userNote = "Quick snip captured from mini-player"
                        )
                    )
                }
            }
        }.apply {
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.marginEnd = dp(2)
            layoutParams = lp
        }
        row.addView(snipBtn)

        // Play/Pause
        val playWrap = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            background = context.rippleDrawable(Theme.c.accent, radiusDp = 21)
        }
        playPauseIcon = IconView(context, Icon.PLAY, 20, Theme.c.onAccent)
        playWrap.addView(playPauseIcon, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        playWrap.onTapFeedback { PlaybackManager.togglePlayPause() }
        row.addView(playWrap)

        outer.addView(row)
        addView(outer)

        onTapFeedback {
            if (PlaybackManager.currentEpisode != null) {
                navigator.pushScreen(FullPlayerScreen(navigator, imageLoader, aiService))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        eventSub = EventBus.subscribe { event ->
            if (event is PlaybackStateEvent) updateState(event)
        }
        val ep = PlaybackManager.currentEpisode
        if (ep == null) {
            visibility = View.GONE
        } else {
            visibility = View.VISIBLE
            titleTv?.text = ep.title
            subtitleTv?.text = PlaybackManager.currentPodcast?.title ?: "Playing"
            playPauseIcon?.icon = if (PlaybackManager.isPlaying()) Icon.PAUSE else Icon.PLAY
            artIv?.let { imageLoader.load(ep.artworkUrl, it) }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        eventSub?.cancel(); eventSub = null
    }

    private fun updateState(event: PlaybackStateEvent) {
        val ep = event.episode
        if (ep == null) {
            visibility = View.GONE
        } else {
            visibility = View.VISIBLE
            titleTv?.text = ep.title
            subtitleTv?.text = event.podcast?.title ?: "Playing"
            playPauseIcon?.icon = if (event.isPlaying) Icon.PAUSE else Icon.PLAY
            artIv?.let { imageLoader.load(ep.artworkUrl, it) }
            if (event.durationMs > 0) {
                progressBar?.progress = (event.positionMs.toFloat() / event.durationMs * 1000).toInt().coerceIn(0, 1000)
            }
        }
    }
}
