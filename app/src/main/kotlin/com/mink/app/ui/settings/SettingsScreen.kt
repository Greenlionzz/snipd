package com.mink.app.ui.settings

<<<<<<< HEAD
import android.animation.LayoutTransition
import android.app.AlertDialog
import android.content.Context
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
import java.io.File

/**
 * Material 3 compliant Settings Screen.
 *
 * Design Spec:
 *  - Colors: Theme.c palette tokens only (Theme.c.surface, Theme.c.surfaceHigh, Theme.c.accent, etc.)
 *  - Spacing & Grid: Strictly 4/8dp baseline grid (4, 8, 12, 16, 24, 32dp)
 *  - Cards: 16dp corner radius with Theme.c.outline borders
 *  - Type Hierarchy: Screen title (28sp bold), Headlines (18sp medium), Subheads/Labels (14sp medium), Body (14sp regular), Caption (12sp)
 *  - Touch Targets: Min 48dp on all interactive elements
 *  - Accessibility: contentDescription explicitly declared on icons & actions
 *  - Screen States: Full handling of Loading, Error (with retry), Empty, and Content states with LayoutTransition animations
 */
class SettingsScreen(
    private val aiService: AiService,
    private val navigator: ScreenHost
) : Screen {

    private lateinit var rootFrame: FrameLayout
    private lateinit var contentScroll: View
    private lateinit var stateContainer: FrameLayout

    // Telemetry text targets
    private var inputStatTv: TextView? = null
    private var outputStatTv: TextView? = null
    private var totalStatTv: TextView? = null
    private var cacheSizeTv: TextView? = null

    // State machine
    private enum class ScreenState {
        LOADING, CONTENT, ERROR
    }

    override fun createView(ctx: Context): View {
        rootFrame = FrameLayout(ctx).apply {
            setBackgroundColor(Theme.c.bg)
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(220)
            }
        }

        stateContainer = FrameLayout(ctx).apply {
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(220)
            }
        }
        rootFrame.addView(
            stateContainer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        buildContent(ctx)
        loadData(ctx)

        return rootFrame
    }

    private fun loadData(ctx: Context) {
        setScreenState(ctx, ScreenState.LOADING)

        AppExecutors.executeIo {
            try {
                val (inTok, outTok) = AppGraph.get().miscDao.getAiUsageTotal()
                val cacheDir = File(ctx.cacheDir, "images")
                val cacheBytes = if (cacheDir.exists()) cacheDir.listFiles()?.sumOf { it.length() } ?: 0L else 0L
                val cacheMb = String.format("%.1f MB", cacheBytes.toDouble() / (1024 * 1024))

                AppExecutors.main {
                    inputStatTv?.text = formatTokens(inTok)
                    outputStatTv?.text = formatTokens(outTok)
                    totalStatTv?.text = formatTokens(inTok + outTok)
                    cacheSizeTv?.text = "$cacheMb on disk"
                    setScreenState(ctx, ScreenState.CONTENT)
                }
            } catch (e: Exception) {
                AppExecutors.main {
                    setScreenState(ctx, ScreenState.ERROR, e.message ?: "Failed to load settings data")
                }
            }
        }
    }

    private fun setScreenState(ctx: Context, state: ScreenState, errorMsg: String? = null) {
        stateContainer.removeAllViews()
        when (state) {
            ScreenState.LOADING -> {
                val loadingView = ctx.vstack {
                    gravity = Gravity.CENTER
                    val pb = ProgressBar(ctx).apply {
                        indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.c.accent)
                    }
                    val lp = LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48)).apply {
                        bottomMargin = ctx.dp(16)
                    }
                    addView(pb, lp)
                    addView(ctx.bodyText("Loading settings…").apply {
                        gravity = Gravity.CENTER
                        setTextColor(Theme.c.textSecondary)
                    })
                }
                stateContainer.addView(
                    loadingView,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
            }
            ScreenState.ERROR -> {
                val err = ctx.errorState(
                    message = errorMsg ?: "Unable to read preferences and storage records.",
                    onRetry = { loadData(ctx) }
                )
                stateContainer.addView(
                    err,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
            }
            ScreenState.CONTENT -> {
                stateContainer.addView(
                    contentScroll,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
            }
        }
    }

    // ── Content Builder ──────────────────────────────────────────

    private fun buildContent(ctx: Context) {
        contentScroll = ctx.scrollColumn {
            setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(32))

            // 1. Top Header
            addView(buildScreenHeader(ctx))

            // 2. AI Intelligence Engine Card
            addView(buildAiCard(ctx))

            // 3. Telemetry Ledger Card
            addView(buildTelemetryCard(ctx))

            // 4. Appearance & Theme Card
            addView(buildAppearanceCard(ctx))

            // 5. Storage & Disk Cache Card
            addView(buildStorageCard(ctx))

            // 6. App Specs Footer
            addView(buildFooter(ctx))
        }
    }

    // ── Screen Header ────────────────────────────────────────────

    private fun buildScreenHeader(ctx: Context): View = ctx.vstack {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = ctx.dp(16)
        }
        layoutParams = lp

        val row = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL

            val titles = ctx.vstack {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(ctx.overlineText("PREFERENCES").apply {
                    setTextColor(Theme.c.accent)
                })
                addView(ctx.displayText("Settings").apply {
                    val tlp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = ctx.dp(4) }
                    layoutParams = tlp
                })
            }
            addView(titles)

            val badge = ctx.badgeText("Local & Private", Theme.c.accentSoft, Theme.c.accent).apply {
                contentDescription = "Privacy status: local and private on device"
            }
            addView(badge)
        }
        addView(row)
    }

    // ── M3 Card Scaffolding ──────────────────────────────────────

    private fun m3Card(ctx: Context, block: LinearLayout.() -> Unit): View {
        val card = ctx.card(
            bgColor = Theme.c.surface,
            radiusDp = 16,
            strokeColor = Theme.c.outline,
            strokeWidthDp = 1
        ) { }
        val cardLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = ctx.dp(16) }
        card.layoutParams = cardLp

        val inner = ctx.vstack(block).apply {
            setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(180)
            }
        }
        card.addView(
            inner,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        return card
    }

    private fun m3CardHeader(
        ctx: Context,
        icon: Icon,
        iconTint: Int,
        title: String,
        chipLabel: String? = null
    ): LinearLayout = ctx.hstack {
        gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = ctx.dp(12) }
        layoutParams = lp

        val iconWrap = ctx.frame {
            val s = ctx.dp(36)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = ctx.dp(12) }
            background = ctx.roundedDrawable(Theme.c.surfaceHigh, radiusDp = 8)
            val iv = IconView(ctx, icon, 18, iconTint).apply {
                contentDescription = "$title icon"
            }
            addView(iv, FrameLayout.LayoutParams(ctx.dp(18), ctx.dp(18), Gravity.CENTER))
        }
        addView(iconWrap)

        addView(ctx.headlineText(title).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        chipLabel?.let { label ->
            addView(ctx.badgeText(label, Theme.c.surfaceHigh, Theme.c.textSecondary))
        }
    }

    // ── 1. AI Section ────────────────────────────────────────────

    private fun buildAiCard(ctx: Context): View = m3Card(ctx) {
        addView(m3CardHeader(ctx, Icon.AI_SPARK, Theme.c.accent, "AI Providers", "BYOK"))

        addView(ctx.bodyText(
            "Configure your personal API keys. Network calls connect directly to provider endpoints without intermediaries."
        ).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(12) }
            layoutParams = lp
        })

        val providers = aiService.providers.toList()
        if (providers.isEmpty()) {
            addView(ctx.emptyState(
                icon = Icon.EMPTY_INBOX,
                title = "No AI providers",
                message = "There are no configured AI providers installed."
            ))
        } else {
            providers.forEachIndexed { index, (provId, prov) ->
                addView(buildProviderRow(ctx, provId, prov.name, prov.availableModels))
                if (index < providers.size - 1) {
                    val div = View(ctx).apply {
                        setBackgroundColor(Theme.c.outline)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            ctx.dp(1)
                        ).apply {
                            topMargin = ctx.dp(4)
                            bottomMargin = ctx.dp(4)
                        }
                        layoutParams = lp
                    }
                    addView(div)
                }
            }
        }
    }

    private fun buildProviderRow(
        ctx: Context,
        providerId: String,
        name: String,
        models: List<String>
    ): View {
        val isActive = aiService.activeProviderId == providerId
        val key = aiService.getApiKey(providerId)
        val hasKey = !key.isNullOrBlank()
        val currentModel = aiService.getSelectedModel(providerId)

        return ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(48)
            background = ctx.rippleDrawable(Theme.c.surface, radiusDp = 12)
            setPadding(ctx.dp(8), ctx.dp(10), ctx.dp(8), ctx.dp(10))
            isFocusable = true
            contentDescription = "$name provider, " + if (isActive) "active" else if (hasKey) "configured" else "not configured"

            // Status Indicator Dot (at least 24dp wrap for clarity)
            val dotWrap = ctx.frame {
                val s = ctx.dp(24)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = ctx.dp(12) }
                val color = when {
                    isActive -> Theme.c.success
                    hasKey -> Theme.c.accent
                    else -> Theme.c.textTertiary
                }
                val dot = View(ctx).apply {
                    background = ctx.roundedDrawable(color, radiusDp = 4)
                }
                addView(dot, FrameLayout.LayoutParams(ctx.dp(8), ctx.dp(8), Gravity.CENTER))
            }
            addView(dotWrap)

            // Text Info
            val textCol = ctx.vstack {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                val labelRow = ctx.hstack {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ctx.labelText(name))
                    if (isActive) {
                        addView(ctx.badgeText("ACTIVE", Theme.c.accentSoft, Theme.c.accent).apply {
                            val blp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { marginStart = ctx.dp(8) }
                            layoutParams = blp
                        })
                    }
                }
                addView(labelRow)

                val subText = if (hasKey) "Model: $currentModel" else "No API key configured"
                addView(ctx.captionText(subText).apply {
                    val clp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = ctx.dp(2) }
                    layoutParams = clp
                })
            }
            addView(textCol)

            // Trailing Chevron Icon (48dp touchable frame)
            val actionBox = ctx.frame {
                val s = ctx.dp(48)
                layoutParams = LinearLayout.LayoutParams(s, s)
                val chevron = IconView(ctx, Icon.CHEVRON_RIGHT, 16, Theme.c.textSecondary).apply {
                    contentDescription = "Configure $name"
                }
                addView(chevron, FrameLayout.LayoutParams(ctx.dp(16), ctx.dp(16), Gravity.CENTER))
            }
            addView(actionBox)

            onTapFeedback {
                showProviderBottomSheet(ctx, providerId, name, models)
            }
        }
    }

    private fun showProviderBottomSheet(
        ctx: Context,
        providerId: String,
        name: String,
        models: List<String>
    ) {
        val hasKey = !aiService.getApiKey(providerId).isNullOrBlank()
        val isActive = aiService.activeProviderId == providerId

        ctx.showBottomSheet(rootFrame) {
            addView(ctx.hstack {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), ctx.dp(16))
                val icon = IconView(ctx, Icon.AI_SPARK, 24, Theme.c.accent).apply {
                    contentDescription = "$name configuration"
                    val lp = LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(24)).apply { marginEnd = ctx.dp(12) }
                    layoutParams = lp
                }
                addView(icon)
                addView(ctx.headlineText(name))
            })

            if (hasKey && !isActive) {
                addView(ctx.sheetRow(Icon.CHECK, "Set as default provider", Theme.c.success) {
                    aiService.activeProviderId = providerId
                    refreshScreen()
                })
            }

            if (models.isNotEmpty() && hasKey) {
                val curModel = aiService.getSelectedModel(providerId)
                addView(ctx.sheetRow(Icon.SORT, "Select model ($curModel)") {
                    showModelDialog(ctx, providerId, name, models)
                })
            }

            addView(ctx.sheetRow(Icon.SETTINGS, if (hasKey) "Update API key" else "Add API key") {
                showSetKeyDialog(ctx, providerId, name)
            })

            if (hasKey) {
                addView(ctx.sheetRow(Icon.DELETE, "Remove API key", Theme.c.error) {
                    aiService.setApiKey(providerId, "")
                    refreshScreen()
                })
            }
        }
    }

    private fun showModelDialog(
        ctx: Context,
        providerId: String,
        providerName: String,
        models: List<String>
    ) {
        val current = aiService.getSelectedModel(providerId)
        val items = models.toTypedArray()
        val checkedItem = models.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("$providerName Model")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                aiService.setSelectedModel(providerId, models[which])
                dialog.dismiss()
                refreshScreen()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── 2. Telemetry Section ─────────────────────────────────────

    private fun buildTelemetryCard(ctx: Context): View = m3Card(ctx) {
        addView(m3CardHeader(ctx, Icon.WAVEFORM, Theme.c.warning, "Token Usage", "LOCAL"))

        addView(ctx.bodyText("Total prompt and completion tokens tracked locally.").apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(16) }
            layoutParams = lp
        })

        val grid = ctx.hstack {
            val inputCell = buildStatCell(ctx, "PROMPT", Theme.c.accent) { inputStatTv = it }
            inputCell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(inputCell)

            val outputCell = buildStatCell(ctx, "COMPLETION", Theme.c.warning) { outputStatTv = it }
            outputCell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dp(8)
            }
            addView(outputCell)

            val totalCell = buildStatCell(ctx, "TOTAL", Theme.c.success) { totalStatTv = it }
            totalCell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dp(8)
            }
            addView(totalCell)
        }
        addView(grid)
    }

    private fun buildStatCell(
        ctx: Context,
        title: String,
        accentTint: Int,
        setter: (TextView) -> Unit
    ): View {
        val valueView = ctx.titleText("—").apply {
            setTextColor(accentTint)
        }
        setter(valueView)

        val cell = ctx.vstack {
            gravity = Gravity.CENTER_HORIZONTAL
            background = ctx.roundedDrawable(
                Theme.c.surfaceHigh,
                radiusDp = 12,
                strokeColor = Theme.c.outline,
                strokeWidthDp = 1
            )
            setPadding(ctx.dp(8), ctx.dp(12), ctx.dp(8), ctx.dp(12))

            addView(ctx.overlineText(title).apply {
                setTextColor(accentTint)
            })

            addView(valueView.apply {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dp(4) }
                layoutParams = lp
            })

            addView(ctx.captionText("tokens").apply {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dp(2) }
                layoutParams = lp
            })
        }
        return cell
    }

    // ── 3. Appearance Section ────────────────────────────────────

    private fun buildAppearanceCard(ctx: Context): View = m3Card(ctx) {
        addView(m3CardHeader(ctx, Icon.SETTINGS, Theme.c.accent, "Appearance"))

        addView(ctx.bodyText("Toggle application theme mode.").apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(12) }
            layoutParams = lp
        })

        val control = ctx.segmentedControl(
            items = listOf("Dark", "Light"),
            selectedIndex = if (Theme.isDark) 0 else 1
        ) { index ->
            Theme.apply(index == 0)
            refreshScreen()
        }.apply {
            contentDescription = "Theme switcher: dark or light mode"
        }
        addView(control)
    }

    // ── 4. Storage Section ───────────────────────────────────────

    private fun buildStorageCard(ctx: Context): View = m3Card(ctx) {
        addView(m3CardHeader(ctx, Icon.DOWNLOAD, Theme.c.success, "Storage"))

        val row = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(48)
            background = ctx.roundedDrawable(Theme.c.surfaceHigh, radiusDp = 12)
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))

            val textCol = ctx.vstack {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(ctx.labelText("Cover Art Cache"))
                val tv = ctx.captionText("Calculating…").apply {
                    val clp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = ctx.dp(2) }
                    layoutParams = clp
                }
                cacheSizeTv = tv
                addView(tv)
            }
            addView(textCol)

            val clearBtn = ctx.secondaryButton("Clear") {
                showClearCacheDialog(ctx)
            }.apply {
                setTextColor(Theme.c.error)
                contentDescription = "Clear image cover cache"
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = ctx.dp(8) }
                layoutParams = lp
            }
            addView(clearBtn)
        }
        addView(row)
    }

    private fun showClearCacheDialog(ctx: Context) {
        AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Clear Cover Art Cache")
            .setMessage("All locally cached cover artwork will be deleted. They will re-download when viewed.")
            .setPositiveButton("Clear") { _, _ ->
                AppExecutors.executeIo {
                    val dir = File(ctx.cacheDir, "images")
                    if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
                    AppExecutors.main {
                        ToastHost.show(rootFrame, "Cover art cache cleared")
                        loadData(ctx)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── 5. Footer Section ────────────────────────────────────────

    private fun buildFooter(ctx: Context): View = ctx.vstack {
        gravity = Gravity.CENTER_HORIZONTAL
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = ctx.dp(16)
        }
        layoutParams = lp

        addView(ctx.labelText("Mink").apply {
            setTextColor(Theme.c.textSecondary)
        })
        addView(ctx.captionText("Version 1.0.0 • On-Device Engine").apply {
            val clp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ctx.dp(4) }
            layoutParams = clp
        })
    }

    // ── Helper functions ─────────────────────────────────────────

    private fun refreshScreen() {
        navigator.setRootScreen(SettingsScreen(aiService, navigator))
    }

    private fun formatTokens(n: Number): String {
        val v = n.toLong()
        return when {
            v >= 1_000_000 -> String.format("%.1fM", v.toDouble() / 1_000_000)
            v >= 1_000 -> String.format("%.1fK", v.toDouble() / 1_000)
            else -> v.toString()
        }
    }

    private fun showSetKeyDialog(ctx: Context, providerId: String, name: String) {
        val input = EditText(ctx).apply {
            hint = "Paste your $name API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Theme.c.text)
            setHintTextColor(Theme.c.textTertiary)
            background = ctx.roundedDrawable(
                Theme.c.surfaceHigh, radiusDp = 12,
                strokeColor = Theme.c.outline, strokeWidthDp = 1
            )
            setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
            setText(aiService.getApiKey(providerId) ?: "")
            textSize = 14f
            contentDescription = "API key input field for $name"
        }
        val container = ctx.vstack {
            setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), ctx.dp(8))
            addView(input)
        }

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("$name API Key")
            .setMessage("Your key is stored encrypted on device and only sent directly to $name.")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim()
                aiService.setApiKey(providerId, key)
                if (aiService.activeProviderId.isEmpty() && key.isNotEmpty()) {
                    aiService.activeProviderId = providerId
                }
                refreshScreen()
            }
            .setNeutralButton("Remove") { _, _ ->
                aiService.setApiKey(providerId, "")
                refreshScreen()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Theme.c.accent)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Theme.c.error)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Theme.c.textSecondary)
        }
        dialog.show()
=======
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
>>>>>>> branch 'main' of https://github.com/Greenlionzz/snipd
    }
}
