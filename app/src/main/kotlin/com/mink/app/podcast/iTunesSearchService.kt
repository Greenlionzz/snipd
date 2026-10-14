package com.mink.app.podcast

import com.mink.app.core.AppExecutors
import com.mink.app.core.ErrorCode
import com.mink.app.core.Logger
import com.mink.app.core.Result
import com.mink.app.data.model.Podcast
import com.mink.app.net.HttpClient
import org.json.JSONObject
import java.net.URLEncoder

object iTunesSearchService {

    fun search(term: String, limit: Int = 20, onResult: (Result<List<Podcast>>) -> Unit) {
        AppExecutors.executeIo {
            try {
                val encoded = URLEncoder.encode(term, "UTF-8")
                val url = "https://itunes.apple.com/search?term=$encoded&media=podcast&entity=podcast&limit=$limit"
                val res = HttpClient.get(url)
                when (res) {
                    is Result.Ok -> {
                        val json = JSONObject(res.value.body)
                        val results = json.optJSONArray("results")
                        val list = mutableListOf<Podcast>()
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val feedUrl = item.optString("feedUrl", "")
                                if (feedUrl.isNotEmpty()) {
                                    list.add(
                                        Podcast(
                                            feedUrl = feedUrl,
                                            title = item.optString("collectionName", "Untitled"),
                                            author = item.optString("artistName", ""),
                                            artworkUrl = item.optString("artworkUrl600", item.optString("artworkUrl100", "")),
                                            itunesId = item.optLong("collectionId", 0)
                                        )
                                    )
                                }
                            }
                        }
                        AppExecutors.main { onResult(Result.Ok(list)) }
                    }
                    is Result.Err -> {
                        AppExecutors.main { onResult(Result.Err(res.message, res.code)) }
                    }
                }
            } catch (e: Exception) {
                Logger.e("iTunesSearch", "Error searching podcasts", e)
                AppExecutors.main { onResult(Result.Err("Search failed: ${e.message}", ErrorCode.NETWORK, e)) }
            }
        }
    }
}
