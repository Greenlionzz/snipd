package com.mink.app.ui.nav

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mink.app.ui.dsl.*
import com.mink.app.ui.theme.AppTheme
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

class BottomBar(
    context: Context,
    private val onTabSelected: (Int) -> Unit
) : LinearLayout(context) {

    private data class TabItem(val title: String, val icon: Icon)

    private val tabs = listOf(
        TabItem("Discover", Icon.DISCOVER),
        TabItem("Library", Icon.LIBRARY),
        TabItem("News", Icon.NEWS),
        TabItem("Settings", Icon.SETTINGS)
    )

    private val tabViews = mutableListOf<LinearLayout>()
    private val iconContainers = mutableListOf<FrameLayout>()
    private val iconViews = mutableListOf<IconView>()
    private val labelViews = mutableListOf<TextView>()
    private val pillViews = mutableListOf<View>()
    private var selectedIndex = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.c.surface)
        minimumHeight = dp(64)

        // Professional top hairline divider
        val divider = View(context).apply {
            setBackgroundColor(Theme.c.outline)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        }
        addView(divider)

        val tabsRow = context.hstack {
            weightSum = tabs.size.toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            tabs.forEachIndexed { index, item ->
                val tabLayout = context.vstack {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(6), 0, dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    background = context.rippleDrawable(Color.TRANSPARENT, radiusDp = 0)
                    onTap { selectTab(index) }
                }

                // Icon container with pill behind
                val iconArea = FrameLayout(context).apply {
                    val iSize = dp(52)
                    layoutParams = LinearLayout.LayoutParams(iSize, dp(28))
                }

                // Active pill behind icon (professional 48x26dp rounded pill)
                val pill = View(context).apply {
                    background = context.roundedDrawable(Theme.c.accentSoft, radiusDp = 13)
                    val plp = FrameLayout.LayoutParams(dp(48), dp(26), Gravity.CENTER)
                    layoutParams = plp
                    alpha = 0f
                    scaleX = 0.5f
                }
                pillViews.add(pill)
                iconArea.addView(pill)

                val iv = IconView(context, item.icon, 22, Theme.c.textTertiary)
                val ivlp = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                iconArea.addView(iv, ivlp)
                iconViews.add(iv)
                iconContainers.add(iconArea)
                tabLayout.addView(iconArea)

                // Label
                val label = context.overlineText(item.title).apply {
                    gravity = Gravity.CENTER
                    textSize = 11f
                    val llp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    llp.topMargin = dp(2)
                    layoutParams = llp
                }
                labelViews.add(label)
                tabLayout.addView(label)

                tabViews.add(tabLayout)
                addView(tabLayout)
            }
        }
        addView(tabsRow)

        updateSelection(false)
    }

    fun selectTab(index: Int) {
        if (index != selectedIndex) {
            selectedIndex = index
            updateSelection(true)
            onTabSelected(index)
        }
    }

    private fun updateSelection(animate: Boolean) {
        tabViews.forEachIndexed { i, _ ->
            val isSel = i == selectedIndex
            val color = if (isSel) Theme.c.accent else Theme.c.textTertiary

            iconViews[i].tint = color
            labelViews[i].setTextColor(color)

            val pill = pillViews[i]
            if (animate) {
                if (isSel) {
                    pill.animate().alpha(1f).scaleX(1f).setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.5f)).start()
                    // Icon bounce
                    iconViews[i].animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).withEndAction {
                        iconViews[i].animate().scaleX(1f).scaleY(1f).setDuration(120)
                            .setInterpolator(OvershootInterpolator(3f)).start()
                    }.start()
                } else {
                    pill.animate().alpha(0f).scaleX(0.5f).setDuration(150).start()
                }
            } else {
                pill.alpha = if (isSel) 1f else 0f
                pill.scaleX = if (isSel) 1f else 0.5f
            }
        }
    }

    fun updateTheme() {
        setBackgroundColor(Theme.c.surface)
        pillViews.forEach { it.background = context.roundedDrawable(Theme.c.accentSoft, radiusDp = 13) }
        updateSelection(false)
    }
}
