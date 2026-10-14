package com.mink.app.feed

import com.mink.app.data.model.Article
import com.mink.app.data.model.Feed
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedFeed(
    val title: String,
    val description: String = "",
    val siteUrl: String = "",
    val iconUrl: String = "",
    val articles: List<Article> = emptyList()
)

object FeedParser {

    fun parse(content: String, feedId: Long): ParsedFeed {
        val trimmed = content.trim()
        return if (trimmed.startsWith("{")) {
            parseJsonFeed(trimmed, feedId)
        } else {
            parseXmlFeed(trimmed, feedId)
        }
    }

    private fun parseJsonFeed(jsonStr: String, feedId: Long): ParsedFeed {
        val root = JSONObject(jsonStr)
        val title = root.optString("title", "Untitled Feed")
        val homePageUrl = root.optString("home_page_url", "")
        val icon = root.optString("icon", root.optString("favicon", ""))
        val itemsArray = root.optJSONArray("items")

        val articles = mutableListOf<Article>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val id = item.optString("id", "")
                val itemTitle = item.optString("title", "Untitled")
                val url = item.optString("url", "")
                val contentHtml = item.optString("content_html", item.optString("content_text", ""))
                val summary = item.optString("summary", "")
                val image = item.optString("image", item.optString("banner_image", ""))
                val datePublished = item.optString("date_published", "")
                val pubTime = parseDate(datePublished)

                articles.add(
                    Article(
                        feedId = feedId,
                        guid = if (id.isNotEmpty()) id else url,
                        title = itemTitle,
                        link = url,
                        contentHtml = contentHtml,
                        summaryText = summary,
                        imageUrl = image,
                        publishedAt = pubTime
                    )
                )
            }
        }
        return ParsedFeed(title = title, siteUrl = homePageUrl, iconUrl = icon, articles = articles)
    }

    private fun parseXmlFeed(xmlStr: String, feedId: Long): ParsedFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlStr))

        var feedTitle = ""
        var siteUrl = ""
        var iconUrl = ""
        val articles = mutableListOf<Article>()

        var inItem = false
        var inChannel = false

        var curGuid = ""
        var curTitle = ""
        var curLink = ""
        var curDesc = ""
        var curContent = ""
        var curPubDate = ""
        var curAuthor = ""
        var curImage = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.lowercase(Locale.ROOT) ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "channel" -> inChannel = true
                        "item", "entry" -> {
                            inItem = true
                            curGuid = ""
                            curTitle = ""
                            curLink = ""
                            curDesc = ""
                            curContent = ""
                            curPubDate = ""
                            curAuthor = ""
                            curImage = ""
                        }
                        "title" -> {
                            val text = parser.nextText().trim()
                            if (inItem) curTitle = text else if (feedTitle.isEmpty()) feedTitle = text
                        }
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            val text = if (href != null) href else {
                                val t = try { parser.nextText() } catch (_: Exception) { "" }
                                t.trim()
                            }
                            if (inItem) {
                                if (curLink.isEmpty()) curLink = text
                            } else if (siteUrl.isEmpty()) {
                                siteUrl = text
                            }
                        }
                        "id", "guid" -> {
                            if (inItem) curGuid = parser.nextText().trim()
                        }
                        "description", "summary" -> {
                            val text = parser.nextText().trim()
                            if (inItem) curDesc = text
                        }
                        "encoded" -> {
                            if (inItem) curContent = parser.nextText().trim()
                        }
                        "pubdate", "published", "updated" -> {
                            val text = parser.nextText().trim()
                            if (inItem) curPubDate = text
                        }
                        "creator", "author" -> {
                            val text = try { parser.nextText().trim() } catch (_: Exception) { "" }
                            if (inItem) curAuthor = text
                        }
                        "enclosure" -> {
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            val url = parser.getAttributeValue(null, "url") ?: ""
                            if (inItem && type.startsWith("image/") && curImage.isEmpty()) {
                                curImage = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "item" || tag == "entry") {
                        inItem = false
                        val guid = if (curGuid.isNotEmpty()) curGuid else if (curLink.isNotEmpty()) curLink else curTitle
                        val content = if (curContent.isNotEmpty()) curContent else curDesc
                        val pubTime = parseDate(curPubDate)

                        articles.add(
                            Article(
                                feedId = feedId,
                                guid = guid,
                                title = if (curTitle.isNotEmpty()) curTitle else "Untitled",
                                author = curAuthor,
                                link = curLink,
                                contentHtml = content,
                                summaryText = stripHtml(curDesc),
                                imageUrl = curImage,
                                publishedAt = pubTime
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = if (feedTitle.isNotEmpty()) feedTitle else "Untitled Feed",
            siteUrl = siteUrl,
            iconUrl = iconUrl,
            articles = articles
        )
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val formats = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(dateStr)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
