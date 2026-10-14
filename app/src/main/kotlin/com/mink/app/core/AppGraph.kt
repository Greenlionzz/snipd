package com.mink.app.core

import android.content.Context
import com.mink.app.data.db.AppDb
import com.mink.app.data.db.ArticleDao
import com.mink.app.data.db.EpisodeDao
import com.mink.app.data.db.FeedDao
import com.mink.app.data.db.MiscDao
import com.mink.app.data.db.PodcastDao

class AppGraph private constructor(val context: Context) {

    val db = AppDb(context)
    val podcastDao = PodcastDao(db)
    val episodeDao = EpisodeDao(db)
    val feedDao = FeedDao(db)
    val articleDao = ArticleDao(db)
    val miscDao = MiscDao(db)

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(): AppGraph = checkNotNull(instance) { "AppGraph not initialized" }

        fun init(context: Context): AppGraph {
            return instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
        }
    }
}
