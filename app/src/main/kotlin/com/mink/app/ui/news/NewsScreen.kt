package com.mink.app.ui.news

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.core.EventBus
import com.mink.app.core.TimeFmt
import com.mink.app.data.model.Article
import com.mink.app.feed.FeedRepository
import com.mink.app.feed.FeedsUpdatedEvent
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class NewsScreen(
    private val navigator: ScreenHost,
    private val aiService: AiService
) : Screen {

    private var filterFeedId: Long? = null
    private var activeFilterTab = 0

    private lateinit var contentLayout: LinearLayout
    private lateinit var feedListLayout: LinearLayout
    private lateinit var articleListLayout: LinearLayout
    private lateinit var filterStatusTv: TextView
    private var segmentedCtrl: LinearLayout? = null
    private var eventSubscription: Cancelable? = null

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            // Header
            addView(ctx.screenHeader("News & Feeds") {
                addView(ctx.iconButton(Icon.ADD, sizeDp = 20, tint = Theme.c.text) {
                    showAddFeedDialog(ctx)
                })
                addView(ctx.iconButton(Icon.REFRESH, sizeDp = 20, tint = Theme.c.text) {
                    FeedRepository.refreshAllFeeds(onComplete = { reload(ctx) })
                })
            })

            // Segmented control + mark read
            addView(ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(8))

                segmentedCtrl = ctx.segmentedControl(
                    items = listOf("All", "Unread", "Starred"),
                    selectedIndex = activeFilterTab
                ) { idx ->
                    activeFilterTab = idx
                    reloadArticles(ctx)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(segmentedCtrl!!)

                addView(ctx.textButton("Mark all read", Theme.c.textTertiary) {
                    AppExecutors.executeIo {
                        AppGraph.get().articleDao.setAllRead(filterFeedId)
                        AppExecutors.main { reload(ctx) }
                    }
                }.apply {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = dp(8); layoutParams = lp
                })
            })

            // Feed chips carousel
            val feedScroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                val fslp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = fslp
            }
            feedListLayout = ctx.hstack {
                setPadding(dp(20), dp(4), dp(20), dp(8))
            }
            feedScroll.addView(feedListLayout)
            addView(feedScroll)

            // Filter status
            filterStatusTv = ctx.captionText("All feeds").apply {
                setTextColor(Theme.c.accent)
                setPadding(dp(20), 0, dp(20), dp(8))
            }
            addView(filterStatusTv)

            // Article list in scroll
            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            articleListLayout = ctx.vstack {
                setPadding(dp(20), 0, dp(20), dp(20))
            }
            scrollView.addView(articleListLayout)
            addView(scrollView)
        }

        // Show skeletons then load
        articleListLayout.addView(ctx.skeletonRows(4))
        reload(ctx)
        return root
    }

    override fun onShow() {
        eventSubscription = EventBus.subscribe { event ->
            if (event is FeedsUpdatedEvent && ::contentLayout.isInitialized) {
                reload(articleListLayout.context)
            }
        }
    }

    override fun onHide() {
        eventSubscription?.cancel(); eventSubscription = null
    }

    private fun reload(ctx: Context) {
        reloadFeeds(ctx)
        reloadArticles(ctx)
    }

    private fun reloadFeeds(ctx: Context) {
        AppExecutors.executeIo {
            val feeds = AppGraph.get().feedDao.getAll()
            AppExecutors.main {
                feedListLayout.removeAllViews()
                feedListLayout.addView(ctx.chipButton("All Feeds", filterFeedId == null) {
                    filterFeedId = null; filterStatusTv.text = "All feeds"; reload(ctx)
                }.apply {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginEnd = dp(8); layoutParams = lp
                })
                for (feed in feeds) {
                    val countStr = if (feed.unreadCount > 0) " (${feed.unreadCount})" else ""
                    feedListLayout.addView(ctx.chipButton(feed.title + countStr, filterFeedId == feed.id) {
                        filterFeedId = feed.id; filterStatusTv.text = feed.title; reload(ctx)
                    }.apply {
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.marginEnd = dp(8); layoutParams = lp
                    })
                }
            }
        }
    }

    private fun reloadArticles(ctx: Context) {
        AppExecutors.executeIo {
            val articles = AppGraph.get().articleDao.getArticles(
                feedId = filterFeedId,
                filterUnreadOnly = activeFilterTab == 1,
                filterStarredOnly = activeFilterTab == 2,
                limit = 100
            )
            AppExecutors.main {
                articleListLayout.removeAllViews()
                if (articles.isEmpty()) {
                    articleListLayout.addView(ctx.emptyState(
                        icon = Icon.NEWS,
                        title = "No articles",
                        message = if (activeFilterTab == 0) "Add an RSS feed to start reading"
                                  else "No ${if (activeFilterTab == 1) "unread" else "starred"} articles",
                        actionLabel = if (activeFilterTab == 0) "Add Feed" else null,
                        onAction = if (activeFilterTab == 0) { { showAddFeedDialog(ctx) } } else null
                    ))
                } else {
                    for (article in articles) {
                        articleListLayout.addView(createArticleRow(ctx, article))
                    }
                }
            }
        }
    }

    private fun createArticleRow(ctx: Context, article: Article): View {
        return ctx.card(bgColor = Theme.c.surface, radiusDp = 14, strokeColor = Theme.c.outline) {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = ctx.dp(10)
            layoutParams = lp

            val row = ctx.vstack {
                setPadding(dp(16), dp(14), dp(16), dp(14))

                // Top: feed badge + time + unread dot
                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ctx.badgeText(article.feedTitle).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(ctx.captionText(TimeFmt.formatRelativeTime(article.publishedAt)))
                    if (!article.read) {
                        val dot = View(ctx).apply {
                            val dlp = LinearLayout.LayoutParams(dp(7), dp(7))
                            dlp.marginStart = dp(8); layoutParams = dlp
                            background = ctx.roundedDrawable(Theme.c.accent, radiusDp = 4)
                        }
                        addView(dot)
                    }
                })

                // Title
                addView(ctx.headlineText(article.title).apply {
                    textSize = 15f
                    setTextColor(if (article.read) Theme.c.textSecondary else Theme.c.text)
                    maxLines = 2
                    val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    tlp.topMargin = dp(8); layoutParams = tlp
                })

                // Summary
                if (article.summaryText.isNotBlank()) {
                    addView(ctx.bodyText(article.summaryText).apply {
                        maxLines = 2; textSize = 13f; setTextColor(Theme.c.textTertiary)
                        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        slp.topMargin = dp(4); layoutParams = slp
                    })
                }

                // AI badge
                if (article.aiSummary.isNotBlank()) {
                    addView(ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        val alp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        alp.topMargin = dp(8); layoutParams = alp
                        addView(IconView(ctx, Icon.AI_SPARK, 14, Theme.c.accent).apply {
                            val ilp = LinearLayout.LayoutParams(dp(14), dp(14))
                            ilp.marginEnd = dp(4); layoutParams = ilp
                        })
                        addView(ctx.captionText("AI Summary").apply { setTextColor(Theme.c.accent) })
                    })
                }
            }
            addView(row)

            background = ctx.rippleDrawable(Theme.c.surface, radiusDp = 14, strokeColor = Theme.c.outline, strokeWidthDp = 1)
            onTapFeedback {
                AppExecutors.executeIo { AppGraph.get().articleDao.setRead(article.id, true) }
                navigator.pushScreen(ArticleReaderScreen(article.id, navigator, aiService))
            }
        }
    }

    private fun showAddFeedDialog(ctx: Context) {
        val input = EditText(ctx).apply {
            hint = "https://example.com/feed.xml"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Theme.c.text); setHintTextColor(Theme.c.textTertiary)
            background = ctx.roundedDrawable(Theme.c.surfaceHigh, radiusDp = 10, strokeColor = Theme.c.outline, strokeWidthDp = 1)
            padding(12)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Add RSS Feed")
            .setView(input)
            .setPositiveButton("Subscribe") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    FeedRepository.addFeedFromUrl(url) { result ->
                        when (result) {
                            is com.mink.app.core.Result.Ok -> reload(ctx)
                            is com.mink.app.core.Result.Err -> {}
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
