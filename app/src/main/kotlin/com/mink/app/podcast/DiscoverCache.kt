package com.mink.app.podcast

import com.mink.app.data.model.Podcast

object DiscoverCache {

    var heroPodcasts: List<Podcast>? = null
    var trendingPodcasts: List<Podcast>? = null
    var promoPodcast: Podcast? = null

    // Shelves: title -> List<Podcast>
    val shelves = mutableMapOf<String, List<Podcast>>()
    var popularPodcasts: List<Podcast>? = null

    // Cache of all loaded podcasts mapped by feedUrl or ID for instant access anywhere
    val podcastCatalogByFeed = mutableMapOf<String, Podcast>()
    val podcastCatalogById = mutableMapOf<Long, Podcast>()

    fun hasData(): Boolean {
        return !heroPodcasts.isNullOrEmpty() && !trendingPodcasts.isNullOrEmpty()
    }

    fun cachePodcasts(pods: List<Podcast>) {
        for (p in pods) {
            if (p.feedUrl.isNotEmpty()) podcastCatalogByFeed[p.feedUrl] = p
            if (p.id > 0) podcastCatalogById[p.id] = p
        }
    }

    fun getPodcastByFeed(feedUrl: String): Podcast? = podcastCatalogByFeed[feedUrl]
    fun getPodcastById(id: Long): Podcast? = podcastCatalogById[id]
}
