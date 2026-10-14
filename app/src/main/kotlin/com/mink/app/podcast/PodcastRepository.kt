package com.mink.app.podcast

import com.mink.app.core.AppExecutors
import com.mink.app.core.AppGraph
import com.mink.app.core.EventBus
import com.mink.app.core.Result
import com.mink.app.data.model.Episode
import com.mink.app.data.model.Podcast
import com.mink.app.net.HttpClient

object PodcastRepository {

    fun fetchAndSavePodcast(podcast: Podcast, subscribed: Boolean = false, onComplete: (Result<Podcast>) -> Unit) {
        AppExecutors.executeIo {
            val res = HttpClient.get(podcast.feedUrl)
            when (res) {
                is Result.Ok -> {
                    try {
                        val parsed = PodcastFeedParser.parse(res.value.body, podcast.feedUrl)
                        val existing = AppGraph.get().podcastDao.getByFeedUrl(podcast.feedUrl)
                        val isSub = if (existing != null) (if (subscribed) true else existing.subscribed) else subscribed
                        val toSave = parsed.podcast.copy(
                            id = existing?.id ?: 0,
                            artworkUrl = if (parsed.podcast.artworkUrl.isNotEmpty()) parsed.podcast.artworkUrl else podcast.artworkUrl,
                            author = if (parsed.podcast.author.isNotEmpty()) parsed.podcast.author else podcast.author,
                            itunesId = podcast.itunesId,
                            subscribed = isSub
                        )
                        val id = AppGraph.get().podcastDao.insertOrUpdate(toSave)
                        val finalPod = toSave.copy(id = id)

                        val episodesWithPodId = parsed.episodes.map { it.copy(podcastId = id) }
                        AppGraph.get().episodeDao.insertAll(episodesWithPodId)

                        AppExecutors.main {
                            if (isSub) EventBus.post(PodcastsUpdatedEvent)
                            onComplete(Result.Ok(finalPod))
                        }
                    } catch (e: Exception) {
                        AppExecutors.main { onComplete(Result.Err("Failed to parse podcast: ${e.message}")) }
                    }
                }
                is Result.Err -> {
                    AppExecutors.main { onComplete(Result.Err(res.message, res.code)) }
                }
            }
        }
    }

    fun subscribe(podcast: Podcast, onComplete: (Result<Podcast>) -> Unit) {
        fetchAndSavePodcast(podcast, subscribed = true, onComplete = onComplete)
    }

    fun unsubscribe(podcastId: Long, onComplete: (() -> Unit)? = null) {
        AppExecutors.executeIo {
            AppGraph.get().podcastDao.setSubscribed(podcastId, false)
            AppExecutors.main {
                EventBus.post(PodcastsUpdatedEvent)
                onComplete?.invoke()
            }
        }
    }

    fun isSubscribed(feedUrl: String, onResult: (Boolean, Long?) -> Unit) {
        AppExecutors.executeIo {
            val pod = AppGraph.get().podcastDao.getByFeedUrl(feedUrl)
            val subbed = pod?.subscribed == true
            val id = pod?.id
            AppExecutors.main { onResult(subbed, id) }
        }
    }
}

object PodcastsUpdatedEvent
