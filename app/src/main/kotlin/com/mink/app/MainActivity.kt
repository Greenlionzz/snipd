package com.mink.app

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.mink.app.ai.AiService
import com.mink.app.core.AppGraph
import com.mink.app.net.ImageLoader
import com.mink.app.player.PlaybackManager
import com.mink.app.ui.discover.DiscoverScreen
import com.mink.app.ui.dsl.ToastHost
import com.mink.app.ui.dsl.dp
import com.mink.app.ui.library.LibraryScreen
import com.mink.app.ui.nav.BottomBar
import com.mink.app.ui.nav.ScreenHost
import com.mink.app.ui.news.NewsScreen
import com.mink.app.ui.player.MiniPlayerView
import com.mink.app.ui.settings.SettingsScreen
import com.mink.app.ui.theme.Theme
import java.io.File

class MainActivity : Activity() {

    private lateinit var screenHost: ScreenHost
    private lateinit var bottomBar: BottomBar
    private lateinit var miniPlayer: MiniPlayerView
    private lateinit var aiService: AiService
    private lateinit var imageLoader: ImageLoader
    private lateinit var root: FrameLayout

    private var currentTab = 0
    private var lastBackPressTime = 0L

    private lateinit var discoverScreen: DiscoverScreen
    private lateinit var libraryScreen: LibraryScreen
    private lateinit var newsScreen: NewsScreen
    private lateinit var settingsScreen: SettingsScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Theme.apply(true)  // dark mode

        // Edge-to-edge: transparent bars
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        // Initialize
        AppGraph.init(this)
        PlaybackManager.init(this)
        com.mink.app.podcast.SubscriptionManager.init()
        aiService = AiService(this)
        val imageCacheDir = File(cacheDir, "images").apply { mkdirs() }
        imageLoader = ImageLoader(imageCacheDir)

        root = FrameLayout(this).apply {
            setBackgroundColor(Theme.c.bg)
        }

        // Screen host
        screenHost = ScreenHost(this)
        root.addView(screenHost, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Bottom overlay: mini player + bottom nav
        val bottomOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        miniPlayer = MiniPlayerView(this, imageLoader, screenHost, aiService).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(12), 0, dp(12), dp(8))
            }
        }
        bottomOverlay.addView(miniPlayer)

        bottomBar = BottomBar(this) { tabIndex ->
            currentTab = tabIndex
            when (tabIndex) {
                0 -> screenHost.setRootScreen(discoverScreen)
                1 -> screenHost.setRootScreen(libraryScreen)
                2 -> screenHost.setRootScreen(newsScreen)
                3 -> screenHost.setRootScreen(settingsScreen)
            }
        }
        bottomOverlay.addView(bottomBar)

        root.addView(bottomOverlay)

        // Apply insets
        root.setOnApplyWindowInsetsListener { _, insets ->
            val statusTop = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            val navBottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            screenHost.setPadding(0, statusTop, 0, 0)
            // ScreenHost leaves bottom space ONLY for the actual bottom bar + navigation inset
            // (Mini player floats above bottom bar when active without creating empty space when inactive)
            val overlayHeight = dp(64) + navBottom
            (screenHost.layoutParams as FrameLayout.LayoutParams).bottomMargin = overlayHeight
            screenHost.requestLayout()

            bottomBar.setPadding(0, 0, 0, navBottom)
            insets
        }

        setContentView(root)

        // Screens
        discoverScreen = DiscoverScreen(screenHost, imageLoader, aiService)
        libraryScreen = LibraryScreen(screenHost, imageLoader, aiService)
        newsScreen = NewsScreen(screenHost, aiService)
        settingsScreen = SettingsScreen(aiService, screenHost)

        screenHost.setRootScreen(discoverScreen)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                handleBackPress()
            }
        }
    }

    override fun onDestroy() {
        PlaybackManager.release(this)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) {
            handleBackPress()
        }
    }

    private fun handleBackPress() {
        if (screenHost.hasBackStack()) {
            screenHost.popScreen()
        } else if (currentTab != 0) {
            currentTab = 0
            bottomBar.selectTab(0)
            screenHost.setRootScreen(discoverScreen)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                finish()
            } else {
                lastBackPressTime = now
                ToastHost.show(root, "Press back again to exit")
            }
        }
    }
}
