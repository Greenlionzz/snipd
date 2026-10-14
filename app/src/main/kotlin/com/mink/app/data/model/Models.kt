package com.mink.app.data.model

data class Podcast(
    val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val website: String = "",
    val itunesId: Long = 0,
    val subscribed: Boolean = false,
    val lastFetched: Long = 0
)

data class Episode(
    val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val descriptionHtml: String = "",
    val audioUrl: String,
    val durationSec: Long = 0,
    val publishedAt: Long = 0,
    val artworkUrl: String = "",
    val positionSec: Long = 0,
    val played: Boolean = false,
    val downloadedPath: String? = null
)

data class EpisodeAi(
    val episodeId: Long,
    val transcriptJson: String = "",
    val transcriptMode: String = "notes", // 'audio' or 'notes'
    val chaptersJson: String = "",
    val summaryJson: String = "",
    val guestsJson: String = "",
    val model: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Snip(
    val id: Long = 0,
    val episodeId: Long,
    val startMs: Long,
    val endMs: Long,
    val title: String = "",
    val transcriptText: String = "",
    val summaryText: String = "",
    val userNote: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val episodeTitle: String = "",
    val podcastTitle: String = ""
)

data class ChatMessage(
    val id: Long = 0,
    val scopeType: String, // 'episode' | 'article'
    val scopeId: Long,
    val role: String, // 'user' | 'assistant'
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Folder(
    val id: Long = 0,
    val name: String,
    val sort: Int = 0
)

data class Feed(
    val id: Long = 0,
    val url: String,
    val title: String,
    val siteUrl: String = "",
    val iconUrl: String = "",
    val folderId: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastFetched: Long = 0,
    val errorCount: Int = 0,
    val unreadCount: Int = 0
)

data class Article(
    val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val author: String = "",
    val link: String = "",
    val contentHtml: String = "",
    val summaryText: String = "",
    val imageUrl: String = "",
    val publishedAt: Long = 0,
    val read: Boolean = false,
    val starred: Boolean = false,
    val aiSummary: String = "",
    val feedTitle: String = ""
)

data class QueueItem(
    val id: Long = 0,
    val episodeId: Long,
    val sort: Int = 0,
    val episode: Episode? = null,
    val podcast: Podcast? = null
)

data class AiUsage(
    val id: Long = 0,
    val provider: String,
    val model: String,
    val feature: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val at: Long = System.currentTimeMillis()
)
