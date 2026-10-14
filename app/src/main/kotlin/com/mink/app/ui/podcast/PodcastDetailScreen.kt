package com.mink.app.ui.podcast

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.TimeFmt
import com.mink.app.data.model.Episode
import com.mink.app.data.model.Podcast
import com.mink.app.net.ImageLoader
import com.mink.app.player.PlaybackManager
import com.mink.app.podcast.DiscoverCache
import com.mink.app.podcast.PodcastRepository
import com.mink.app.podcast.SubscriptionManager
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView

class PodcastDetailScreen(
    private val podcastId: Long,
    private val navigator: ScreenHost,
    private val imageLoader: ImageLoader,
    private val aiService: AiService,
    private val initialPodcast: Podcast? = null
) : Screen {

    private var podcast: Podcast? = initialPodcast
    private var allEpisodes: List<Episode> = emptyList()
    private var filteredEpisodes: List<Episode> = emptyList()

    private var episodesView: View? = null
    private var bookmarksView: View? = null
    private var recommendationsView: View? = null

    private lateinit var contentContainer: LinearLayout
    private lateinit var episodesContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var followButton: TextView
    private lateinit var episodesCountLabel: TextView
    private lateinit var tabsRow: LinearLayout

    private var activeTab = 0 // 0: Episodes, 1: Bookmarks, 2: You might like

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Color.parseColor("#0F1115"))

            // Scrollable Content
            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            contentContainer = ctx.vstack {
                setPadding(0, 0, 0, dp(40))
            }
            scrollView.addView(contentContainer)
            addView(scrollView)
        }

        val initP = initialPodcast
        if (initP != null) {
            renderScreen(ctx, initP, emptyList())
        } else {
            contentContainer.addView(ctx.skeletonRows(6))
        }

        loadData(ctx)
        return root
    }

    private fun loadData(ctx: Context) {
        AppExecutors.executeIo {
            var pod = AppGraph.get().podcastDao.getById(podcastId)
            var episodes = AppGraph.get().episodeDao.getByPodcastId(podcastId)

            val currentPod = pod
            if (episodes.isEmpty() && currentPod != null && currentPod.feedUrl.isNotEmpty()) {
                AppExecutors.main {
                    if (::episodesContainer.isInitialized) {
                        episodesContainer.removeAllViews()
                        episodesContainer.addView(ctx.skeletonRows(6))
                    }
                }
                // Fetch feed to get full episode list without forcing subscription
                PodcastRepository.fetchAndSavePodcast(currentPod, subscribed = currentPod.subscribed) { res ->
                    if (res is com.mink.app.core.Result.Ok) {
                        val savedPod = res.value
                        val savedId = savedPod.id
                        AppExecutors.executeIo {
                            val refreshed = AppGraph.get().episodeDao.getByPodcastId(savedId)
                            AppExecutors.main {
                                podcast = savedPod
                                allEpisodes = refreshed
                                filteredEpisodes = refreshed
                                renderScreen(ctx, savedPod, refreshed)
                            }
                        }
                    } else {
                        AppExecutors.main {
                            if (::episodesContainer.isInitialized) {
                                episodesContainer.removeAllViews()
                                episodesContainer.addView(ctx.emptyState(
                                    icon = Icon.ERROR,
                                    title = "Could not load episodes",
                                    message = "Please check your network connection"
                                ))
                            }
                        }
                    }
                }
            } else {
                AppExecutors.main {
                    val finalPod = pod
                    podcast = finalPod
                    allEpisodes = episodes
                    filteredEpisodes = episodes
                    if (finalPod != null) {
                        renderScreen(ctx, finalPod, episodes)
                    } else {
                        contentContainer.removeAllViews()
                        contentContainer.addView(ctx.errorState("Podcast not found"))
                    }
                }
            }
        }
    }

    private fun renderScreen(ctx: Context, pod: Podcast, episodes: List<Episode>) {
        contentContainer.removeAllViews()

        // 1. Top Radiant Gradient Hero Banner (matching red radiant hero from screenshot)
        val heroBanner = ctx.frame {
            val hlp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = hlp

            val gradientBg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#991B1B"), Color.parseColor("#450A0A"), Color.parseColor("#0F1115"))
            )
            background = gradientBg
            setPadding(dp(20), dp(16), dp(20), dp(28))

            // Action row: Back on left, Cast + Share on right
            val actionRow = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                val alp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = alp

                val backBtn = ctx.iconButton(Icon.CHEVRON_LEFT, sizeDp = 22, tint = Color.WHITE) {
                    navigator.popScreen()
                }
                addView(backBtn)

                val spacer = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                }
                addView(spacer)

                val shareBtn = ctx.iconButton(Icon.SHARE, sizeDp = 20, tint = Color.WHITE) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${pod.title}\n${pod.feedUrl}")
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Share Podcast"))
                }
                addView(shareBtn)
            }
            addView(actionRow)

            // Centered artwork + podcast header details
            val centerColumn = ctx.vstack {
                gravity = Gravity.CENTER_HORIZONTAL
                val clp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                clp.topMargin = dp(24)
                layoutParams = clp

                // Prominent center rounded artwork (160x160dp)
                val artIv = ctx.artworkView(sizeDp = 160, radiusDp = 16).apply {
                    val alp = LinearLayout.LayoutParams(dp(160), dp(160))
                    alp.bottomMargin = dp(14)
                    layoutParams = alp
                    elevation = dp(12).toFloat()
                }
                addView(artIv)
                imageLoader.load(pod.artworkUrl, artIv)

                // Category & Publisher
                val categoryAuthorTv = ctx.captionText("Society & Culture • ${pod.author}").apply {
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                }
                addView(categoryAuthorTv)

                // Podcast Title with chevron
                val titleRow = ctx.hstack {
                    gravity = Gravity.CENTER
                    val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    tlp.topMargin = dp(4)
                    tlp.bottomMargin = dp(6)
                    layoutParams = tlp

                    val titleTv = ctx.displayText(pod.title).apply {
                        setTextColor(Color.WHITE)
                        textSize = 20f
                        gravity = Gravity.CENTER
                    }
                    addView(titleTv)

                    val chev = IconView(ctx, Icon.CHEVRON_DOWN, 14, Color.WHITE).apply {
                        val ilp = LinearLayout.LayoutParams(dp(14), dp(14))
                        ilp.marginStart = dp(6)
                        layoutParams = ilp
                    }
                    addView(chev)
                }
                addView(titleRow)

                // Star Rating row (★★★★☆ 4.4 (97.3K))
                val ratingRow = ctx.hstack {
                    gravity = Gravity.CENTER
                    val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    rlp.bottomMargin = dp(14)
                    layoutParams = rlp

                    val stars = ctx.captionText("★★★★☆ 4.4 (97.3K)").apply {
                        setTextColor(Color.parseColor("#F1F5F9"))
                        textSize = 12f
                    }
                    addView(stars)
                }
                addView(ratingRow)

                // Follow / Following compact pill button
                val isSubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
                followButton = ctx.labelText(if (isSubbed) "Following" else "Follow").apply {
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    background = ctx.roundedDrawable(
                        if (isSubbed) Color.parseColor("#33FFFFFF") else Color.parseColor("#26FFFFFF"),
                        radiusDp = 18,
                        strokeColor = Color.parseColor("#55FFFFFF"),
                        strokeWidthDp = 1
                    )
                    val flp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    flp.topMargin = dp(4)
                    layoutParams = flp
                    padding(h = 24, v = 6)
                    onTapFeedback {
                        toggleFollow(pod)
                    }
                }
                addView(followButton)
            }
            addView(centerColumn)
        }
        contentContainer.addView(heroBanner)

        // 2. Podcast Description text
        if (pod.description.isNotBlank()) {
            val descTv = ctx.bodyText(pod.description).apply {
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 13f
                maxLines = 4
                setPadding(dp(20), 0, dp(20), dp(14))
            }
            contentContainer.addView(descTv)
        }

        // 3. Metadata list card (Author, Website, Schedule, Next Episode)
        val metaCard = ctx.card(bgColor = Color.parseColor("#151820"), radiusDp = 12, strokeColor = Color.parseColor("#222733")) {
            val mlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            mlp.setMargins(dp(20), 0, dp(20), dp(16))
            layoutParams = mlp

            val col = ctx.vstack {
                setPadding(dp(14), dp(12), dp(14), dp(12))

                // Author row
                val authorText = pod.author.ifEmpty { pod.title }
                addView(createMetaRow(ctx, Icon.HEADPHONE, authorText))

                // Website
                val websiteUrl = if (pod.website.isNotBlank()) {
                    pod.website
                } else if (pod.feedUrl.isNotBlank()) {
                    try {
                        val uri = android.net.Uri.parse(pod.feedUrl)
                        uri.host ?: pod.feedUrl
                    } catch (_: Exception) {
                        pod.feedUrl
                    }
                } else {
                    ""
                }
                if (websiteUrl.isNotBlank()) {
                    val displayWeb = websiteUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
                    addView(createMetaRow(ctx, Icon.DISCOVER, displayWeb, isLink = true) {
                        try {
                            val openUrl = if (!websiteUrl.startsWith("http")) "https://$websiteUrl" else websiteUrl
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(openUrl))
                            ctx.startActivity(intent)
                        } catch (_: Exception) {}
                    })
                }

                // Schedule / Release Frequency based on episode publishing history
                val (frequencyText, nextEpText) = calculateReleaseSchedule(episodes)
                addView(createMetaRow(ctx, Icon.PLAYLIST, frequencyText))

                // Next episode expected date
                if (nextEpText.isNotBlank()) {
                    addView(createMetaRow(ctx, Icon.SLEEP, nextEpText))
                }
            }
            addView(col)
        }
        contentContainer.addView(metaCard)

        // 4. Tab Navigation: Episodes | Bookmarks | You might like
        val tabsRow = ctx.hstack {
            setPadding(dp(20), dp(4), dp(20), dp(12))

            addView(createTabItem(ctx, "Episodes", activeTab == 0) {
                activeTab = 0
                renderTabContent(ctx, pod)
            })
            addView(createTabItem(ctx, "Bookmarks", activeTab == 1) {
                activeTab = 1
                renderTabContent(ctx, pod)
            })
            addView(createTabItem(ctx, "You might like", activeTab == 2) {
                activeTab = 2
                renderTabContent(ctx, pod)
            })
        }
        contentContainer.addView(tabsRow)

        // 5. Search Episodes Bar with more vert
        val searchRow = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), dp(12))

            val searchBox = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                background = ctx.roundedDrawable(Color.parseColor("#1B1F28"), radiusDp = 20)
                setPadding(dp(14), 0, dp(14), 0)
                minimumHeight = dp(42)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)

                val sIcon = IconView(ctx, Icon.SEARCH, 16, Color.parseColor("#7D8597")).apply {
                    val lp = LinearLayout.LayoutParams(dp(16), dp(16))
                    lp.marginEnd = dp(10)
                    layoutParams = lp
                }
                addView(sIcon)

                searchInput = EditText(ctx).apply {
                    hint = "Search episodes"
                    setTextColor(Color.parseColor("#F1F5F9"))
                    setHintTextColor(Color.parseColor("#64748B"))
                    background = ctx.roundedDrawable(Color.TRANSPARENT, radiusDp = 0)
                    textSize = 14f
                    setSingleLine()
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) {
                            filterEpisodes(s?.toString().orEmpty())
                        }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    })
                }
                addView(searchInput)
            }
            addView(searchBox)

            val moreBtn = ctx.iconButton(Icon.MORE_VERT, sizeDp = 20, tint = Color.parseColor("#94A3B8")) {}.apply {
                val mlp = LinearLayout.LayoutParams(dp(38), dp(38))
                mlp.marginStart = dp(8)
                layoutParams = mlp
            }
            addView(moreBtn)
        }
        contentContainer.addView(searchRow)

        // 6. Episode counter & Show archived row
        val countRow = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))

            episodesCountLabel = ctx.captionText("${episodes.size} episodes • 0 archived").apply {
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(episodesCountLabel)

            val archivedBtn = ctx.captionText("Show archived").apply {
                setTextColor(Color.parseColor("#F87171"))
                textSize = 12f
            }
            addView(archivedBtn)
        }
        contentContainer.addView(countRow)

        // 7. Episodes container
        episodesContainer = ctx.vstack {
            setPadding(dp(20), 0, dp(20), 0)
        }
        contentContainer.addView(episodesContainer)

        episodesView = buildEpisodesView(ctx, pod, episodes)
        bookmarksView = null
        recommendationsView = null
        renderTabContent(ctx, pod)
    }

    private fun renderTabContent(ctx: Context, pod: Podcast) {
        episodesContainer.removeAllViews()
        when (activeTab) {
            0 -> {
                if (episodesView == null) {
                    episodesView = buildEpisodesView(ctx, pod, filteredEpisodes)
                }
                episodesContainer.addView(episodesView)
            }
            1 -> {
                if (bookmarksView == null) {
                    bookmarksView = ctx.emptyState(
                        icon = Icon.STAR,
                        title = "No bookmarks yet",
                        message = "Bookmark episodes to find them quickly here"
                    )
                }
                episodesContainer.addView(bookmarksView)
            }
            2 -> {
                if (recommendationsView == null) {
                    recommendationsView = buildRecommendationsView(ctx, pod)
                }
                episodesContainer.addView(recommendationsView)
            }
        }
    }

    private fun filterEpisodes(query: String) {
        val q = query.trim().lowercase()
        filteredEpisodes = if (q.isEmpty()) {
            allEpisodes
        } else {
            allEpisodes.filter {
                it.title.lowercase().contains(q) || it.descriptionHtml.lowercase().contains(q)
            }
        }
        episodesCountLabel.text = "${filteredEpisodes.size} episodes • 0 archived"
        podcast?.let { pod ->
            episodesView = buildEpisodesView(episodesContainer.context, pod, filteredEpisodes)
            if (activeTab == 0) {
                renderTabContent(episodesContainer.context, pod)
            }
        }
    }

    private fun buildEpisodesView(ctx: Context, pod: Podcast, episodes: List<Episode>): View {
        val container = ctx.vstack {
            setPadding(0, 0, 0, 0)
        }
        if (episodes.isEmpty()) {
            container.addView(ctx.emptyState(
                icon = Icon.EPISODE,
                title = "No episodes found",
                message = "Try searching with a different term"
            ))
            return container
        }

        for (ep in episodes) {
            container.addView(createEpisodeRow(ctx, ep, pod))
        }
        return container
    }

    private fun buildRecommendationsView(ctx: Context, pod: Podcast): View {
        val container = ctx.vstack {
            setPadding(0, dp(8), 0, dp(16))
        }
        val recommendedPods = DiscoverCache.podcastCatalogByFeed.values.filter { it.feedUrl != pod.feedUrl }.take(8)
        val list = if (recommendedPods.isNotEmpty()) recommendedPods else listOf(
            Podcast(id = 0L, feedUrl = "https://feeds.megaphone.fm/STW5478177266", title = "The Daily", author = "The New York Times", description = "Daily news from NYT", artworkUrl = "", website = "", itunesId = 0L, subscribed = false, lastFetched = 0L),
            Podcast(id = 0L, feedUrl = "https://feeds.npr.org/510289/podcast.xml", title = "Planet Money", author = "NPR", description = "Economic insights", artworkUrl = "", website = "", itunesId = 0L, subscribed = false, lastFetched = 0L),
            Podcast(id = 0L, feedUrl = "https://feeds.megaphone.fm/vergecast", title = "The Vergecast", author = "The Verge", description = "Technology news and analysis", artworkUrl = "", website = "", itunesId = 0L, subscribed = false, lastFetched = 0L)
        )

        val titleTv = ctx.labelText("You might also like").apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 15f
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tlp.bottomMargin = dp(12)
            layoutParams = tlp
        }
        container.addView(titleTv)

        for (recPod in list) {
            val row = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(0, dp(6), 0, dp(6))
                background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 8)

                val artIv = ctx.artworkView(sizeDp = 50, radiusDp = 8).apply {
                    val alp = LinearLayout.LayoutParams(dp(50), dp(50))
                    alp.marginEnd = dp(14)
                    layoutParams = alp
                }
                addView(artIv)
                imageLoader.load(recPod.artworkUrl, artIv)

                val textStack = ctx.vstack {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val tTv = ctx.labelText(recPod.title).apply {
                        setTextColor(Color.parseColor("#F1F5F9"))
                        textSize = 14f
                        maxLines = 1
                    }
                    addView(tTv)

                    val aTv = ctx.captionText(recPod.author).apply {
                        setTextColor(Color.parseColor("#94A3B8"))
                        textSize = 12f
                        maxLines = 1
                        val alp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        alp.topMargin = dp(2)
                        layoutParams = alp
                    }
                    addView(aTv)
                }
                addView(textStack)

                onTapFeedback {
                    navigator.pushScreen(PodcastDetailScreen(recPod.id, navigator, imageLoader, aiService, initialPodcast = recPod))
                }
            }
            container.addView(row)
        }
        return container
    }



    /**
     * Minimalist clean episode row matching the screenshot:
     * - Date & Episode Number in red/pink overline (e.g. "E427 • OCTOBER 12")
     * - Bold episode title
     * - Duration (e.g. "1h 3m")
     * - Circular Play Icon on the right
     * - 1dp bottom divider
     */
    private fun createEpisodeRow(ctx: Context, ep: Episode, pod: Podcast): View {
        val container = ctx.vstack {
            val row = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(0, dp(10), 0, dp(10))
                background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 8)

                val textStack = ctx.vstack {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    // Episode header: e.g. "E427 • OCTOBER 12"
                    val dateStr = TimeFmt.formatRelativeTime(ep.publishedAt).uppercase()
                    val headerTv = ctx.overlineText(dateStr).apply {
                        setTextColor(Color.parseColor("#F87171"))
                        textSize = 11f
                    }
                    addView(headerTv)

                    // Title
                    val titleTv = ctx.labelText(ep.title).apply {
                        setTextColor(Color.parseColor("#F1F5F9"))
                        textSize = 15f
                        maxLines = 2
                        val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        tlp.topMargin = dp(2)
                        tlp.bottomMargin = dp(2)
                        layoutParams = tlp
                    }
                    addView(titleTv)

                    // Duration
                    val durationStr = TimeFmt.formatDuration(ep.durationSec)
                    val durTv = ctx.captionText(durationStr).apply {
                        setTextColor(Color.parseColor("#94A3B8"))
                        textSize = 12f
                    }
                    addView(durTv)
                }
                addView(textStack)

                // Circular play button matching screenshot outline
                val playBtn = ctx.frame {
                    val plp = LinearLayout.LayoutParams(dp(36), dp(36))
                    plp.marginStart = dp(14)
                    layoutParams = plp
                    background = ctx.roundedDrawable(Color.TRANSPARENT, radiusDp = 18, strokeColor = Color.parseColor("#F87171"), strokeWidthDp = 1)

                    val playIcon = IconView(ctx, Icon.PLAY, 14, Color.parseColor("#F87171"))
                    addView(playIcon, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER))

                    onTapFeedback {
                        PlaybackManager.playEpisode(ep, pod)
                    }
                }
                addView(playBtn)

                onTapFeedback {
                    PlaybackManager.playEpisode(ep, pod)
                }
            }
            addView(row)

            val line = View(ctx).apply {
                setBackgroundColor(Color.parseColor("#171A22"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            }
            addView(line)
        }
        return container
    }

    private fun createMetaRow(
        ctx: Context,
        icon: Icon,
        text: String,
        isLink: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        return ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))

            val iv = IconView(ctx, icon, 14, Color.parseColor("#7D8597")).apply {
                val lp = LinearLayout.LayoutParams(dp(14), dp(14))
                lp.marginEnd = dp(10)
                layoutParams = lp
            }
            addView(iv)

            val tv = ctx.captionText(text).apply {
                setTextColor(if (isLink) Color.parseColor("#F87171") else Color.parseColor("#CBD5E1"))
                textSize = 12f
            }
            addView(tv)

            if (onClick != null) {
                onTapFeedback(onClick)
            }
        }
    }

    private fun calculateReleaseSchedule(episodes: List<Episode>): Pair<String, String> {
        if (episodes.isEmpty()) {
            return Pair("Episodes on demand", "")
        }
        val sortedDates = episodes.map { it.publishedAt }.filter { it > 0 }.sortedDescending()
        if (sortedDates.size < 2) {
            val lastDateStr = if (sortedDates.isNotEmpty()) {
                val sdf = java.text.SimpleDateFormat("MMMM d", java.util.Locale.US)
                "Latest episode ${sdf.format(java.util.Date(sortedDates[0]))}"
            } else "Episodes on demand"
            return Pair(lastDateStr, "")
        }

        // Calculate average gap in days between recent episodes (up to 5 gaps)
        val gaps = mutableListOf<Long>()
        for (i in 0 until (sortedDates.size - 1).coerceAtMost(5)) {
            val diffMs = sortedDates[i] - sortedDates[i + 1]
            if (diffMs > 0) {
                gaps.add(diffMs / (1000 * 60 * 60 * 24))
            }
        }
        val avgDays = if (gaps.isNotEmpty()) gaps.average().toLong() else 7L

        val freqText = when {
            avgDays <= 2 -> "Released daily"
            avgDays in 3..5 -> "Released semi-weekly"
            avgDays in 6..9 -> "Released weekly"
            avgDays in 10..18 -> "Released fortnightly"
            avgDays in 19..35 -> "Released monthly"
            else -> "Released periodically"
        }

        // Project next episode date
        val lastEpTime = sortedDates[0]
        val expectedNextMs = lastEpTime + (avgDays * 24 * 60 * 60 * 1000)
        val sdf = java.text.SimpleDateFormat("MMMM d", java.util.Locale.US)
        val nextEpText = "Next episode ~${sdf.format(java.util.Date(expectedNextMs))}"

        return Pair(freqText, nextEpText)
    }

    private fun createTabItem(ctx: Context, title: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        return ctx.labelText(title).apply {
            setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#64748B"))
            textSize = 14f
            setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(20)
            layoutParams = lp
            onTapFeedback(onClick)
        }
    }

    private fun rebuildTabs(ctx: Context, pod: Podcast) {
        if (!::tabsRow.isInitialized) return
        tabsRow.removeAllViews()
        tabsRow.addView(createTabItem(ctx, "Episodes", activeTab == 0) {
            activeTab = 0
            rebuildTabs(ctx, pod)
            renderTabContent(ctx, pod)
        })
        tabsRow.addView(createTabItem(ctx, "Bookmarks", activeTab == 1) {
            activeTab = 1
            rebuildTabs(ctx, pod)
            renderTabContent(ctx, pod)
        })
        tabsRow.addView(createTabItem(ctx, "You might like", activeTab == 2) {
            activeTab = 2
            rebuildTabs(ctx, pod)
            renderTabContent(ctx, pod)
        })
    }

    private fun toggleFollow(pod: Podcast) {
        val isSubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
        if (isSubbed) {
            val podId = SubscriptionManager.getPodcastId(pod.feedUrl) ?: pod.id
            SubscriptionManager.setSubscribedLocally(pod.feedUrl, false)
            updateFollowButton(false)
            PodcastRepository.unsubscribe(podId)
        } else {
            SubscriptionManager.setSubscribedLocally(pod.feedUrl, true)
            updateFollowButton(true)
            PodcastRepository.subscribe(pod) { res ->
                if (res is com.mink.app.core.Result.Ok) {
                    SubscriptionManager.setSubscribedLocally(pod.feedUrl, true, res.value.id)
                } else {
                    SubscriptionManager.setSubscribedLocally(pod.feedUrl, false)
                    updateFollowButton(false)
                }
            }
        }
    }

    private fun updateFollowButton(isSubscribed: Boolean) {
        if (::followButton.isInitialized) {
            followButton.text = if (isSubscribed) "Following" else "Follow"
            followButton.background = followButton.context.roundedDrawable(
                if (isSubscribed) Color.parseColor("#33FFFFFF") else Color.parseColor("#26FFFFFF"),
                radiusDp = 18,
                strokeColor = Color.parseColor("#55FFFFFF"),
                strokeWidthDp = 1
            )
        }
    }
}
