package com.mink.app.ui.theme

import android.graphics.Color

/**
 * Backward-compat facade. New code should use Theme.c directly.
 * Old screen code that still references AppTheme.xxx continues to compile.
 */
object AppTheme {
    var isDark: Boolean
        get() = Theme.isDark
        set(v) { Theme.apply(v) }

    val background: Int         get() = Theme.c.bg
    val surface: Int            get() = Theme.c.surface
    val surfaceHigh: Int        get() = Theme.c.surfaceHigh
    val outline: Int            get() = Theme.c.outline
    val textPrimary: Int        get() = Theme.c.text
    val textSecondary: Int      get() = Theme.c.textSecondary
    val textTertiary: Int       get() = Theme.c.textTertiary
    val accent: Int             get() = Theme.c.accent
    val accentCyan: Int         get() = Theme.c.accent   // folded into accent
    val accentSoft: Int         get() = Theme.c.accentSoft
    val success: Int            get() = Theme.c.success
    val warning: Int            get() = Theme.c.warning
    val error: Int              get() = Theme.c.error
    val ripple: Int             get() = Theme.c.ripple
    val scrim: Int              get() = Theme.c.scrim
    val glassSurface: Int       get() = Color.argb(0xE6, Color.red(surface), Color.green(surface), Color.blue(surface))
}
