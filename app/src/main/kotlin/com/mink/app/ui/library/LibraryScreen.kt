package com.mink.app.ui.library

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
import com.mink.app.podcast.PodcastsUpdatedEvent
import com.mink.app.ui.discover.SeeAllListScreen
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.snips.SnipsLibraryScreen
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.Theme

data class PodcastCategoryDef(
    val name: String,
    val bgColor: Int,
    val keywords: List<String>
)

class LibraryScreen(
    private val navigator: ScreenHost,
    private val imageLoader: ImageLoader,
    private val aiService: AiService
) : Screen {

    private lateinit var contentLayout: LinearLayout
    private var eventSub: Cancelable? = null

    private val categoryDefs = listOf(
        PodcastCategoryDef("Daily News", Color.parseColor("#C53030"), listOf("news", "daily", "guardian", "bbc", "today", "global")),
        PodcastCategoryDef("Documentary", Color.parseColor("#D97706"), listOf("documentary", "casefile", "radiolab", "investigation", "secret")),
        PodcastCategoryDef("Education", Color.parseColor("#B45309"), listOf("education", "learning", "english", "kit", "privacy", "minute")),
        PodcastCategoryDef("Fiction", Color.parseColor("#059669"), listOf("fiction", "sherlock", "sleepy", "truth", "bookshelf", "story")),
        PodcastCategoryDef("History", Color.parseColor("#0284C7"), listOf("history", "ancients", "medieval", "battle", "snow")),
        PodcastCategoryDef("Science", Color.parseColor("#7C3AED"), listOf("science", "planet", "universe", "change", "living")),
        PodcastCategoryDef("Society & Culture", Color.parseColor("#BE185D"), listOf("society", "culture", "atlas", "ologies", "dead", "alive")),
        PodcastCategoryDef("True Crime", Color.parseColor("#9F1239"), listOf("crime", "conspiracy", "culpable", "deck", "scene")),
        PodcastCategoryDef("TV & Film", Color.parseColor("#475569"), listOf("film", "tv", "a24", "empire", "halloween", "movie"))
    )

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            val header = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Theme.c.surface)
                setPadding(ctx.dp(24), ctx.dp(18), ctx.dp(24), ctx.dp(18))

                addView(ctx.displayText("Podcasts").apply {
                    setTextColor(Color.WHITE)
                    textSize = 28f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val actionsLayout = ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ctx.iconButton(Icon.PLAYLIST, sizeDp = 22, tint = Color.parseColor("#CBD5E1")) {
                    })
                    addView(ctx.iconButton(Icon.RSS, sizeDp = 22, tint = Color.parseColor("#CBD5E1")) {
                    })
                    addView(ctx.iconButton(Icon.SEARCH, sizeDp = 22, tint = Color.parseColor("#CBD5E1")) {
                    })
                    addView(ctx.iconButton(Icon.SNIP, sizeDp = 22, tint = Color.parseColor("#CBD5E1")) {
                        navigator.pushScreen(SnipsLibraryScreen(navigator))
                    })
                }
                addView(actionsLayout)
            }
            addView(header)

            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            }
            contentLayout = ctx.vstack {
                setPadding(ctx.dp(24), ctx.dp(20), ctx.dp(24), ctx.dp(32))
                addView(ctx.skeletonRows(4))
            }
            scrollView.addView(contentLayout)
            addView(scrollView)
        }

        loadData(root.context)
        return root
    }

    override fun onShow() {
        eventSub = EventBus.subscribe { event ->
            if (event is PodcastsUpdatedEvent && ::contentLayout.isInitialized) {
                loadData(contentLayout.context)
            }
        }
    }

    override fun onHide() {
        eventSub?.cancel()
        eventSub = null
    }

    private fun loadData(ctxParam: Context) {
        AppExecutors.executeIo {
            val subscriptions = AppGraph.get().podcastDao.getAllSubscribed()
            val cachedPods = DiscoverCache.heroPodcasts.orEmpty() + DiscoverCache.trendingPodcasts.orEmpty() + DiscoverCache.shelves.values.flatten()

            AppExecutors.main {
                val ctxVal: Context = ctxParam
                contentLayout.removeAllViews()
                if (subscriptions.isEmpty() && cachedPods.isEmpty()) {
                    contentLayout.addView(ctxVal.emptyState(
                        icon = Icon.LIBRARY,
                        title = "Your library is empty",
                        message = "Subscribe to podcasts from Discover to build your library",
                        actionLabel = "Go to Discover"
                    ))
                } else {
                    val allAvailable = if (subscriptions.isNotEmpty()) subscriptions else cachedPods

                    val screenWidth = ctxVal.resources.displayMetrics.widthPixels
                    val columns = if (screenWidth >= ctxVal.dp(1000)) 4 else if (screenWidth >= ctxVal.dp(700)) 3 else 2

                    var currentRow: LinearLayout? = null
                    var cardCountInRow = 0

                    for (catDef in categoryDefs) {
                        val matching = allAvailable.filter { pod ->
                            val text = (pod.title + " " + pod.author + " " + pod.description).lowercase()
                            catDef.keywords.any { text.contains(it) }
                        }

                        val categoryPods = mutableListOf<Podcast>()
                        categoryPods.addAll(matching)
                        for (p in allAvailable) {
                            if (categoryPods.size >= 4) break
                            if (!categoryPods.contains(p)) categoryPods.add(p)
                        }

                        val categoryCard = createCategoryCard(ctxVal, catDef.name, catDef.bgColor, categoryPods) {
                            showCategoryDetail(catDef.name)
                        }

                        if (currentRow == null || cardCountInRow >= columns) {
                            currentRow = LinearLayout(ctxVal).apply {
                                orientation = LinearLayout.HORIZONTAL
                                val hlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                hlp.bottomMargin = ctxParam.dp(18)
                                layoutParams = hlp
                            }
                            contentLayout.addView(currentRow)
                            cardCountInRow = 0
                        }

                        val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        if (cardCountInRow > 0) clp.marginStart = ctxParam.dp(16)
                        categoryCard.layoutParams = clp
                        currentRow.addView(categoryCard)
                        cardCountInRow++
                    }
                }
            }
        }
    }

    private fun createCategoryCard(
        ctxVal: Context,
        categoryTitle: String,
        bgColor: Int,
        podcasts: List<Podcast>,
        onClick: () -> Unit
    ): View {
        return ctxVal.card(bgColor = bgColor, radiusDp = 22, strokeColor = Color.parseColor("#33FFFFFF"), strokeWidthDp = 1) {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            background = ctxVal.rippleDrawable(bgColor, radiusDp = 22, strokeColor = Color.parseColor("#33FFFFFF"), strokeWidthDp = 1)

            val inner = ctxVal.vstack {
                setPadding(ctxVal.dp(22), ctxVal.dp(22), ctxVal.dp(22), ctxVal.dp(22))

                val artworkGrid = ctxVal.vstack {
                    val row1 = ctxVal.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(createThumbnailView(ctxVal, podcasts.getOrNull(0)?.artworkUrl))
                        val spacer = View(ctxVal).apply { layoutParams = LinearLayout.LayoutParams(ctxVal.dp(12), ctxVal.dp(12)) }
                        addView(spacer)
                        addView(createThumbnailView(ctxVal, podcasts.getOrNull(1)?.artworkUrl))
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    addView(row1)

                    val row2 = ctxVal.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        rlp.topMargin = ctxVal.dp(12)
                        layoutParams = rlp

                        addView(createThumbnailView(ctxVal, podcasts.getOrNull(2)?.artworkUrl))
                        val spacer = View(ctxVal).apply { layoutParams = LinearLayout.LayoutParams(ctxVal.dp(12), ctxVal.dp(12)) }
                        addView(spacer)
                        addView(createThumbnailView(ctxVal, podcasts.getOrNull(3)?.artworkUrl))
                    }
                    addView(row2)
                }
                addView(artworkGrid)

                val titleTv = ctxVal.headlineText(categoryTitle).apply {
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    tlp.topMargin = ctxVal.dp(20)
                    layoutParams = tlp
                }
                addView(titleTv)
            }
            addView(inner)

            onTapFeedback {
                onClick()
            }
        }
    }

    private fun createThumbnailView(ctxVal: Context, artworkUrl: String?): ImageView {
        val size = ctxVal.dp(84)
        val iv = ctxVal.artworkView(sizeDp = 84, radiusDp = 14).apply {
            val lp = LinearLayout.LayoutParams(0, size, 1f)
            layoutParams = lp
            setBackgroundColor(Color.parseColor("#22000000"))
        }
        if (!artworkUrl.isNullOrEmpty()) {
            imageLoader.load(artworkUrl, iv)
        }
        return iv
    }

    private fun showCategoryDetail(categoryName: String) {
        navigator.pushScreen(SeeAllListScreen(categoryName, categoryName, navigator, imageLoader, aiService))
    }
}
