package com.mink.app.ui.news

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.*
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.TimeFmt
import com.mink.app.data.model.Article
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class ArticleReaderScreen(
    private val articleId: Long,
    private val navigator: ScreenHost,
    private val aiService: AiService
) : Screen {

    private var article: Article? = null
    private lateinit var contentContainer: LinearLayout
    private lateinit var aiBoxContainer: LinearLayout

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            // Header
            addView(ctx.screenHeader(
                title = "Article",
                leadingIcon = Icon.CHEVRON_LEFT,
                onLeading = { navigator.popScreen() }
            ) {
                addView(ctx.iconButton(Icon.AI_SPARK, sizeDp = 20, tint = Theme.c.accent) {
                    article?.let { art ->
                        ArticleAiDialog(ctx, art, aiService) { newSummary ->
                            article = art.copy(aiSummary = newSummary)
                            renderAiBox(ctx, article!!)
                        }.show()
                    }
                })
                addView(ctx.iconButton(
                    icon = Icon.STAR,
                    sizeDp = 20,
                    tint = if (article?.starred == true) Theme.c.accent else Theme.c.textTertiary
                ) {
                    article?.let { art ->
                        val newStarred = !art.starred
                        article = art.copy(starred = newStarred)
                        AppExecutors.executeIo { AppGraph.get().articleDao.setStarred(art.id, newStarred) }
                    }
                })
            })

            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            }
            contentContainer = ctx.vstack {
                setPadding(dp(20), dp(16), dp(20), dp(20))
                addView(ctx.skeletonRows(3))
            }
            scrollView.addView(contentContainer)
            addView(scrollView)
        }

        loadArticle(ctx)
        return root
    }

    private fun loadArticle(ctx: Context) {
        AppExecutors.executeIo {
            val art = AppGraph.get().articleDao.getById(articleId)
            AppExecutors.main {
                article = art
                if (art != null) renderArticle(ctx, art)
                else {
                    contentContainer.removeAllViews()
                    contentContainer.addView(ctx.errorState("Article not found"))
                }
            }
        }
    }

    private fun renderArticle(ctx: Context, art: Article) {
        contentContainer.removeAllViews()

        // Meta: feed + date
        contentContainer.addView(ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            addView(ctx.badgeText(art.feedTitle).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(ctx.captionText(TimeFmt.formatRelativeTime(art.publishedAt)))
        })

        // Title
        contentContainer.addView(ctx.displayText(art.title).apply {
            textSize = 22f
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tlp.topMargin = dp(12); tlp.bottomMargin = dp(8); layoutParams = tlp
        })

        // Author
        if (art.author.isNotBlank()) {
            contentContainer.addView(ctx.captionText("By ${art.author}").apply {
                val alp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                alp.bottomMargin = dp(16); layoutParams = alp
            })
        }

        // AI summary box
        aiBoxContainer = ctx.vstack {
            val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            blp.bottomMargin = dp(16); layoutParams = blp
        }
        contentContainer.addView(aiBoxContainer)
        renderAiBox(ctx, art)

        // Article content webview
        val rawHtml = art.contentHtml.ifEmpty { art.summaryText }
        val textColor = if (Theme.isDark) "#F2F3F7" else "#12141A"
        val linkColor = if (Theme.isDark) "#7C8CFF" else "#4F5FE0"
        val styledHtml = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { color: $textColor; font-family: sans-serif; line-height: 1.7; padding: 0; margin: 0; }
                img { max-width: 100%; height: auto; border-radius: 12px; margin: 16px 0; }
                a { color: $linkColor; text-decoration: none; }
                pre, code { background: rgba(128,128,128,0.15); border-radius: 6px; padding: 2px 6px; font-size: 14px; }
                blockquote { border-left: 3px solid $linkColor; margin-left: 0; padding-left: 16px; color: #888; }
                h1,h2,h3 { color: $textColor; }
            </style></head><body>$rawHtml</body></html>
        """.trimIndent()

        val webView = WebView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = false; settings.defaultFontSize = 16
            loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
        }
        contentContainer.addView(webView)
    }

    private fun renderAiBox(ctx: Context, art: Article) {
        aiBoxContainer.removeAllViews()
        if (art.aiSummary.isNotBlank()) {
            val card = ctx.card(bgColor = Theme.c.surfaceHigh, radiusDp = 14, strokeColor = Theme.c.outline) {
                setPadding(dp(14), dp(14), dp(14), dp(14))
                val box = ctx.vstack {
                    addView(ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(IconView(ctx, Icon.AI_SPARK, 16, Theme.c.accent).apply {
                            val ilp = LinearLayout.LayoutParams(dp(16), dp(16))
                            ilp.marginEnd = dp(6); layoutParams = ilp
                        })
                        addView(ctx.overlineText("AI Summary").apply { setTextColor(Theme.c.accent) })
                    })
                    addView(ctx.bodyText(art.aiSummary).apply {
                        setTextColor(Theme.c.text)
                        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        blp.topMargin = dp(8); layoutParams = blp
                    })
                }
                addView(box)
            }
            aiBoxContainer.addView(card)
        }
    }
}
