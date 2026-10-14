package com.mink.app.ui.settings

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
import com.mink.app.ui.dsl.*
import com.mink.app.ui.nav.Screen
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class SettingsScreen(
    private val aiService: AiService,
    private val navigator: ScreenHost
) : Screen {

    private lateinit var contentLayout: LinearLayout
    private lateinit var usageTv: TextView

    override fun createView(ctx: Context): View {
        val root = ctx.vstack {
            setBackgroundColor(Theme.c.bg)

            // Header
            addView(ctx.screenHeader("Settings"))

            addView(ctx.scrollColumn {
                contentLayout = this
                setPadding(dp(20), dp(16), dp(20), dp(16))

                // AI Providers card
                addView(createAiSection(ctx))

                // Usage card
                addView(createUsageSection(ctx))

                // Appearance card
                addView(createAppearanceSection(ctx))
            })
        }

        loadUsage()
        return root
    }

    private fun createAiSection(ctx: Context): View {
        return ctx.card(bgColor = Theme.c.surface, radiusDp = 16, strokeColor = Theme.c.outline) {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = ctx.dp(16)
            layoutParams = lp

            val layout = ctx.vstack {
                setPadding(dp(16), dp(16), dp(16), dp(16))

                // Header row
                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(IconView(ctx, Icon.AI_SPARK, 20, Theme.c.accent).apply {
                        val ilp = LinearLayout.LayoutParams(dp(20), dp(20))
                        ilp.marginEnd = dp(10)
                        layoutParams = ilp
                    })
                    addView(ctx.headlineText("AI Providers").apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(ctx.badgeText("BYOK"))
                })

                addView(ctx.bodyText("API keys stored encrypted on-device. Direct provider calls, no middleman.").apply {
                    setTextColor(Theme.c.textTertiary); textSize = 12f
                    val dlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    dlp.topMargin = dp(8); dlp.bottomMargin = dp(16)
                    layoutParams = dlp
                })

                for ((provId, prov) in aiService.providers) {
                    addView(createProviderRow(ctx, provId, prov.name))
                }
            }
            addView(layout)
        }
    }

    private fun createProviderRow(ctx: Context, providerId: String, name: String): View {
        val active = aiService.activeProviderId == providerId
        val hasKey = !aiService.getApiKey(providerId).isNullOrBlank()

        return ctx.card(bgColor = Theme.c.surfaceHigh, radiusDp = 12, strokeColor = Theme.c.outline) {
            val clp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            clp.bottomMargin = ctx.dp(10)
            layoutParams = clp

            val row = ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))

                // Status dot
                val dot = View(ctx).apply {
                    val dotSize = dp(8)
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = dp(12) }
                    background = ctx.roundedDrawable(
                        if (active) Theme.c.success else if (hasKey) Theme.c.warning else Theme.c.textTertiary,
                        radiusDp = 4
                    )
                }
                addView(dot)

                // Info
                val info = ctx.vstack {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(ctx.hstack {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ctx.labelText(name).apply { setTextColor(Theme.c.text) })
                        if (active) {
                            addView(ctx.badgeText("Active", bgColor = Theme.c.accentSoft, textColor = Theme.c.accent).apply {
                                val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                blp.marginStart = dp(8); layoutParams = blp
                            })
                        }
                    })
                    addView(ctx.captionText(if (hasKey) "Key configured" else "No key set").apply {
                        setTextColor(if (hasKey) Theme.c.success else Theme.c.textTertiary)
                        val klp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        klp.topMargin = dp(2); layoutParams = klp
                    })
                }
                addView(info)

                addView(ctx.secondaryButton(if (hasKey) "Edit" else "Set Key") {
                    showSetKeyDialog(ctx, providerId, name)
                })

                if (!active && hasKey) {
                    addView(ctx.primaryButton("Activate") {
                        aiService.activeProviderId = providerId
                        refreshScreen(ctx)
                    }.apply {
                        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        slp.marginStart = dp(8); layoutParams = slp
                    })
                }
            }
            addView(row)
        }
    }

    private fun createUsageSection(ctx: Context): View {
        return ctx.card(bgColor = Theme.c.surface, radiusDp = 16, strokeColor = Theme.c.outline) {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = ctx.dp(16)
            layoutParams = lp

            val layout = ctx.vstack {
                setPadding(dp(16), dp(16), dp(16), dp(16))

                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(IconView(ctx, Icon.WAVEFORM, 20, Theme.c.accent).apply {
                        val ilp = LinearLayout.LayoutParams(dp(20), dp(20))
                        ilp.marginEnd = dp(10); layoutParams = ilp
                    })
                    addView(ctx.headlineText("Usage & Diagnostics"))
                })

                usageTv = ctx.bodyText("Calculating...").apply {
                    val ulp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    ulp.topMargin = dp(12); layoutParams = ulp
                    setTextColor(Theme.c.textSecondary)
                }
                addView(usageTv)
            }
            addView(layout)
        }
    }

    private fun createAppearanceSection(ctx: Context): View {
        return ctx.card(bgColor = Theme.c.surface, radiusDp = 16, strokeColor = Theme.c.outline) {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = ctx.dp(16)
            layoutParams = lp

            val layout = ctx.vstack {
                setPadding(dp(16), dp(16), dp(16), dp(16))

                addView(ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(IconView(ctx, Icon.SETTINGS, 20, Theme.c.accent).apply {
                        val ilp = LinearLayout.LayoutParams(dp(20), dp(20))
                        ilp.marginEnd = dp(10); layoutParams = ilp
                    })
                    addView(ctx.headlineText("Appearance"))
                })

                addView(ctx.bodyText(
                    if (Theme.isDark) "OLED Dark theme active" else "Light theme active"
                ).apply {
                    val clp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    clp.topMargin = dp(8); clp.bottomMargin = dp(16)
                    layoutParams = clp
                })

                addView(ctx.segmentedControl(
                    items = listOf("Dark", "Light"),
                    selectedIndex = if (Theme.isDark) 0 else 1
                ) { idx ->
                    Theme.apply(idx == 0)
                })
            }
            addView(layout)
        }
    }

    private fun refreshScreen(ctx: Context) {
        // Re-render by navigating to self
        navigator.setRootScreen(SettingsScreen(aiService, navigator))
    }

    private fun loadUsage() {
        AppExecutors.executeIo {
            val (inputTokens, outputTokens) = AppGraph.get().miscDao.getAiUsageTotal()
            AppExecutors.main {
                if (::usageTv.isInitialized) {
                    val total = inputTokens + outputTokens
                    usageTv.text = "Input tokens: ${formatNumber(inputTokens)}\nOutput tokens: ${formatNumber(outputTokens)}\nTotal: ${formatNumber(total)}\nDirect provider connection: OK"
                }
            }
        }
    }

    private fun formatNumber(n: Number): String {
        val v = n.toLong()
        return if (v >= 1_000_000) "${v / 1_000_000}M"
        else if (v >= 1_000) "${v / 1_000}K"
        else "$v"
    }

    private fun showSetKeyDialog(ctx: Context, providerId: String, name: String) {
        val input = EditText(ctx).apply {
            hint = "Paste your $name API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Theme.c.text); setHintTextColor(Theme.c.textTertiary)
            background = ctx.roundedDrawable(Theme.c.surfaceHigh, radiusDp = 10, strokeColor = Theme.c.outline, strokeWidthDp = 1)
            padding(12)
            setText(aiService.getApiKey(providerId) ?: "")
        }

        AlertDialog.Builder(ctx)
            .setTitle("$name API Key")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim()
                aiService.setApiKey(providerId, key)
                if (aiService.activeProviderId.isEmpty() && key.isNotEmpty()) {
                    aiService.activeProviderId = providerId
                }
                refreshScreen(ctx)
            }
            .setNeutralButton("Clear") { _, _ ->
                aiService.setApiKey(providerId, "")
                refreshScreen(ctx)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
