package com.mink.app.feed

import com.mink.app.core.Result
import com.mink.app.net.HttpClient
import java.net.URI
import java.net.URL
import java.util.regex.Pattern

data class DiscoveredFeed(
    val title: String,
    val url: String,
    val type: String
)

object FeedDiscovery {

    fun discover(inputUrl: String): Result<List<DiscoveredFeed>> {
        val normalizedUrl = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
            "https://$inputUrl"
        } else {
            inputUrl
        }

        // Try direct fetch first
        val fetchResult = HttpClient.get(normalizedUrl)
        if (fetchResult.isErr) {
            return Result.Err(fetchResult.onError {}.toString())
        }

        val body = (fetchResult as Result.Ok).value.body
        val trimmed = body.trim()

        // Check if the URL itself is already a feed
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<rss") || trimmed.startsWith("<feed") || trimmed.startsWith("{")) {
            return Result.Ok(listOf(DiscoveredFeed("Direct Feed", normalizedUrl, "direct")))
        }

        // Otherwise parse HTML for <link rel="alternate" ...>
        val discovered = mutableListOf<DiscoveredFeed>()
        val linkPattern = Pattern.compile("<link[^>]+rel=[\"']alternate[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
        val matcher = linkPattern.matcher(body)

        while (matcher.find()) {
            val tag = matcher.group()
            val href = extractAttr(tag, "href")
            val type = extractAttr(tag, "type")
            val title = extractAttr(tag, "title") ?: "Feed"

            if (href != null && (type == null || type.contains("rss") || type.contains("atom") || type.contains("xml") || type.contains("json"))) {
                val resolvedUrl = resolveUrl(normalizedUrl, href)
                discovered.add(DiscoveredFeed(title, resolvedUrl, type ?: "rss"))
            }
        }

        // Also check common paths if none found
        if (discovered.isEmpty()) {
            val commonPaths = listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml")
            val baseUri = try { URI(normalizedUrl) } catch (_: Exception) { null }
            if (baseUri != null && baseUri.host != null) {
                val origin = "${baseUri.scheme}://${baseUri.host}${if (baseUri.port > 0) ":${baseUri.port}" else ""}"
                for (path in commonPaths) {
                    val candidate = "$origin$path"
                    val testResp = HttpClient.get(candidate, timeoutMs = 4000)
                    if (testResp.isOk) {
                        val cBody = (testResp as Result.Ok).value.body.trim()
                        if (cBody.startsWith("<?xml") || cBody.startsWith("<rss") || cBody.startsWith("<feed")) {
                            discovered.add(DiscoveredFeed(path.removePrefix("/"), candidate, "rss"))
                            break
                        }
                    }
                }
            }
        }

        return Result.Ok(discovered)
    }

    private fun extractAttr(tag: String, name: String): String? {
        val pattern = Pattern.compile("$name=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val m = pattern.matcher(tag)
        return if (m.find()) m.group(1) else null
    }

    private fun resolveUrl(base: String, relative: String): String {
        return try {
            URL(URL(base), relative).toString()
        } catch (_: Exception) {
            relative
        }
    }
}
