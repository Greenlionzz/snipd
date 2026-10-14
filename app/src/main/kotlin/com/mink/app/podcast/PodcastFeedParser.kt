package com.mink.app.podcast

import com.mink.app.data.model.Episode
import com.mink.app.data.model.Podcast
import com.mink.app.feed.FeedParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class ParsedPodcast(
    val podcast: Podcast,
    val episodes: List<Episode>
)

object PodcastFeedParser {

    fun parse(xmlStr: String, feedUrl: String = "", podcastId: Long = 0): ParsedPodcast {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlStr))

        var title = ""
        var author = ""
        var description = ""
        var artworkUrl = ""
        var website = ""

        val episodes = mutableListOf<Episode>()

        var inItem = false
        var curGuid = ""
        var curTitle = ""
        var curDesc = ""
        var curAudioUrl = ""
        var curDuration = 0L
        var curPubDate = ""
        var curArt = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "item" -> {
                            inItem = true
                            curGuid = ""
                            curTitle = ""
                            curDesc = ""
                            curAudioUrl = ""
                            curDuration = 0L
                            curPubDate = ""
                            curArt = ""
                        }
                        "title" -> {
                            val text = parser.nextText().trim()
                            if (inItem) curTitle = text else if (title.isEmpty()) title = text
                        }
                        "author" -> {
                            val text = try { parser.nextText().trim() } catch (_: Exception) { "" }
                            if (inItem) {} else if (author.isEmpty()) author = text
                        }
                        "description", "summary" -> {
                            val text = parser.nextText().trim()
                            if (inItem) curDesc = text else if (description.isEmpty()) description = text
                        }
                        "link" -> {
                            val text = try { parser.nextText().trim() } catch (_: Exception) { "" }
                            if (!inItem && website.isEmpty()) website = text
                        }
                        "image" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank()) {
                                if (inItem && curArt.isEmpty()) curArt = href
                                else if (!inItem && artworkUrl.isEmpty()) artworkUrl = href
                            }
                        }
                        "guid", "id" -> {
                            if (inItem) curGuid = parser.nextText().trim()
                        }
                        "pubdate", "published" -> {
                            if (inItem) curPubDate = parser.nextText().trim()
                        }
                        "duration" -> {
                            if (inItem) {
                                val durStr = parser.nextText().trim()
                                curDuration = parseDuration(durStr)
                            }
                        }
                        "enclosure" -> {
                            val url = parser.getAttributeValue(null, "url") ?: ""
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (inItem && (type.contains("audio") || type.contains("mp3") || url.endsWith(".mp3") || url.endsWith(".m4a"))) {
                                curAudioUrl = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "item") {
                        inItem = false
                        if (curAudioUrl.isNotEmpty()) {
                            val guid = if (curGuid.isNotEmpty()) curGuid else curAudioUrl
                            episodes.add(
                                Episode(
                                    podcastId = podcastId,
                                    guid = guid,
                                    title = if (curTitle.isNotEmpty()) curTitle else "Untitled Episode",
                                    descriptionHtml = curDesc,
                                    audioUrl = curAudioUrl,
                                    durationSec = curDuration,
                                    publishedAt = parseDate(curPubDate),
                                    artworkUrl = if (curArt.isNotEmpty()) curArt else artworkUrl
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val pod = Podcast(
            id = podcastId,
            feedUrl = feedUrl,
            title = if (title.isNotEmpty()) title else "Podcast",
            author = author,
            description = description,
            artworkUrl = artworkUrl,
            website = website,
            subscribed = true,
            lastFetched = System.currentTimeMillis()
        )

        return ParsedPodcast(pod, episodes)
    }

    private fun parseDuration(s: String): Long {
        if (s.isBlank()) return 0
        if (s.toLongOrNull() != null) return s.toLong()
        val parts = s.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun parseDate(s: String): Long {
        // reuse standard date parsing
        return try {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US).parse(s)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
