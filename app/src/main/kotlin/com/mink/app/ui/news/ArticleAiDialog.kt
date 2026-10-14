package com.mink.app.ui.news

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mink.app.ai.AiMessage
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.data.model.Article
import com.mink.app.feed.FeedParser
import com.mink.app.ui.dsl.*
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class ArticleAiDialog(
    private val context: Context,
    private val article: Article,
    private val aiService: AiService,
    private val onSummarySaved: (String) -> Unit
) {

    private lateinit var outputTv: TextView
    private var dialog: AlertDialog? = null
    private var streamJob: Cancelable? = null

    fun show() {
        val plainContent = FeedParser.stripHtml(article.contentHtml.ifEmpty { article.summaryText })
        val truncatedContent = if (plainContent.length > 8000) plainContent.substring(0, 8000) else plainContent

        val dialogView = context.vstack {
            setBackgroundColor(Theme.c.surface)
            setPadding(dp(20), dp(20), dp(20), dp(16))

            // Header title row
            addView(context.hstack {
                gravity = Gravity.CENTER_VERTICAL
                addView(IconView(context, Icon.AI_SPARK, 20, Theme.c.accent).apply {
                    val lp = LinearLayout.LayoutParams(dp(20), dp(20))
                    lp.marginEnd = dp(10)
                    layoutParams = lp
                })
                addView(context.headlineText("Article Intelligence").apply {
                    textSize = 17f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(context.badgeText("BYOK"))
            })

            // Article title preview
            addView(context.captionText(article.title).apply {
                setTextColor(Theme.c.textSecondary)
                maxLines = 2
                val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                tlp.topMargin = dp(6)
                tlp.bottomMargin = dp(14)
                layoutParams = tlp
            })

            // Actions row: Summarize, Key Points, ELI5
            val actionsBar = context.hstack {
                val sumBtn = context.secondaryButton("Summarize") {
                    runAiAction(
                        systemPrompt = "You are an expert news analyst. Produce a clear, concise summary with 3 key takeaways.",
                        userPrompt = "Summarize this article:\n\nTitle: ${article.title}\n\nContent:\n$truncatedContent",
                        saveSummary = true
                    )
                }
                addView(sumBtn)

                val keyBtn = context.secondaryButton("Key Points") {
                    runAiAction(
                        systemPrompt = "Extract the 5 most critical insights or facts from the text as bullet points.",
                        userPrompt = "Extract key points for this article:\n\nTitle: ${article.title}\n\nContent:\n$truncatedContent"
                    )
                }.apply {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = dp(8)
                    layoutParams = lp
                }
                addView(keyBtn)

                val explainBtn = context.secondaryButton("ELI5") {
                    runAiAction(
                        systemPrompt = "Explain the concepts in this article simply, as if to a 5-year old or beginner.",
                        userPrompt = "Explain this simply:\n\nTitle: ${article.title}\n\nContent:\n$truncatedContent"
                    )
                }.apply {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = dp(8)
                    layoutParams = lp
                }
                addView(explainBtn)
            }
            addView(actionsBar)

            // Result container box
            val outputCard = context.card(
                bgColor = Theme.c.surfaceHigh,
                radiusDp = 12,
                strokeColor = Theme.c.outline
            ) {
                val clp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(220))
                clp.topMargin = dp(14)
                clp.bottomMargin = dp(8)
                layoutParams = clp
                setPadding(dp(14), dp(12), dp(14), dp(12))

                val outputScroll = ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                outputTv = context.bodyText("Choose an action above to analyze this article.").apply {
                    setTextColor(Theme.c.textSecondary)
                    textSize = 14f
                }
                outputScroll.addView(outputTv)
                addView(outputScroll)
            }
            addView(outputCard)
        }

        dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun runAiAction(systemPrompt: String, userPrompt: String, saveSummary: Boolean = false) {
        streamJob?.cancel()
        outputTv.text = "Analyzing article...\n\n"
        outputTv.setTextColor(Theme.c.text)

        val accumulated = StringBuilder()
        streamJob = aiService.stream(
            feature = "article_ai",
            messages = listOf(AiMessage("user", userPrompt)),
            systemPrompt = systemPrompt,
            onChunk = { chunk ->
                accumulated.append(chunk)
                outputTv.text = accumulated.toString()
            },
            onComplete = { fullText ->
                outputTv.text = fullText
                if (saveSummary) {
                    AppExecutors.executeIo {
                        AppGraph.get().articleDao.updateAiSummary(article.id, fullText)
                        AppExecutors.main {
                            onSummarySaved(fullText)
                        }
                    }
                }
            },
            onError = { err ->
                outputTv.text = "Error: ${err.message}"
                outputTv.setTextColor(Theme.c.error)
            }
        )
    }
}
