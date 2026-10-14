package com.mink.app.ui.discover

import android.content.Context
import android.graphics.Color
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
import com.mink.app.podcast.DiscoverCache
import com.mink.app.podcast.PodcastRepository
import com.mink.app.podcast.PodcastsUpdatedEvent
import com.mink.app.podcast.SubscriptionChangedEvent
import com.mink.app.podcast.SubscriptionManager
import com.mink.app.podcast.iTunesSearchService
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.podcast.PodcastDetailScreen
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView

class DiscoverScreen(
    private val navigator: ScreenHost,
    private val imageLoader: ImageLoader,
    private val aiService: AiService
) : Screen {

    private lateinit var searchInput: EditText
    private lateinit var dynamicSectionsContainer: LinearLayout
    private lateinit var heroCarouselLayout: LinearLayout
    private lateinit var heroScrollView: HorizontalScrollView
    private lateinit var heroDotsContainer: LinearLayout
    private lateinit var chipsRow: LinearLayout

    private var eventSub: Cancelable? = null
    private var subChangedSub: Cancelable? = null
    private var activeCategory = "All categories"

    // Map to keep track of active icon views per feedUrl for instantaneous UI updates
    private val subActionViews = mutableMapOf<String, MutableList<IconView>>()
    private val subBadgeBackgrounds = mutableMapOf<String, MutableList<View>>()

    private val categories = listOf(
        "All categories",
        "True Crime",
        "Comedy",
        "Society & Culture",
        "History",
        "Fiction",
        "Technology",
        "News"
    )

    private val heroGradients = listOf(
        Pair(Color.parseColor("#4A1515"), "SPONSORED"),
        Pair(Color.parseColor("#1B3B2B"), "FEATURED"),
        Pair(Color.parseColor("#1C2E4A"), "MUST LISTEN"),
        Pair(Color.parseColor("#3E204A"), "EDITOR'S PICK"),
        Pair(Color.parseColor("#423214"), "NEW & NOTABLE")
    )

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Color.parseColor("#0F1115"))

            // 1. Top Search Bar: Full width, tablet/landscape responsive
            val searchBarContainer = ctx.hstack {
                setPadding(dp(20), dp(16), dp(20), dp(10))
                gravity = Gravity.CENTER_VERTICAL

                val searchBox = ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    background = ctx.roundedDrawable(Color.parseColor("#20242D"), radiusDp = 24)
                    setPadding(dp(18), 0, dp(18), 0)
                    minimumHeight = dp(50)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(50)
                    )

                    val searchIcon = IconView(ctx, Icon.SEARCH, 20, Color.parseColor("#8E95A5")).apply {
                        val lp = LinearLayout.LayoutParams(dp(20), dp(20))
                        lp.marginEnd = dp(14)
                        layoutParams = lp
                    }
                    addView(searchIcon)

                    searchInput = EditText(ctx).apply {
                        hint = "Search podcasts or add RSS URL"
                        setTextColor(Color.parseColor("#F1F5F9"))
                        setHintTextColor(Color.parseColor("#7D8597"))
                        background = ctx.roundedDrawable(Color.TRANSPARENT, radiusDp = 0)
                        textSize = 15f
                        setSingleLine()
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, 0)
                        includeFontPadding = false
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        setOnEditorActionListener { _, _, _ ->
                            val query = text.toString().trim()
                            if (query.isNotEmpty()) performSearch(ctx, query)
                            true
                        }
                    }
                    addView(searchInput)
                }
                addView(searchBox)
            }
            addView(searchBarContainer)

            // 2. Horizontal Filter Categories
            val chipScroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                slp.bottomMargin = dp(14)
                layoutParams = slp
            }
            chipsRow = ctx.hstack {
                setPadding(dp(20), 0, dp(20), 0)
            }
            rebuildChips(ctx)
            chipScroll.addView(chipsRow)
            addView(chipScroll)

            // 3. Scrollable Content Area: Hero Swiper + All sections
            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val contentColumn = ctx.vstack {
                setPadding(0, 0, 0, dp(32))

                // Swipeable Horizontal Hero Carousel
                heroScrollView = HorizontalScrollView(ctx).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = lp
                }
                heroCarouselLayout = ctx.hstack {
                    setPadding(dp(20), 0, dp(20), 0)
                }
                heroScrollView.addView(heroCarouselLayout)
                addView(heroScrollView)

                // Carousel Dot Indicators
                heroDotsContainer = ctx.hstack {
                    gravity = Gravity.CENTER
                    val dlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    dlp.topMargin = dp(12)
                    dlp.bottomMargin = dp(24)
                    layoutParams = dlp
                }
                addView(heroDotsContainer)

                // Dynamic Sections Container
                dynamicSectionsContainer = ctx.vstack {}
                addView(dynamicSectionsContainer)

                // Bottom Content Region Selector matching screenshot
                val regionRow = ctx.vstack {
                    gravity = Gravity.CENTER_HORIZONTAL
                    val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    rlp.topMargin = dp(32)
                    rlp.bottomMargin = dp(16)
                    layoutParams = rlp

                    addView(ctx.captionText("Select Content Region").apply {
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 12f
                    })

                    val globeChip = ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        background = ctx.roundedDrawable(Color.parseColor("#1E293B"), radiusDp = 18)
                        setPadding(dp(14), dp(8), dp(16), dp(8))
                        val glp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        glp.topMargin = dp(8)
                        layoutParams = glp

                        val globeIcon = IconView(ctx, Icon.DISCOVER, 14, Color.parseColor("#38BDF8")).apply {
                            val ilp = LinearLayout.LayoutParams(dp(14), dp(14))
                            ilp.marginEnd = dp(6)
                            layoutParams = ilp
                        }
                        addView(globeIcon)

                        val regionText = ctx.labelText("Worldwide").apply {
                            setTextColor(Color.parseColor("#F1F5F9"))
                            textSize = 12f
                        }
                        addView(regionText)
                    }
                    addView(globeChip)
                }
                addView(regionRow)
            }

            scrollView.addView(contentColumn)
            addView(scrollView)
        }

        // Show initial loading skeleton and load all full discovery sections
        populateInitialSkeletons(root.context)
        loadFullDiscoverCatalog(root.context)

        return root
    }

    override fun onShow() {
        eventSub = EventBus.subscribe { event ->
            if (event is PodcastsUpdatedEvent) {
                // Subscription state changed in DB; sync state
                SubscriptionManager.refresh {
                    updateAllTrackedIcons()
                }
            }
            if (event is SubscriptionChangedEvent) {
                updateTrackedIconsForUrl(event.feedUrl, event.isSubscribed)
            }
        }
    }

    private fun registerIconForFeed(feedUrl: String, iconView: IconView, badgeBg: View? = null) {
        val list = subActionViews.getOrPut(feedUrl) { mutableListOf() }
        list.add(iconView)
        if (badgeBg != null) {
            val bgList = subBadgeBackgrounds.getOrPut(feedUrl) { mutableListOf() }
            bgList.add(badgeBg)
        }
    }

    private fun updateTrackedIconsForUrl(feedUrl: String, isSubscribed: Boolean) {
        val iconViews = subActionViews[feedUrl] ?: return
        val bgViews = subBadgeBackgrounds[feedUrl]

        for (iv in iconViews) {
            iv.icon = if (isSubscribed) Icon.CHECK else Icon.ADD
            iv.tint = if (isSubscribed) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")
            iv.invalidate()
        }

        if (bgViews != null) {
            for (bg in bgViews) {
                val color = if (isSubscribed) Color.parseColor("#E6152A1D") else Color.parseColor("#CC121418")
                bg.background = bg.context.roundedDrawable(color, radiusDp = 15)
            }
        }
    }

    private fun updateAllTrackedIcons() {
        for ((url, _) in subActionViews) {
            val isSubbed = SubscriptionManager.isSubscribed(url)
            updateTrackedIconsForUrl(url, isSubbed)
        }
    }

    override fun onHide() {
        eventSub?.cancel()
        eventSub = null
        subActionViews.clear()
        subBadgeBackgrounds.clear()
    }

    private fun populateInitialSkeletons(ctx: Context) {
        if (DiscoverCache.hasData()) return

        heroCarouselLayout.removeAllViews()
        heroCarouselLayout.addView(createHeroCardPlaceholder(ctx, "Loading trending spotlight...", "Please wait", Color.parseColor("#261B2E")))

        dynamicSectionsContainer.removeAllViews()
        dynamicSectionsContainer.addView(createSectionHeader(ctx, "Trending", "SHOW ALL") {})
        dynamicSectionsContainer.addView(ctx.skeletonRows(4))
    }

    private fun loadFullDiscoverCatalog(ctx: Context) {
        if (DiscoverCache.hasData()) {
            // Restore immediately from memory cache without refetching from network
            val heroList = DiscoverCache.heroPodcasts.orEmpty()
            val trendingList = DiscoverCache.trendingPodcasts.orEmpty()

            setupHeroCarousel(ctx, heroList)

            dynamicSectionsContainer.removeAllViews()

            // Trending Section
            dynamicSectionsContainer.addView(createSectionHeader(ctx, "Trending", "SHOW ALL") {
                navigator.pushScreen(SeeAllListScreen("Trending", "Trending", navigator, imageLoader, aiService))
            })
            val trendingCol = ctx.vstack {
                setPadding(dp(20), 0, dp(20), dp(16))
                for (p in trendingList) addView(createCompactRow(ctx, p))
            }
            dynamicSectionsContainer.addView(trendingCol)

            // Sponsored Card
            val promoPod = DiscoverCache.promoPodcast
            val promoCard = createMiniHeroBanner(
                ctx,
                badge = "SPONSORED",
                title = "The Perfect Scam",
                desc = "Explore the world of scams with Bob Sullivan, independent journalist & author.",
                artworkUrl = promoPod?.artworkUrl.orEmpty(),
                bgColor = Color.parseColor("#381111")
            )
            dynamicSectionsContainer.addView(promoCard)

            // Shelves from cache
            for ((title, pods) in DiscoverCache.shelves) {
                val header = createSectionHeader(ctx, title, "SHOW ALL") {
                    navigator.pushScreen(SeeAllListScreen(title, title, navigator, imageLoader, aiService))
                }
                val shelf = createHorizontalShelf(ctx, pods)
                dynamicSectionsContainer.addView(header)
                dynamicSectionsContainer.addView(shelf)
            }

            // Popular from cache
            DiscoverCache.popularPodcasts?.let { loadPopularSection(ctx, it) }
            return
        }

        // Fetch trending podcasts for Hero and Top List on first app launch
        iTunesSearchService.search("podcast", 25) { heroRes ->
            if (heroRes is com.mink.app.core.Result.Ok) {
                val allPods = heroRes.value
                if (allPods.isNotEmpty()) {
                    val heroList = allPods.take(5)
                    val trendingPods = allPods.drop(5).take(4)
                    val promoPod = if (allPods.size > 10) allPods[10] else null

                    DiscoverCache.heroPodcasts = heroList
                    DiscoverCache.trendingPodcasts = trendingPods
                    DiscoverCache.promoPodcast = promoPod
                    DiscoverCache.cachePodcasts(allPods)

                    setupHeroCarousel(ctx, heroList)

                    dynamicSectionsContainer.removeAllViews()

                    // Section 1: Trending (compact vertical list)
                    dynamicSectionsContainer.addView(createSectionHeader(ctx, "Trending", "SHOW ALL") {
                        navigator.pushScreen(SeeAllListScreen("Trending", "Trending", navigator, imageLoader, aiService))
                    })
                    val trendingCol = ctx.vstack {
                        setPadding(dp(20), 0, dp(20), dp(16))
                        for (p in trendingPods) addView(createCompactRow(ctx, p))
                    }
                    dynamicSectionsContainer.addView(trendingCol)

                    // Section 2: Sponsored mini hero card (The Perfect Scam)
                    val promoCard = createMiniHeroBanner(
                        ctx,
                        badge = "SPONSORED",
                        title = "The Perfect Scam",
                        desc = "Explore the world of scams with Bob Sullivan, independent journalist & author.",
                        artworkUrl = promoPod?.artworkUrl.orEmpty(),
                        bgColor = Color.parseColor("#381111")
                    )
                    dynamicSectionsContainer.addView(promoCard)

                    // Fetch and build horizontal shelf sections
                    loadShelf(ctx, "You Might Like", "recommendation")
                    loadShelf(ctx, "British Podcast Awards Winners 2026", "bbc radio")
                    loadShelf(ctx, "History in One Sitting", "history")
                    loadShelf(ctx, "Made in Australia", "australia")
                    loadShelf(ctx, "Guest Curator", "tim ferriss")
                    loadShelf(ctx, "Staff Picks", "editor")

                    // Bottom Popular section
                    val popularPods = allPods.takeLast(4)
                    DiscoverCache.popularPodcasts = popularPods
                    loadPopularSection(ctx, popularPods)
                }
            }
        }
    }

    private fun loadShelf(ctx: Context, title: String, searchTerm: String) {
        val cached = DiscoverCache.shelves[title]
        if (!cached.isNullOrEmpty()) {
            val header = createSectionHeader(ctx, title, "SHOW ALL") {
                navigator.pushScreen(SeeAllListScreen(title, searchTerm, navigator, imageLoader, aiService))
            }
            val shelf = createHorizontalShelf(ctx, cached)
            dynamicSectionsContainer.addView(header)
            dynamicSectionsContainer.addView(shelf)
            return
        }

        iTunesSearchService.search(searchTerm, 12) { res ->
            if (res is com.mink.app.core.Result.Ok && res.value.isNotEmpty()) {
                DiscoverCache.shelves[title] = res.value
                DiscoverCache.cachePodcasts(res.value)

                val header = createSectionHeader(ctx, title, "SHOW ALL") {
                    navigator.pushScreen(SeeAllListScreen(title, searchTerm, navigator, imageLoader, aiService))
                }
                val shelf = createHorizontalShelf(ctx, res.value)
                dynamicSectionsContainer.addView(header)
                dynamicSectionsContainer.addView(shelf)
            }
        }
    }

    private fun loadPopularSection(ctx: Context, pods: List<Podcast>) {
        val header = createSectionHeader(ctx, "Popular", "SHOW ALL") {
            navigator.pushScreen(SeeAllListScreen("Popular", "popular", navigator, imageLoader, aiService))
        }
        val col = ctx.vstack {
            setPadding(dp(20), 0, dp(20), dp(16))
            for (p in pods) {
                addView(createCompactRow(ctx, p, showCheck = true))
            }
        }
        dynamicSectionsContainer.addView(header)
        dynamicSectionsContainer.addView(col)
    }

    private fun setupHeroCarousel(ctx: Context, heroList: List<Podcast>) {
        heroCarouselLayout.removeAllViews()
        heroDotsContainer.removeAllViews()

        heroList.forEachIndexed { idx, pod ->
            val gradConfig = heroGradients[idx % heroGradients.size]
            val card = createHeroCard(
                ctx = ctx,
                podcast = pod,
                badge = gradConfig.second,
                bgColor = gradConfig.first
            )
            heroCarouselLayout.addView(card)

            val dot = View(ctx).apply {
                val dotSize = dp(6)
                val lp = LinearLayout.LayoutParams(dotSize, dotSize)
                lp.setMargins(dp(3), 0, dp(3), 0)
                layoutParams = lp
                background = ctx.roundedDrawable(
                    if (idx == 0) Color.parseColor("#38BDF8") else Color.parseColor("#3B4252"),
                    radiusDp = 3
                )
            }
            heroDotsContainer.addView(dot)
        }
    }

    private fun createHeroCard(ctx: Context, podcast: Podcast, badge: String, bgColor: Int): View {
        val screenWidth = ctx.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth - ctx.dp(40)).coerceAtMost(ctx.dp(640))

        val card = ctx.frame {
            val lp = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(14)
            layoutParams = lp
            background = ctx.roundedDrawable(bgColor, radiusDp = 12)
            setPadding(dp(18), dp(18), dp(18), dp(18))

            val row = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL

                val artIv = ctx.artworkView(sizeDp = 92, radiusDp = 10).apply {
                    val alp = LinearLayout.LayoutParams(dp(92), dp(92))
                    alp.marginEnd = dp(16)
                    layoutParams = alp
                }
                addView(artIv)
                imageLoader.load(podcast.artworkUrl, artIv)

                val textStack = ctx.vstack {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val badgeTv = ctx.overlineText(badge).apply {
                        setTextColor(Color.parseColor("#F87171"))
                        textSize = 11f
                        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        blp.bottomMargin = dp(4)
                        layoutParams = blp
                    }
                    addView(badgeTv)

                    val titleTv = ctx.headlineText(podcast.title).apply {
                        setTextColor(Color.parseColor("#FFFFFF"))
                        textSize = 17f
                        maxLines = 2
                    }
                    addView(titleTv)

                    val authorTv = ctx.captionText(podcast.author).apply {
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 13f
                        maxLines = 1
                        val alp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        alp.topMargin = dp(4)
                        layoutParams = alp
                    }
                    addView(authorTv)
                }
                addView(textStack)

                val isSubbed = SubscriptionManager.isSubscribed(podcast.feedUrl)
                val btnWrap = FrameLayout(ctx).apply {
                    val plp = LinearLayout.LayoutParams(dp(40), dp(40))
                    plp.marginStart = dp(8)
                    layoutParams = plp
                    background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 20)
                }
                val iv = IconView(
                    ctx,
                    if (isSubbed) Icon.CHECK else Icon.ADD,
                    22,
                    if (isSubbed) Color.parseColor("#4ADE80") else Color.parseColor("#E2E8F0")
                )
                btnWrap.addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                registerIconForFeed(podcast.feedUrl, iv)

                btnWrap.onTapFeedback {
                    toggleSubscription(podcast)
                }
                addView(btnWrap)
            }
            addView(row)

            onTapFeedback {
                openPodcast(ctx, podcast)
            }
        }
        return card
    }

    private fun createHeroCardPlaceholder(ctx: Context, title: String, author: String, bgColor: Int): View {
        val screenWidth = ctx.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth - ctx.dp(40)).coerceAtMost(ctx.dp(640))

        return ctx.frame {
            val lp = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            background = ctx.roundedDrawable(bgColor, radiusDp = 12)
            setPadding(dp(18), dp(18), dp(18), dp(18))

            val row = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                val artIv = ctx.artworkView(sizeDp = 92, radiusDp = 10).apply {
                    val alp = LinearLayout.LayoutParams(dp(92), dp(92))
                    alp.marginEnd = dp(16)
                    layoutParams = alp
                    setBackgroundColor(Color.parseColor("#33FFFFFF"))
                }
                addView(artIv)

                val textStack = ctx.vstack {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(ctx.headlineText(title).apply { setTextColor(Color.WHITE); textSize = 17f })
                    addView(ctx.captionText(author).apply { setTextColor(Color.LTGRAY); textSize = 13f })
                }
                addView(textStack)
            }
            addView(row)
        }
    }

    private fun createMiniHeroBanner(
        ctx: Context,
        badge: String,
        title: String,
        desc: String,
        artworkUrl: String,
        bgColor: Int
    ): View {
        val banner = ctx.frame {
            val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            blp.setMargins(dp(20), dp(8), dp(20), dp(20))
            layoutParams = blp
            background = ctx.roundedDrawable(bgColor, radiusDp = 10)
            setPadding(dp(14), dp(14), dp(14), dp(14))

            val row = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL

                val artIv = ctx.artworkView(sizeDp = 52, radiusDp = 8).apply {
                    val alp = LinearLayout.LayoutParams(dp(52), dp(52))
                    alp.marginEnd = dp(14)
                    layoutParams = alp
                }
                addView(artIv)
                if (artworkUrl.isNotEmpty()) imageLoader.load(artworkUrl, artIv)

                val textStack = ctx.vstack {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val badgeTv = ctx.overlineText(badge).apply {
                        setTextColor(Color.parseColor("#F87171"))
                        textSize = 10f
                    }
                    addView(badgeTv)

                    val titleTv = ctx.labelText(title).apply {
                        setTextColor(Color.WHITE)
                        textSize = 14f
                    }
                    addView(titleTv)

                    val descTv = ctx.captionText(desc).apply {
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 12f
                        maxLines = 2
                    }
                    addView(descTv)
                }
                addView(textStack)

                val plusBtn = ctx.iconButton(Icon.ADD, sizeDp = 18, tint = Color.parseColor("#94A3B8")) {}.apply {
                    val plp = LinearLayout.LayoutParams(dp(36), dp(36))
                    layoutParams = plp
                }
                addView(plusBtn)
            }
            addView(row)
        }
        return banner
    }

    private fun createSectionHeader(ctx: Context, title: String, actionText: String, onAction: () -> Unit): LinearLayout {
        return ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))

            val titleTv = ctx.headlineText(title).apply {
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleTv)

            val actionTv = ctx.labelText(actionText).apply {
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                onTapFeedback(onAction)
            }
            addView(actionTv)
        }
    }

    private fun createHorizontalShelf(ctx: Context, podcasts: List<Podcast>): HorizontalScrollView {
        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            slp.bottomMargin = dp(16)
            layoutParams = slp
        }

        val row = ctx.hstack {
            setPadding(dp(20), 0, dp(20), 0)
            for (pod in podcasts) {
                addView(createShelfCard(ctx, pod))
            }
        }
        scroll.addView(row)
        return scroll
    }

    private fun createShelfCard(ctx: Context, pod: Podcast): View {
        val cardWidth = ctx.dp(148)
        val card = ctx.vstack {
            val lp = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(14)
            layoutParams = lp

            val artContainer = ctx.frame {
                val alp = LinearLayout.LayoutParams(cardWidth, cardWidth)
                layoutParams = alp

                val artIv = ctx.artworkView(sizeDp = 148, radiusDp = 12).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                addView(artIv)
                imageLoader.load(pod.artworkUrl, artIv)

                // Subscription toggle button inside bottom-right corner of artwork
                val isSubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
                val badgeBg = if (isSubbed) Color.parseColor("#E6152A1D") else Color.parseColor("#CC121418")
                val badgeIcon = if (isSubbed) Icon.CHECK else Icon.ADD
                val badgeTint = if (isSubbed) Color.parseColor("#4ADE80") else Color.WHITE

                val plusBadge = ctx.frame {
                    val blp = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END)
                    blp.setMargins(0, 0, dp(8), dp(8))
                    layoutParams = blp
                    background = ctx.roundedDrawable(badgeBg, radiusDp = 15)

                    val plusIcon = IconView(ctx, badgeIcon, 16, badgeTint)
                    addView(plusIcon, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
                    registerIconForFeed(pod.feedUrl, plusIcon, this)

                    onTapFeedback {
                        toggleSubscription(pod)
                    }
                }
                addView(plusBadge)
            }
            addView(artContainer)

            val titleTv = ctx.labelText(pod.title).apply {
                setTextColor(Color.parseColor("#F1F5F9"))
                textSize = 14f
                maxLines = 1
                val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                tlp.topMargin = dp(8)
                layoutParams = tlp
            }
            addView(titleTv)

            val authorTv = ctx.captionText(pod.author).apply {
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                maxLines = 1
            }
            addView(authorTv)

            onTapFeedback {
                openPodcast(ctx, pod)
            }
        }
        return card
    }

    private fun createCompactRow(ctx: Context, pod: Podcast, showCheck: Boolean = false): View {
        val row = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
            setPadding(0, dp(6), 0, dp(6))
            background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 8)

            val artIv = ctx.artworkView(sizeDp = 54, radiusDp = 10).apply {
                val alp = LinearLayout.LayoutParams(dp(54), dp(54))
                alp.marginEnd = dp(16)
                layoutParams = alp
            }
            addView(artIv)
            imageLoader.load(pod.artworkUrl, artIv)

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
                    alp.topMargin = dp(2)
                    layoutParams = alp
                }
                addView(authorTv)
            }
            addView(textStack)

            val isSubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
            val btnWrap = FrameLayout(ctx).apply {
                val plp = LinearLayout.LayoutParams(dp(40), dp(40))
                layoutParams = plp
                background = ctx.rippleDrawable(Color.TRANSPARENT, radiusDp = 20)
            }
            val iv = IconView(
                ctx,
                if (isSubbed) Icon.CHECK else Icon.ADD,
                18,
                if (isSubbed) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")
            )
            btnWrap.addView(iv, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            registerIconForFeed(pod.feedUrl, iv)

            btnWrap.onTapFeedback {
                toggleSubscription(pod)
            }
            addView(btnWrap)

            onTapFeedback {
                openPodcast(ctx, pod)
            }
        }
        return row
    }

    private fun performSearch(ctx: Context, query: String) {
        dynamicSectionsContainer.removeAllViews()
        dynamicSectionsContainer.addView(createSectionHeader(ctx, "Results for \"$query\"", "") {})
        dynamicSectionsContainer.addView(ctx.skeletonRows(6))

        iTunesSearchService.search(query, 25) { result ->
            dynamicSectionsContainer.removeAllViews()
            when (result) {
                is com.mink.app.core.Result.Ok -> {
                    val podcasts = result.value
                    if (podcasts.isEmpty()) {
                        dynamicSectionsContainer.addView(ctx.emptyState(
                            icon = Icon.SEARCH,
                            title = "No results found",
                            message = "Try different keywords or explore other categories"
                        ))
                    } else {
                        setupHeroCarousel(ctx, podcasts.take(3))

                        dynamicSectionsContainer.addView(createSectionHeader(ctx, "All Results (${podcasts.size})", "") {})
                        val listCol = ctx.vstack {
                            setPadding(dp(20), 0, dp(20), 0)
                            for (pod in podcasts) {
                                addView(createCompactRow(ctx, pod))
                            }
                        }
                        dynamicSectionsContainer.addView(listCol)
                    }
                }
                is com.mink.app.core.Result.Err -> {
                    dynamicSectionsContainer.addView(ctx.errorState(
                        message = result.message,
                        onRetry = { performSearch(ctx, query) }
                    ))
                }
            }
        }
    }

    private fun toggleSubscription(pod: Podcast) {
        val isSubbed = SubscriptionManager.isSubscribed(pod.feedUrl)
        if (isSubbed) {
            val podId = SubscriptionManager.getPodcastId(pod.feedUrl)
            if (podId != null) {
                SubscriptionManager.setSubscribedLocally(pod.feedUrl, false)
                PodcastRepository.unsubscribe(podId)
            } else {
                AppExecutors.executeIo {
                    val dbPod = AppGraph.get().podcastDao.getByFeedUrl(pod.feedUrl)
                    if (dbPod != null) {
                        AppExecutors.main {
                            SubscriptionManager.setSubscribedLocally(pod.feedUrl, false)
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
                }
            }
        }
    }

    private fun rebuildChips(ctx: Context) {
        if (!::chipsRow.isInitialized) return
        chipsRow.removeAllViews()
        for (cat in categories) {
            val isFirst = cat == "All categories"
            val isSelected = cat == activeCategory
            val chip = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                background = ctx.roundedDrawable(
                    if (isSelected) Color.parseColor("#2D3342") else Color.parseColor("#1B1E26"),
                    radiusDp = 20,
                    strokeColor = if (isSelected) Color.parseColor("#4F5B73") else Color.parseColor("#2A2F3D"),
                    strokeWidthDp = 1
                )
                setPadding(dp(16), dp(8), dp(16), dp(8))
                val clp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                clp.marginEnd = dp(10)
                layoutParams = clp

                val label = ctx.labelText(cat).apply {
                    setTextColor(if (isSelected) Color.parseColor("#FFFFFF") else Color.parseColor("#CBD5E1"))
                    textSize = 13f
                }
                addView(label)

                if (isFirst) {
                    val arrow = IconView(ctx, Icon.CHEVRON_DOWN, 14, Color.parseColor("#94A3B8")).apply {
                        val alp = LinearLayout.LayoutParams(dp(14), dp(14))
                        alp.marginStart = dp(6)
                        layoutParams = alp
                    }
                    addView(arrow)
                }

                onTapFeedback {
                    activeCategory = cat
                    searchInput.setText(if (cat == "All categories") "" else cat)
                    rebuildChips(ctx)
                    if (cat == "All categories") {
                        loadFullDiscoverCatalog(ctx)
                    } else {
                        performSearch(ctx, cat)
                    }
                }
            }
            chipsRow.addView(chip)
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
