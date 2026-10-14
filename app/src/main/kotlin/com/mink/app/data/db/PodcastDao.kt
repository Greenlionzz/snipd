package com.mink.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mink.app.data.model.*

class PodcastDao(private val dbHelper: AppDb) {
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    fun insertOrUpdate(podcast: Podcast): Long {
        val cv = ContentValues().apply {
            put("feed_url", podcast.feedUrl)
            put("title", podcast.title)
            put("author", podcast.author)
            put("description", podcast.description)
            put("artwork_url", podcast.artworkUrl)
            put("website", podcast.website)
            put("itunes_id", podcast.itunesId)
            put("subscribed", if (podcast.subscribed) 1 else 0)
            put("last_fetched", podcast.lastFetched)
        }
        return db.insertWithOnConflict("podcast", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setSubscribed(id: Long, subscribed: Boolean) {
        val cv = ContentValues().apply {
            put("subscribed", if (subscribed) 1 else 0)
        }
        db.update("podcast", cv, "id = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): Podcast? {
        val cursor = db.rawQuery("SELECT * FROM podcast WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) cursorToPodcast(it) else null }
    }

    fun getByFeedUrl(url: String): Podcast? {
        val cursor = db.rawQuery("SELECT * FROM podcast WHERE feed_url = ?", arrayOf(url))
        return cursor.use { if (it.moveToFirst()) cursorToPodcast(it) else null }
    }

    fun getAllSubscribed(): List<Podcast> {
        val list = mutableListOf<Podcast>()
        val cursor = db.rawQuery("SELECT * FROM podcast WHERE subscribed = 1 ORDER BY title ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPodcast(it))
            }
        }
        return list
    }

    fun delete(id: Long) {
        db.delete("podcast", "id = ?", arrayOf(id.toString()))
    }

    private fun cursorToPodcast(c: Cursor) = Podcast(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        feedUrl = c.getString(c.getColumnIndexOrThrow("feed_url")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        author = c.getString(c.getColumnIndexOrThrow("author")) ?: "",
        description = c.getString(c.getColumnIndexOrThrow("description")) ?: "",
        artworkUrl = c.getString(c.getColumnIndexOrThrow("artwork_url")) ?: "",
        website = c.getString(c.getColumnIndexOrThrow("website")) ?: "",
        itunesId = c.getLong(c.getColumnIndexOrThrow("itunes_id")),
        subscribed = c.getInt(c.getColumnIndexOrThrow("subscribed")) == 1,
        lastFetched = c.getLong(c.getColumnIndexOrThrow("last_fetched"))
    )
}
