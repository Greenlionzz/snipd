package com.mink.app.ui.podcast

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.ai.AiMessage
import com.mink.app.ai.AiService
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.data.model.Episode
import com.mink.app.data.model.EpisodeAi
import com.mink.app.feed.FeedParser
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class EpisodeAiScreen(
    private val episodeId: Long,
    private val navigator: ScreenHost,
    private val aiService: AiService
) : Screen {

    private var episode: Episode? = null
    private var episodeAi: EpisodeAi? = null

    private lateinit var contentLayout: LinearLayout
    private lateinit var outputTv: TextView
    private var streamJob: Cancelable? = null

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            // Header
            addView(ctx.screenHeader(
                title = "AI Intelligence",
                leadingIcon = Icon.CHEVRON_LEFT,
                onLeading = { navigator.popScreen() }
            ))

            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            }
            contentLayout = ctx.vstack {
                setPadding(dp(20), dp(16), dp(20), dp(20))
                addView(ctx.skeletonRows(3))
            }
            scrollView.addView(contentLayout)
            addView(scrollView)
        }

        loadData(ctx)
        return root
    }

    override fun onHide() {
        streamJob?.cancel(); streamJob = null
    }

    private fun loadData(ctx: Context) {
        AppExecutors.executeIo {
            val ep = AppGraph.get().episodeDao.getById(episodeId)
            val ai = AppGraph.get().miscDao.getEpisodeAi(episodeId)
            AppExecutors.main {
                episode = ep; episodeAi = ai
                if (ep != null) renderContent(ctx, ep, ai)
                else {
                    contentLayout.removeAllViews()
                    contentLayout.addView(ctx.errorState("Episode not found"))
                }
            }
        }
    }

    private fun renderContent(ctx: Context, ep: Episode, existingAi: EpisodeAi?) {
        contentLayout.removeAllViews()

        // Episode title card
        contentLayout.addView(ctx.card(bgColor = Theme.c.surface, radiusDp = 14, strokeColor = Theme.c.outline) {
            val clp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            clp.bottomMargin = ctx.dp(16); layoutParams = clp
            val inner = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(IconView(ctx, Icon.EPISODE, 22, Theme.c.accent).apply {
                    val ilp = LinearLayout.LayoutParams(dp(22), dp(22))
                    ilp.marginEnd = dp(12); layoutParams = ilp
                })
                addView(ctx.headlineText(ep.title).apply {
                    maxLines = 2; textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            addView(inner)
        })

        // Action buttons grid
        val actionsCard = ctx.card(bgColor = Theme.c.surface, radiusDp = 14, strokeColor = Theme.c.outline) {
            val alp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            alp.bottomMargin = ctx.dp(16); layoutParams = alp

            val grid = ctx.vstack {
                setPadding(dp(14), dp(14), dp(14), dp(14))

                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(IconView(ctx, Icon.AI_SPARK, 20, Theme.c.accent).apply {
                        val ilp = LinearLayout.LayoutParams(dp(20), dp(20))
                        ilp.marginEnd = dp(10); layoutParams = ilp
                    })
                    addView(ctx.headlineText("AI Analysis").apply {
                        textSize = 15f
                    })
                })

                addView(ctx.bodyText("Generate AI-powered insights from this episode's content").apply {
                    setTextColor(Theme.c.textTertiary); textSize = 12f
                    val dlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    dlp.topMargin = dp(6); dlp.bottomMargin = dp(14); layoutParams = dlp
                })

                // Row 1: Summary, Deep Dive
                addView(ctx.hstack {
                    addView(createActionButton(ctx, ep, "Summary", Icon.WAVEFORM,
                        "You are a world-class podcast analyst. Produce an executive summary followed by key takeaways and actionable highlights.",
                        "Analyze this podcast episode based on its title and show notes:\nTitle: ${ep.title}\nShow notes:\n${FeedParser.stripHtml(ep.descriptionHtml)}"
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                    })
                    addView(createActionButton(ctx, ep, "Deep Dive", Icon.PODCAST,
                        "You are a subject-matter expert producing a thorough analysis with nuanced perspectives.",
                        "Provide a deep dive analysis of:\nTitle: ${ep.title}\nNotes:\n${FeedParser.stripHtml(ep.descriptionHtml)}"
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                })

                // Row 2: Discussion, Key Quotes
                addView(ctx.hstack {
                    val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    rlp.topMargin = dp(8); layoutParams = rlp

                    addView(createActionButton(ctx, ep, "Discussion", Icon.HEADPHONE,
                        "Generate thought-provoking discussion questions from this episode.",
                        "Create 5-7 discussion questions for:\nTitle: ${ep.title}\nNotes:\n${FeedParser.stripHtml(ep.descriptionHtml)}"
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                    })
                    addView(createActionButton(ctx, ep, "Key Quotes", Icon.SNIP,
                        "Extract the most impactful quotes and sound bites from this episode.",
                        "Extract key quotes and sound bites from:\nTitle: ${ep.title}\nNotes:\n${FeedParser.stripHtml(ep.descriptionHtml)}"
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                })
            }
            addView(grid)
        }
        contentLayout.addView(actionsCard)

        // Existing AI result
        val existingSummary = existingAi?.summaryJson
        if (!existingSummary.isNullOrBlank()) {
            contentLayout.addView(ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                val hlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                hlp.bottomMargin = dp(10); layoutParams = hlp
                addView(ctx.headlineText("Previous Result").apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(ctx.textButton("Clear") {
                    AppExecutors.executeIo {
                        AppGraph.get().miscDao.saveEpisodeAi(
                            EpisodeAi(episodeId = episodeId, summaryJson = "")
                        )
                        AppExecutors.main { loadData(ctx) }
                    }
                })
            })

            contentLayout.addView(ctx.card(bgColor = Theme.c.surface, radiusDp = 14, strokeColor = Theme.c.outline) {
                val rlp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                rlp.bottomMargin = ctx.dp(16); layoutParams = rlp
                addView(ctx.bodyText(existingSummary).apply {
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    setTextColor(Theme.c.text)
                })
            })
        }

        // Output area
        outputTv = ctx.bodyText("").apply {
            setTextColor(Theme.c.text)
            val olp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            olp.topMargin = dp(8); layoutParams = olp
        }
        contentLayout.addView(outputTv)
    }

    private fun createActionButton(ctx: Context, ep: Episode, label: String, icon: Icon, sysPrompt: String, userPrompt: String): View {
        return ctx.card(bgColor = Theme.c.surfaceHigh, radiusDp = 12, strokeColor = Theme.c.outline) {
            background = ctx.rippleDrawable(Theme.c.surfaceHigh, radiusDp = 12, strokeColor = Theme.c.outline, strokeWidthDp = 1)

            val inner = ctx.vstack {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(14), dp(12), dp(14))

                addView(IconView(ctx, icon, 22, Theme.c.accent).apply {
                    val ilp = LinearLayout.LayoutParams(dp(22), dp(22))
                    ilp.bottomMargin = dp(6); layoutParams = ilp
                })
                addView(ctx.labelText(label).apply {
                    gravity = Gravity.CENTER; textSize = 12f
                })
            }
            addView(inner)

            onTapFeedback {
                generateAction(sysPrompt, userPrompt, label, ctx)
            }
        }
    }

    private fun generateAction(sys: String, user: String, action: String, ctx: Context) {
        streamJob?.cancel()
        outputTv.text = "Generating $action...\n\n"
        outputTv.setTextColor(Theme.c.text)

        val accumulated = StringBuilder()
        streamJob = aiService.stream(
            feature = "episode_ai",
            messages = listOf(AiMessage("user", user)),
            systemPrompt = sys,
            onChunk = { token ->
                accumulated.append(token)
                outputTv.text = accumulated.toString()
            },
            onComplete = { fullText ->
                outputTv.text = fullText
                AppExecutors.executeIo {
                    AppGraph.get().miscDao.saveEpisodeAi(
                        EpisodeAi(episodeId = episodeId, summaryJson = fullText)
                    )
                }
            },
            onError = { err ->
                outputTv.text = "Error: ${err.message}"
                outputTv.setTextColor(Theme.c.error)
            }
        )
    }
}
