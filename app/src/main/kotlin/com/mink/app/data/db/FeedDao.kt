package com.mink.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mink.app.data.model.Feed

class FeedDao(private val dbHelper: AppDb) {
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    fun insert(feed: Feed): Long {
        val cv = ContentValues().apply {
            put("url", feed.url)
            put("title", feed.title)
            put("site_url", feed.siteUrl)
            put("icon_url", feed.iconUrl)
            put("folder_id", feed.folderId)
            put("etag", feed.etag)
            put("last_modified", feed.lastModified)
            put("last_fetched", feed.lastFetched)
            put("error_count", feed.errorCount)
        }
        return db.insertWithOnConflict("feed", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateMetadata(id: Long, etag: String?, lastModified: String?, lastFetched: Long, errorCount: Int) {
        val cv = ContentValues().apply {
            put("etag", etag)
            put("last_modified", lastModified)
            put("last_fetched", lastFetched)
            put("error_count", errorCount)
        }
        db.update("feed", cv, "id = ?", arrayOf(id.toString()))
    }

    fun updateFolder(id: Long, folderId: Long?) {
        val cv = ContentValues().apply {
            put("folder_id", folderId)
        }
        db.update("feed", cv, "id = ?", arrayOf(id.toString()))
    }

    fun updateTitle(id: Long, title: String) {
        val cv = ContentValues().apply {
            put("title", title)
        }
        db.update("feed", cv, "id = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): Feed? {
        val cursor = db.rawQuery("SELECT * FROM feed WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) cursorToFeed(it) else null }
    }

    fun getByUrl(url: String): Feed? {
        val cursor = db.rawQuery("SELECT * FROM feed WHERE url = ?", arrayOf(url))
        return cursor.use { if (it.moveToFirst()) cursorToFeed(it) else null }
    }

    fun getAll(): List<Feed> {
        val list = mutableListOf<Feed>()
        val sql = """
            SELECT f.*, (SELECT COUNT(*) FROM article a WHERE a.feed_id = f.id AND a.read = 0) as unread_count
            FROM feed f
            ORDER BY f.title COLLATE NOCASE ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToFeedWithUnread(it))
            }
        }
        return list
    }

    fun delete(id: Long) {
        db.delete("feed", "id = ?", arrayOf(id.toString()))
    }

    private fun cursorToFeed(c: Cursor) = Feed(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        url = c.getString(c.getColumnIndexOrThrow("url")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        siteUrl = c.getString(c.getColumnIndexOrThrow("site_url")) ?: "",
        iconUrl = c.getString(c.getColumnIndexOrThrow("icon_url")) ?: "",
        folderId = if (c.isNull(c.getColumnIndexOrThrow("folder_id"))) null else c.getLong(c.getColumnIndexOrThrow("folder_id")),
        etag = c.getString(c.getColumnIndexOrThrow("etag")),
        lastModified = c.getString(c.getColumnIndexOrThrow("last_modified")),
        lastFetched = c.getLong(c.getColumnIndexOrThrow("last_fetched")),
        errorCount = c.getInt(c.getColumnIndexOrThrow("error_count")),
        unreadCount = 0
    )

    private fun cursorToFeedWithUnread(c: Cursor) = Feed(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        url = c.getString(c.getColumnIndexOrThrow("url")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        siteUrl = c.getString(c.getColumnIndexOrThrow("site_url")) ?: "",
        iconUrl = c.getString(c.getColumnIndexOrThrow("icon_url")) ?: "",
        folderId = if (c.isNull(c.getColumnIndexOrThrow("folder_id"))) null else c.getLong(c.getColumnIndexOrThrow("folder_id")),
        etag = c.getString(c.getColumnIndexOrThrow("etag")),
        lastModified = c.getString(c.getColumnIndexOrThrow("last_modified")),
        lastFetched = c.getLong(c.getColumnIndexOrThrow("last_fetched")),
        errorCount = c.getInt(c.getColumnIndexOrThrow("error_count")),
        unreadCount = c.getInt(c.getColumnIndexOrThrow("unread_count"))
    )
}
