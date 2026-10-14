package com.mink.app.ui.theme

import android.graphics.Color

data class Palette(
    val bg: Int, val surface: Int, val surfaceHigh: Int, val outline: Int,
    val text: Int, val textSecondary: Int, val textTertiary: Int,
    val accent: Int, val onAccent: Int, val accentSoft: Int,
    val success: Int, val warning: Int, val error: Int,
    val scrim: Int, val ripple: Int
)

object Theme {
    val dark = Palette(
        bg           = 0xFF0E0F13.toInt(),
        surface      = 0xFF16181F.toInt(),
        surfaceHigh  = 0xFF1E212A.toInt(),
        outline      = 0x1FFFFFFF,
        text         = 0xFFF2F3F7.toInt(),
        textSecondary= 0xFFA5A9B8.toInt(),
        textTertiary = 0xFF6F7385.toInt(),
        accent       = 0xFF7C8CFF.toInt(),
        onAccent     = 0xFF0E0F13.toInt(),
        accentSoft   = 0x297C8CFF.toInt(),
        success      = 0xFF4ADE80.toInt(),
        warning      = 0xFFFBBF24.toInt(),
        error        = 0xFFF87171.toInt(),
        scrim        = 0x99000000.toInt(),
        ripple       = 0x22FFFFFF
    )

    val light = Palette(
        bg           = 0xFFF7F8FB.toInt(),
        surface      = 0xFFFFFFFF.toInt(),
        surfaceHigh  = 0xFFEEF0F6.toInt(),
        outline      = 0x1F12141A,
        text         = 0xFF12141A.toInt(),
        textSecondary= 0xFF5B6070.toInt(),
        textTertiary = 0xFF8D92A3.toInt(),
        accent       = 0xFF4F5FE0.toInt(),
        onAccent     = 0xFFFFFFFF.toInt(),
        accentSoft   = 0x1F4F5FE0,
        success      = 0xFF16A34A.toInt(),
        warning      = 0xFFB45309.toInt(),
        error        = 0xFFDC2626.toInt(),
        scrim        = 0x66000000,
        ripple       = 0x1A12141A
    )

    var c: Palette = dark
        private set

    var isDark: Boolean = true
        private set

    fun apply(dark: Boolean) {
        isDark = dark
        c = if (dark) this.dark else light
    }

    /** Blend a color into bg at the given alpha (0..1) – used for artwork-tinted surfaces. */
    fun tint(base: Int, alpha: Float): Int {
        val r = ((Color.red(base) * alpha) + (Color.red(c.bg) * (1f - alpha))).toInt().coerceIn(0, 255)
        val g = ((Color.green(base) * alpha) + (Color.green(c.bg) * (1f - alpha))).toInt().coerceIn(0, 255)
        val b = ((Color.blue(base) * alpha) + (Color.blue(c.bg) * (1f - alpha))).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
