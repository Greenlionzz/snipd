package com.mink.app.ui.library

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.core.EventBus
import com.mink.app.data.model.Podcast
import com.mink.app.net.ImageLoader
import com.mink.app.podcast.PodcastsUpdatedEvent
import com.mink.app.podcast.SubscriptionChangedEvent
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.podcast.PodcastDetailScreen
import com.mink.app.ui.snips.SnipsLibraryScreen
import com.mink.app.ui.theme.Icon

class LibraryScreen(
    private val navigator: ScreenHost,
    private val imageLoader: ImageLoader,
    private val aiService: AiService
) : Screen {

    private lateinit var contentLayout: LinearLayout
    private lateinit var countBadge: TextView
    private var eventSub: Cancelable? = null
    private var displayedSubscriptions: List<Podcast>? = null

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Color.parseColor("#0F1115"))

            // ── Premium App Bar ─────────────────────────────────────
            addView(ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#15181F"))
                setPadding(ctx.dp(20), ctx.dp(16), ctx.dp(16), ctx.dp(16))

                // Title + Count Pill
                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val titleView = TextView(ctx).apply {
                        text = "Library"
                        setTextColor(Color.parseColor("#F8FAFC"))
                        textSize = 24f
                        typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                    }
                    addView(titleView)

                    countBadge = TextView(ctx).apply {
                        visibility = View.GONE
                        setTextColor(Color.parseColor("#94A3B8"))
                        textSize = 11f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        background = ctx.roundedDrawable(Color.parseColor("#222733"), radiusDp = 12)
                        setPadding(ctx.dp(8), ctx.dp(3), ctx.dp(8), ctx.dp(3))
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also {
                            it.marginStart = ctx.dp(10)
                        }
                        layoutParams = lp
                    }
                    addView(countBadge)
                })

                // Top right actions: Snips button in subtle circular button
                addView(ctx.frame {
                    val size = ctx.dp(38)
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    background = ctx.roundedDrawable(Color.parseColor("#1F2430"), radiusDp = 19)
                    val iconBtn = ctx.iconButton(Icon.SNIP, sizeDp = 20, tint = Color.parseColor("#A0AEC0")) {
                        navigator.pushScreen(SnipsLibraryScreen(navigator))
                    }.apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                    addView(iconBtn)
                })
            })

            // ── Scrollable Grid ──────────────────────────────────────
            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            contentLayout = ctx.vstack {
                setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(36))
                addView(ctx.skeletonRows(4))
            }
            scrollView.addView(contentLayout)
            addView(scrollView)
        }

        loadData(root.context, forceReload = true)
        return root
    }

    override fun onShow() {
        eventSub = EventBus.subscribe { event ->
            if ((event is PodcastsUpdatedEvent || event is SubscriptionChangedEvent) && ::contentLayout.isInitialized) {
                loadData(contentLayout.context, forceReload = false)
            }
        }
        if (::contentLayout.isInitialized) {
            // When switching back to this screen, only reload if data changed
            loadData(contentLayout.context, forceReload = false)
        }
    }

    override fun onHide() {
        eventSub?.cancel()
        eventSub = null
    }

    private fun loadData(ctx: Context, forceReload: Boolean) {
        AppExecutors.executeIo {
            val subscriptions = AppGraph.get().podcastDao.getAllSubscribed()

            AppExecutors.main {
                val previous = displayedSubscriptions
                val isDataIdentical = previous != null &&
                        previous.size == subscriptions.size &&
                        previous.map { it.id to it.feedUrl } == subscriptions.map { it.id to it.feedUrl }

                // If data has not changed and content is already rendered, keep views and cached covers untouched!
                if (!forceReload && isDataIdentical && contentLayout.childCount > 0) {
                    return@main
                }

                displayedSubscriptions = subscriptions
                contentLayout.removeAllViews()

                if (subscriptions.isEmpty()) {
                    countBadge.visibility = View.GONE
                    contentLayout.addView(ctx.emptyState(
                        icon = Icon.LIBRARY,
                        title = "Your library is empty",
                        message = "Follow your favorite podcasts from Discover to quickly access them here.",
                        actionLabel = "Discover Podcasts"
                    ))
                    return@main
                }

                // Update badge
                countBadge.visibility = View.VISIBLE
                countBadge.text = "${subscriptions.size} SHOWS"

                val gap = ctx.dp(14) // spacing between grid items

                // Responsive columns: 3 on phones, 4 on tablets, 6 on wide screens
                val screenW = ctx.resources.displayMetrics.widthPixels
                val columns = when {
                    screenW >= ctx.dp(960) -> 6
                    screenW >= ctx.dp(600) -> 4
                    else -> 3
                }

                val rows = subscriptions.chunked(columns)

                for (rowPodcasts in rows) {
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.bottomMargin = gap }
                    }

                    for ((i, podcast) in rowPodcasts.withIndex()) {
                        val card = buildPodcastCard(ctx, podcast)
                        val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        if (i > 0) clp.marginStart = gap
                        card.layoutParams = clp
                        row.addView(card)
                    }

                    // Pad incomplete rows with invisible placeholders so cards keep consistent width
                    val missing = columns - rowPodcasts.size
                    for (j in 0 until missing) {
                        val placeholder = View(ctx).apply {
                            val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            clp.marginStart = gap
                            layoutParams = clp
                        }
                        row.addView(placeholder)
                    }

                    contentLayout.addView(row)
                }
            }
        }
    }

    private fun buildPodcastCard(ctx: Context, pod: Podcast): View {
        val card = ctx.vstack {
            // Square artwork container with squircle border & shadow styling
            val squareFrame = object : FrameLayout(ctx) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, widthMeasureSpec)
                    setMeasuredDimension(measuredWidth, measuredWidth)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1B1F28"))
                    cornerRadius = ctx.dp(12).toFloat()
                    setStroke(ctx.dp(1), Color.parseColor("#292F3D"))
                }
                background = bg
                clipToOutline = true

                val iv = ImageView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                addView(iv)

                if (pod.artworkUrl.isNotEmpty()) {
                    imageLoader.load(pod.artworkUrl, iv)
                }
            }
            addView(squareFrame)

            // Title
            addView(TextView(ctx).apply {
                text = pod.title
                setTextColor(Color.parseColor("#F1F5F9"))
                textSize = 12.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 2
                setLineSpacing(ctx.dp(2).toFloat(), 1f)
                ellipsize = android.text.TextUtils.TruncateAt.END
                val tlp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                tlp.topMargin = ctx.dp(7)
                layoutParams = tlp
            })

            // Author / Publisher
            val authorText = pod.author.ifEmpty { pod.description }
            if (authorText.isNotEmpty()) {
                addView(TextView(ctx).apply {
                    text = authorText
                    setTextColor(Color.parseColor("#828E9F"))
                    textSize = 11f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    val alp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    alp.topMargin = ctx.dp(2)
                    layoutParams = alp
                })
            }

            onTapFeedback {
                navigator.pushScreen(PodcastDetailScreen(pod.id, navigator, imageLoader, aiService, initialPodcast = pod))
            }
        }
        return card
    }
}
