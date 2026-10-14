package com.mink.app.ui.nav

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import java.util.Stack

class ScreenHost(context: Context) : FrameLayout(context) {

    private val backStack = Stack<Screen>()
    private var currentView: View? = null
    private val screenViewCache = mutableMapOf<Screen, View>()

    val currentScreen: Screen? get() = if (backStack.isNotEmpty()) backStack.peek() else null

    fun pushScreen(screen: Screen) {
        currentScreen?.onHide()
        backStack.push(screen)
        showScreenView(screen, animate = true)
    }

    fun popScreen(): Boolean {
        if (backStack.size > 1) {
            val popped = backStack.pop()
            popped.onHide()
            val next = backStack.peek()
            showScreenView(next, animate = false)
            return true
        }
        return false
    }

    fun setRootScreen(screen: Screen) {
        // Save current screen state
        currentScreen?.onHide()
        backStack.clear()
        backStack.push(screen)
        showScreenView(screen, animate = false)
    }

    fun hasBackStack(): Boolean = backStack.size > 1

    private fun showScreenView(screen: Screen, animate: Boolean) {
        val cached = screenViewCache[screen]
        val newView = cached ?: screen.createView(context).also {
            screenViewCache[screen] = it
        }
        val oldView = currentView

        if (newView.parent != null) {
            (newView.parent as? android.view.ViewGroup)?.removeView(newView)
        }

        addView(newView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        screen.onShow()

        if (animate && oldView != null) {
            newView.alpha = 0f
            newView.translationX = 100f
            newView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180)
                .withEndAction {
                    removeView(oldView)
                }
                .start()
        } else {
            if (oldView != null) {
                removeView(oldView)
            }
        }
        currentView = newView
    }
}
