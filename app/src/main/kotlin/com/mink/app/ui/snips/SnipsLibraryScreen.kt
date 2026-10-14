package com.mink.app.ui.snips

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.TimeFmt
import com.mink.app.data.model.Snip
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class SnipsLibraryScreen(private val navigator: ScreenHost) : Screen {

    private lateinit var contentLayout: LinearLayout

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            // Header
            addView(ctx.screenHeader(
                title = "Saved Snips",
                leadingIcon = Icon.CHEVRON_LEFT,
                onLeading = { navigator.popScreen() }
            ) {
                addView(ctx.iconButton(Icon.SHARE, sizeDp = 20, tint = Theme.c.text) {
                    exportAllSnips(ctx)
                })
            })

            val scrollView = ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            }
            contentLayout = ctx.vstack {
                setPadding(dp(20), dp(16), dp(20), dp(20))
                addView(ctx.skeletonRows(4))
            }
            scrollView.addView(contentLayout)
            addView(scrollView)
        }

        loadSnips(ctx)
        return root
    }

    private fun loadSnips(ctx: Context) {
        AppExecutors.executeIo {
            val snips = AppGraph.get().miscDao.getAllSnips()
            AppExecutors.main {
                contentLayout.removeAllViews()
                if (snips.isEmpty()) {
                    contentLayout.addView(ctx.emptyState(
                        icon = Icon.SNIP,
                        title = "No snips yet",
                        message = "Listen to an episode and tap 'Capture Snip' to save key moments"
                    ))
                } else {
                    contentLayout.addView(ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        val hlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        hlp.bottomMargin = dp(12); layoutParams = hlp
                        addView(ctx.headlineText("${snips.size} Snips").apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                    })

                    for (snip in snips) {
                        contentLayout.addView(createSnipCard(ctx, snip))
                    }
                }
            }
        }
    }

    private fun createSnipCard(ctx: Context, snip: Snip): View {
        return ctx.card(bgColor = Theme.c.surface, radiusDp = 14, strokeColor = Theme.c.outline) {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = ctx.dp(10); layoutParams = lp

            val layout = ctx.vstack {
                setPadding(dp(14), dp(14), dp(14), dp(14))

                // Header: podcast/time + date
                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        addView(IconView(ctx, Icon.SNIP, 14, Theme.c.accent).apply {
                            val ilp = LinearLayout.LayoutParams(dp(14), dp(14))
                            ilp.marginEnd = dp(6); layoutParams = ilp
                        })
                        addView(ctx.captionText("${snip.podcastTitle} • ${TimeFmt.formatDurationMs(snip.startMs)} – ${TimeFmt.formatDurationMs(snip.endMs)}").apply {
                            setTextColor(Theme.c.accent)
                        })
                    })
                    addView(ctx.captionText(TimeFmt.formatRelativeTime(snip.createdAt)))
                })

                // Title
                if (snip.title.isNotBlank()) {
                    addView(ctx.labelText(snip.title).apply {
                        setTextColor(Theme.c.text); maxLines = 2
                        val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        tlp.topMargin = dp(8); layoutParams = tlp
                    })
                }

                // User note
                if (snip.userNote.isNotBlank()) {
                    addView(ctx.bodyText(snip.userNote).apply {
                        maxLines = 3; textSize = 13f; setTextColor(Theme.c.textSecondary)
                        val nlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        nlp.topMargin = dp(4); layoutParams = nlp
                    })
                }

                // Summary text
                if (snip.summaryText.isNotBlank()) {
                    addView(ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        val ailp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        ailp.topMargin = dp(6); layoutParams = ailp
                        addView(IconView(ctx, Icon.AI_SPARK, 12, Theme.c.accent).apply {
                            val ilp = LinearLayout.LayoutParams(dp(12), dp(12))
                            ilp.marginEnd = dp(4); layoutParams = ilp
                        })
                        addView(ctx.captionText(snip.summaryText).apply {
                            setTextColor(Theme.c.textSecondary); maxLines = 2
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                    })
                }

                // Actions
                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    val alp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    alp.topMargin = dp(10); layoutParams = alp

                    addView(ctx.textButton("Share") {
                        val text = "${snip.title}\n${snip.podcastTitle}\n${TimeFmt.formatDurationMs(snip.startMs)} – ${TimeFmt.formatDurationMs(snip.endMs)}\n${snip.userNote}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "Share Snip"))
                    })

                    addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })

                    addView(ctx.iconButton(Icon.DELETE, sizeDp = 18, tint = Theme.c.error) {
                        AlertDialog.Builder(ctx)
                            .setTitle("Delete snip?")
                            .setMessage("This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                AppExecutors.executeIo {
                                    AppGraph.get().miscDao.deleteSnip(snip.id)
                                    AppExecutors.main { loadSnips(ctx) }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    })
                })
            }
            addView(layout)
        }
    }

    private fun exportAllSnips(ctx: Context) {
        AppExecutors.executeIo {
            val snips = AppGraph.get().miscDao.getAllSnips()
            val sb = StringBuilder("# Snips Library\n\n")
            for (snip in snips) {
                sb.append("## ${snip.title}\n")
                sb.append("Podcast: ${snip.podcastTitle}\n")
                sb.append("Time: ${TimeFmt.formatDurationMs(snip.startMs)} – ${TimeFmt.formatDurationMs(snip.endMs)}\n")
                if (snip.userNote.isNotBlank()) sb.append("Note: ${snip.userNote}\n")
                sb.append("\n")
            }
            AppExecutors.main {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                }
                ctx.startActivity(Intent.createChooser(intent, "Export Snips"))
            }
        }
    }
}
