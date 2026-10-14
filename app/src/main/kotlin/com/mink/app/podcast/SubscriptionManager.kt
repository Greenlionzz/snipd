package com.mink.app.podcast

import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.EventBus

object SubscriptionManager {

    private val subscribedUrls = mutableSetOf<String>()
    private val urlToIdMap = mutableMapOf<String, Long>()
    private var isLoaded = false

    fun init() {
        refresh()
        EventBus.subscribe { event ->
            if (event is PodcastsUpdatedEvent) {
                refresh()
            }
        }
    }

    fun refresh(onComplete: (() -> Unit)? = null) {
        AppExecutors.executeIo {
            val list = AppGraph.get().podcastDao.getAllSubscribed()
            val urls = mutableSetOf<String>()
            val ids = mutableMapOf<String, Long>()
            for (p in list) {
                urls.add(p.feedUrl)
                ids[p.feedUrl] = p.id
            }
            AppExecutors.main {
                subscribedUrls.clear()
                subscribedUrls.addAll(urls)
                urlToIdMap.clear()
                urlToIdMap.putAll(ids)
                isLoaded = true
                onComplete?.invoke()
            }
        }
    }

    fun isSubscribed(feedUrl: String): Boolean {
        return subscribedUrls.contains(feedUrl)
    }

    fun getPodcastId(feedUrl: String): Long? {
        return urlToIdMap[feedUrl]
    }

    fun setSubscribedLocally(feedUrl: String, subbed: Boolean, id: Long? = null) {
        if (subbed) {
            subscribedUrls.add(feedUrl)
            if (id != null) urlToIdMap[feedUrl] = id
        } else {
            subscribedUrls.remove(feedUrl)
        }
        EventBus.post(SubscriptionChangedEvent(feedUrl, subbed))
    }
}

data class SubscriptionChangedEvent(val feedUrl: String, val isSubscribed: Boolean)

