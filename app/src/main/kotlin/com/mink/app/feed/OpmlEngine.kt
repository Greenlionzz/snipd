package com.mink.app.feed

import com.mink.app.data.model.Feed
import com.mink.app.data.model.Folder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.StringWriter

data class OpmlOutline(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String = "",
    val folderName: String? = null
)

object OpmlEngine {

    fun parse(opmlText: String): List<OpmlOutline> {
        val list = mutableListOf<OpmlOutline>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(opmlText))

        var curFolder: String? = null
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                        val text = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title") ?: ""
                        val htmlUrl = parser.getAttributeValue(null, "htmlUrl") ?: ""

                        if (xmlUrl != null && xmlUrl.isNotBlank()) {
                            list.add(
                                OpmlOutline(
                                    title = text,
                                    xmlUrl = xmlUrl,
                                    htmlUrl = htmlUrl,
                                    folderName = curFolder
                                )
                            )
                        } else if (text.isNotBlank()) {
                            curFolder = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        curFolder = null
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    fun export(feeds: List<Feed>, folders: Map<Long, Folder>): String {
        val sw = StringWriter()
        sw.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        sw.write("""<opml version="2.0">""" + "\n")
        sw.write("  <head>\n    <title>Tuned Feeds Export</title>\n  </head>\n")
        sw.write("  <body>\n")

        val grouped = feeds.groupBy { it.folderId }

        for ((folderId, folderFeeds) in grouped) {
            val folder = if (folderId != null) folders[folderId] else null
            if (folder != null) {
                sw.write("""    <outline text="${escape(folder.name)}" title="${escape(folder.name)}">""" + "\n")
                for (f in folderFeeds) {
                    sw.write("""      <outline type="rss" text="${escape(f.title)}" title="${escape(f.title)}" xmlUrl="${escape(f.url)}" htmlUrl="${escape(f.siteUrl)}"/>""" + "\n")
                }
                sw.write("    </outline>\n")
            } else {
                for (f in folderFeeds) {
                    sw.write("""    <outline type="rss" text="${escape(f.title)}" title="${escape(f.title)}" xmlUrl="${escape(f.url)}" htmlUrl="${escape(f.siteUrl)}"/>""" + "\n")
                }
            }
        }

        sw.write("  </body>\n")
        sw.write("</opml>\n")
        return sw.toString()
    }

    private fun escape(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
