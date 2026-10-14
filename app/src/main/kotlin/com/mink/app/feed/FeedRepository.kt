package com.mink.app.feed

import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.EventBus
import com.mink.app.core.Logger
import com.mink.app.core.Result
import com.mink.app.data.model.Feed
import com.mink.app.net.HttpClient

object FeedRepository {

    fun refreshAllFeeds(onProgress: (Int, Int) -> Unit = { _, _ -> }, onComplete: () -> Unit = {}) {
        AppExecutors.executeIo {
            val feeds = AppGraph.get().feedDao.getAll()
            var done = 0
            for (feed in feeds) {
                refreshFeedSync(feed)
                done++
                AppExecutors.main { onProgress(done, feeds.size) }
            }
            AppExecutors.main {
                EventBus.post(FeedsUpdatedEvent)
                onComplete()
            }
        }
    }

    fun refreshFeed(feedId: Long, onComplete: () -> Unit = {}) {
        AppExecutors.executeIo {
            val feed = AppGraph.get().feedDao.getById(feedId)
            if (feed != null) {
                refreshFeedSync(feed)
                AppExecutors.main {
                    EventBus.post(FeedsUpdatedEvent)
                    onComplete()
                }
            }
        }
    }

    fun addFeedFromUrl(url: String, folderId: Long? = null, onResult: (Result<Feed>) -> Unit) {
        AppExecutors.executeIo {
            val discoveryRes = FeedDiscovery.discover(url)
            val feedUrlToUse = if (discoveryRes is Result.Ok && discoveryRes.value.isNotEmpty()) {
                discoveryRes.value.first().url
            } else {
                url
            }

            val headers = mutableMapOf<String, String>()
            val fetchRes = HttpClient.get(feedUrlToUse, headers)
            when (fetchRes) {
                is Result.Ok -> {
                    try {
                        val parsed = FeedParser.parse(fetchRes.value.body, 0)
                        val feed = Feed(
                            url = feedUrlToUse,
                            title = parsed.title.ifEmpty { "Feed" },
                            siteUrl = parsed.siteUrl,
                            iconUrl = parsed.iconUrl,
                            folderId = folderId,
                            etag = fetchRes.value.header("ETag"),
                            lastModified = fetchRes.value.header("Last-Modified"),
                            lastFetched = System.currentTimeMillis()
                        )
                        val id = AppGraph.get().feedDao.insert(feed)
                        val savedFeed = feed.copy(id = id)

                        val articlesWithFeedId = parsed.articles.map { it.copy(feedId = id) }
                        AppGraph.get().articleDao.insertAll(articlesWithFeedId)

                        AppExecutors.main {
                            EventBus.post(FeedsUpdatedEvent)
                            onResult(Result.Ok(savedFeed))
                        }
                    } catch (e: Exception) {
                        Logger.e("FeedRepo", "Failed to parse feed", e)
                        AppExecutors.main { onResult(Result.Err("Failed to parse feed: ${e.message}")) }
                    }
                }
                is Result.Err -> {
                    AppExecutors.main { onResult(Result.Err(fetchRes.message)) }
                }
            }
        }
    }

    private fun refreshFeedSync(feed: Feed) {
        val headers = mutableMapOf<String, String>()
        feed.etag?.let { headers["If-None-Match"] = it }
        feed.lastModified?.let { headers["If-Modified-Since"] = it }

        val res = HttpClient.get(feed.url, headers)
        when (res) {
            is Result.Ok -> {
                if (res.value.statusCode == 304) {
                    AppGraph.get().feedDao.updateMetadata(feed.id, feed.etag, feed.lastModified, System.currentTimeMillis(), 0)
                    return
                }
                try {
                    val parsed = FeedParser.parse(res.value.body, feed.id)
                    val newEtag = res.value.header("ETag") ?: feed.etag
                    val newLastMod = res.value.header("Last-Modified") ?: feed.lastModified
                    AppGraph.get().feedDao.updateMetadata(feed.id, newEtag, newLastMod, System.currentTimeMillis(), 0)

                    val articles = parsed.articles.map { it.copy(feedId = feed.id) }
                    AppGraph.get().articleDao.insertAll(articles)
                } catch (e: Exception) {
                    Logger.e("FeedRepo", "Error refreshing feed ${feed.id}", e)
                    AppGraph.get().feedDao.updateMetadata(feed.id, feed.etag, feed.lastModified, System.currentTimeMillis(), feed.errorCount + 1)
                }
            }
            is Result.Err -> {
                AppGraph.get().feedDao.updateMetadata(feed.id, feed.etag, feed.lastModified, System.currentTimeMillis(), feed.errorCount + 1)
            }
        }
    }
}

object FeedsUpdatedEvent
