package com.mink.app.core

import java.util.Locale

object TimeFmt {
    fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatDurationMs(totalMs: Long): String {
        return formatDuration(totalMs / 1000)
    }

    fun formatRelativeTime(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        if (diff < 0) return "Just now"

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 30 -> "${days / 7}w ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }
}
