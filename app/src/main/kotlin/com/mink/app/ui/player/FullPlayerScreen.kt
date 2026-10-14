package com.mink.app.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.ai.AiService
import com.mink.app.core.*
import com.mink.app.data.model.Snip
import com.mink.app.net.ImageLoader
import com.mink.app.player.PlaybackManager
import com.mink.app.player.PlaybackStateEvent
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.podcast.EpisodeAiScreen
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class FullPlayerScreen(
    private val navigator: ScreenHost,
    private val imageLoader: ImageLoader,
    private val aiService: AiService
) : Screen {

    private lateinit var artIv: ImageView
    private lateinit var titleTv: TextView
    private lateinit var podTv: TextView
    private lateinit var timeTv: TextView
    private lateinit var remainingTv: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseIcon: IconView
    private lateinit var speedBtn: TextView
    private var eventSub: Cancelable? = null
    private var isUserTracking = false

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            // Header
            addView(ctx.screenHeader(
                title = "Now Playing",
                leadingIcon = Icon.CHEVRON_DOWN,
                onLeading = { navigator.popScreen() }
            ) {
                addView(ctx.iconButton(Icon.AI_SPARK, sizeDp = 20, tint = Theme.c.accent) {
                    val ep = PlaybackManager.currentEpisode
                    if (ep != null) {
                        navigator.pushScreen(EpisodeAiScreen(ep.id, navigator, aiService))
                    }
                })
            })

            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            }

            val content = ctx.vstack {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(24), dp(28), dp(32))

                // Artwork
                val artCard = ctx.card(bgColor = Theme.c.surfaceHigh, radiusDp = 20, strokeColor = Theme.c.outline) {
                    val s = dp(260)
                    artIv = ImageView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(s, s)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                            }
                        }
                    }
                    addView(artIv)
                }
                val alp = LinearLayout.LayoutParams(dp(260), dp(260))
                alp.bottomMargin = dp(28)
                artCard.layoutParams = alp
                artCard.elevation = dp(8).toFloat()
                addView(artCard)

                // Title
                titleTv = ctx.headlineText("").apply {
                    gravity = Gravity.CENTER; maxLines = 2; textSize = 20f
                    val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    tlp.bottomMargin = dp(6); layoutParams = tlp
                }
                addView(titleTv)

                // Podcast name
                podTv = ctx.labelText("").apply {
                    gravity = Gravity.CENTER; setTextColor(Theme.c.accent)
                    val plp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    plp.bottomMargin = dp(28); layoutParams = plp
                }
                addView(podTv)

                // Seek bar
                seekBar = SeekBar(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    thumbTintList = ColorStateList.valueOf(Theme.c.accent)
                    progressTintList = ColorStateList.valueOf(Theme.c.accent)
                    progressBackgroundTintList = ColorStateList.valueOf(Theme.c.outline)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
                        override fun onStartTrackingTouch(sb: SeekBar?) { isUserTracking = true }
                        override fun onStopTrackingTouch(sb: SeekBar?) {
                            isUserTracking = false; sb?.let { PlaybackManager.seekTo(it.progress.toLong()) }
                        }
                    })
                }
                addView(seekBar)

                // Time row
                addView(ctx.hstack {
                    val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    rlp.topMargin = dp(4); rlp.bottomMargin = dp(24); layoutParams = rlp
                    timeTv = ctx.captionText("0:00").apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    remainingTv = ctx.captionText("-0:00")
                    addView(timeTv); addView(remainingTv)
                })

                // Transport controls
                addView(ctx.hstack {
                    gravity = Gravity.CENTER

                    // Back 15s
                    addView(ctx.iconButton(Icon.BACK_15, sizeDp = 26, tint = Theme.c.text,
                        bgColor = Theme.c.surfaceHigh, radiusDp = 26) {
                        PlaybackManager.seekRelative(-15000)
                    }.apply {
                        val blp = LinearLayout.LayoutParams(dp(52), dp(52))
                        layoutParams = blp
                    })

                    // Play/Pause big circle
                    val ppWrap = FrameLayout(ctx).apply {
                        val s = dp(72)
                        val pplp = LinearLayout.LayoutParams(s, s)
                        pplp.marginStart = dp(24); pplp.marginEnd = dp(24)
                        layoutParams = pplp
                        background = ctx.rippleDrawable(Theme.c.accent, radiusDp = 36)
                        elevation = dp(4).toFloat()
                    }
                    playPauseIcon = IconView(ctx, Icon.PLAY, 28, Theme.c.onAccent)
                    ppWrap.addView(playPauseIcon, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
                    ppWrap.onTapFeedback { PlaybackManager.togglePlayPause() }
                    addView(ppWrap)

                    // Forward 30s
                    addView(ctx.iconButton(Icon.FORWARD_30, sizeDp = 26, tint = Theme.c.text,
                        bgColor = Theme.c.surfaceHigh, radiusDp = 26) {
                        PlaybackManager.seekRelative(30000)
                    }.apply {
                        val flp = LinearLayout.LayoutParams(dp(52), dp(52))
                        layoutParams = flp
                    })
                })

                // Speed + Snip row
                addView(ctx.hstack {
                    gravity = Gravity.CENTER
                    val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    blp.topMargin = dp(28); layoutParams = blp

                    speedBtn = ctx.labelText("1.0x").apply {
                        gravity = Gravity.CENTER; setTextColor(Theme.c.text)
                        background = ctx.rippleDrawable(Theme.c.surfaceHigh, radiusDp = 20, strokeColor = Theme.c.outline, strokeWidthDp = 1)
                        padding(h = 20, v = 10)
                        onTapFeedback { cycleSpeed() }
                    }
                    addView(speedBtn)

                    addView(ctx.primaryButton("Capture Snip") {
                        createSnip(ctx)
                    }.apply {
                        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        slp.marginStart = dp(16); layoutParams = slp
                    })
                })
            }
            scrollView.addView(content)
            addView(scrollView)
        }

        updateCurrentView()
        return root
    }

    override fun onShow() {
        eventSub = EventBus.subscribe { event ->
            if (event is PlaybackStateEvent) updateFromState(event)
        }
    }

    override fun onHide() { eventSub?.cancel(); eventSub = null }

    private fun updateCurrentView() {
        val ep = PlaybackManager.currentEpisode ?: return
        val pod = PlaybackManager.currentPodcast
        titleTv.text = ep.title; podTv.text = pod?.title ?: ""
        imageLoader.load(ep.artworkUrl, artIv)
        playPauseIcon.icon = if (PlaybackManager.isPlaying()) Icon.PAUSE else Icon.PLAY
        speedBtn.text = "${PlaybackManager.playbackSpeed}x"
    }

    private fun updateFromState(event: PlaybackStateEvent) {
        val ep = event.episode ?: return
        titleTv.text = ep.title; podTv.text = event.podcast?.title ?: ""
        playPauseIcon.icon = if (event.isPlaying) Icon.PAUSE else Icon.PLAY
        speedBtn.text = "${event.speed}x"
        if (!isUserTracking && event.durationMs > 0) {
            seekBar.max = event.durationMs.toInt()
            seekBar.progress = event.positionMs.toInt()
        }
        timeTv.text = TimeFmt.formatDurationMs(event.positionMs)
        remainingTv.text = "-${TimeFmt.formatDurationMs((event.durationMs - event.positionMs).coerceAtLeast(0))}"
    }

    private fun cycleSpeed() {
        val speeds = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)
        val idx = speeds.indexOf(PlaybackManager.playbackSpeed)
        PlaybackManager.playbackSpeed = speeds[if (idx in 0 until speeds.size - 1) idx + 1 else 0]
    }

    private fun createSnip(ctx: Context) {
        val ep = PlaybackManager.currentEpisode ?: return
        val pos = PlaybackManager.currentPositionMs()
        val start = (pos - 60000).coerceAtLeast(0)

        val input = EditText(ctx).apply {
            hint = "Note for this snip..."
            setTextColor(Theme.c.text); setHintTextColor(Theme.c.textTertiary)
            background = ctx.roundedDrawable(Theme.c.surfaceHigh, radiusDp = 10, strokeColor = Theme.c.outline, strokeWidthDp = 1)
            padding(12)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Save Snip (${TimeFmt.formatDurationMs(start)} – ${TimeFmt.formatDurationMs(pos)})")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val note = input.text.toString().trim()
                AppExecutors.executeIo {
                    AppGraph.get().miscDao.insertSnip(
                        Snip(episodeId = ep.id, startMs = start, endMs = pos,
                            title = if (note.isNotEmpty()) note else "Snip from ${ep.title}",
                            userNote = note)
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
