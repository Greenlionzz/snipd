package com.mink.app.ui.discover

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.core.EventBus
import com.mink.app.data.model.Podcast
import com.mink.app.net.ImageLoader
import com.mink.app.podcast.PodcastRepository
import com.mink.app.podcast.PodcastsUpdatedEvent
import com.mink.app.podcast.SubscriptionManager
import com.mink.app.podcast.iTunesSearchService
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.podcast.PodcastDetailScreen
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView

class SeeAllListScreen(
    private val title: String,
    private val searchTerm: String,
    private val navigator: ScreenHost,
    private val imageLoader: ImageLoader,
    private val aiService: AiService
) : Screen {

    private lateinit var contentListLayout: LinearLayout
    private var eventSub: Cancelable? = null
    private var currentPodcasts: List<Podcast> = emptyList()

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Color.parseColor("#0F1115"))

            // Header matching screenshot: Back arrow (cyan / light grey) + Title
            val topHeader = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#15181F"))
                setPadding(dp(16), dp(14), dp(20), dp(14))

                val backBtn = ctx.iconButton(
                    icon = Icon.CHEVRON_LEFT,
                    sizeDp = 22,
                    tint = Color.parseColor("#38BDF8")
                ) {
                    navigator.popScreen()
                }.apply {
                    val lp = LinearLayout.LayoutParams(dp(40), dp(40))
                    lp.marginEnd = dp(12)
                    layoutParams = lp
                }
                addView(backBtn)

                val titleTv = ctx.headlineText(title).apply {
                    setTextColor(Color.parseColor("#FFFFFF"))
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(titleTv)
            }
            addView(topHeader)

            // Scrollable list
            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            contentListLayout = ctx.vstack {
                setPadding(dp(20), dp(8), dp(20), dp(32))
            }
            scrollView.addView(contentListLayout)
            addView(scrollView)
        }

        contentListLayout.addView(ctx.skeletonRows(8))
        loadData(ctx)

        return root
    }

    override fun onShow() {
        eventSub = EventBus.subscribe { event ->
            if (event is PodcastsUpdatedEvent) {
                renderList(contentListLayout.context)
            }
        }
    }

    override fun onHide() {
        eventSub?.cancel()
        eventSub = null
    }

    private fun loadData(ctx: Context) {
        iTunesSearchService.search(searchTerm, 50) { result ->
            when (result) {
                is com.mink.app.core.Result.Ok -> {
                    currentPodcasts = result.value
                    renderList(ctx)
                }
                is com.mink.app.core.Result.Err -> {
                    contentListLayout.removeAllViews()
                    contentListLayout.addView(
                        ctx.errorState(
                            message = result.message,
                            onRetry = { loadData(ctx) }
                        )
                    )
                }
            }
        }
    }

    private fun renderList(ctx: Context) {
        contentListLayout.removeAllViews()
        if (currentPodcasts.isEmpty()) {
            contentListLayout.addView(
                ctx.emptyState(
                    icon = Icon.SEARCH,
                    title = "No podcasts found",
                    message = "There are no podcasts currently listed under $title"
                )
            )
        } else {
            for (pod in currentPodcasts) {
                contentListLayout.addView(createPodcastRow(ctx, pod))
            }
        }
    }

    private fun createPodcastRow(ctx: Context, pod: Podcast): View {
        val row = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(80)
            setPadding(0, dp(8), 0, dp(8))
            background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 8)

            // Artwork
            val artIv = ctx.artworkView(sizeDp = 64, radiusDp = 12).apply {
                val alp = LinearLayout.LayoutParams(dp(64), dp(64))
                alp.marginEnd = dp(16)
                layoutParams = alp
            }
            addView(artIv)
            imageLoader.load(pod.artworkUrl, artIv)

            // Text Stack
            val textStack = ctx.vstack {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                val titleTv = ctx.labelText(pod.title).apply {
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 15f
                    maxLines = 1
                }
                addView(titleTv)

                val authorTv = ctx.captionText(pod.author).apply {
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 13f
                    maxLines = 1
                    val alp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    alp.topMargin = dp(3)
                    layoutParams = alp
                }
                addView(authorTv)
            }
            addView(textStack)

            // Dynamic Subscription Toggle Button
            val isSubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
            val btnWrap = FrameLayout(ctx).apply {
                val plp = LinearLayout.LayoutParams(dp(40), dp(40))
                layoutParams = plp
                background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 20)
            }
            val iv = IconView(
                ctx,
                if (isSubbed) Icon.CHECK else Icon.ADD,
                20,
                if (isSubbed) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")
            )
            btnWrap.addView(iv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))

            btnWrap.onTapFeedback {
                toggleSubscription(pod, iv)
            }
            addView(btnWrap)

            onTapFeedback {
                openPodcast(ctx, pod)
            }
        }

        val container = ctx.vstack {
            addView(row)
            val line = View(ctx).apply {
                setBackgroundColor(Color.parseColor("#161A22"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            }
            addView(line)
        }
        return container
    }

    private fun toggleSubscription(pod: Podcast, iconView: IconView) {
        val currentlySubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
        val newSubbed = !currentlySubbed

        // Instant UI feedback on the icon view itself
        iconView.icon = if (newSubbed) Icon.CHECK else Icon.ADD
        iconView.tint = if (newSubbed) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")
        iconView.invalidate()

        if (currentlySubbed) {
            val podId = SubscriptionManager.getPodcastId(pod.feedUrl)
            SubscriptionManager.setSubscribedLocally(pod.feedUrl, false)
            if (podId != null) {
                PodcastRepository.unsubscribe(podId)
            } else {
                AppExecutors.executeIo {
                    val dbPod = AppGraph.get().podcastDao.getByFeedUrl(pod.feedUrl)
                    if (dbPod != null) {
                        AppExecutors.main {
                            PodcastRepository.unsubscribe(dbPod.id)
                        }
                    }
                }
            }
        } else {
            SubscriptionManager.setSubscribedLocally(pod.feedUrl, true)
            PodcastRepository.subscribe(pod) { res ->
                if (res is com.mink.app.core.Result.Ok) {
                    SubscriptionManager.setSubscribedLocally(pod.feedUrl, true, res.value.id)
                } else {
                    SubscriptionManager.setSubscribedLocally(pod.feedUrl, false)
                    iconView.icon = Icon.ADD
                    iconView.tint = Color.parseColor("#94A3B8")
                    iconView.invalidate()
                }
            }
        }
    }

    private fun openPodcast(ctx: Context, pod: Podcast) {
        AppExecutors.executeIo {
            val existing = AppGraph.get().podcastDao.getByFeedUrl(pod.feedUrl)
            val podId = existing?.id ?: AppGraph.get().podcastDao.insertOrUpdate(pod)
            AppExecutors.main {
                navigator.pushScreen(PodcastDetailScreen(podId, navigator, imageLoader, aiService, initialPodcast = pod))
            }
        }
    }
}
