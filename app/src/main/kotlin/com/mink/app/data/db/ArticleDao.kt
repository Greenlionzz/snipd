package com.mink.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mink.app.data.model.Article

class ArticleDao(private val dbHelper: AppDb) {
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    fun insertAll(articles: List<Article>): Int {
        var count = 0
        db.beginTransaction()
        try {
            for (art in articles) {
                val cv = ContentValues().apply {
                    put("feed_id", art.feedId)
                    put("guid", art.guid)
                    put("title", art.title)
                    put("author", art.author)
                    put("link", art.link)
                    put("content_html", art.contentHtml)
                    put("summary_text", art.summaryText)
                    put("image_url", art.imageUrl)
                    put("published_at", art.publishedAt)
                }
                val rowId = db.insertWithOnConflict("article", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (rowId != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun setRead(id: Long, read: Boolean) {
        val cv = ContentValues().apply {
            put("read", if (read) 1 else 0)
        }
        db.update("article", cv, "id = ?", arrayOf(id.toString()))
    }

    fun setAllRead(feedId: Long? = null) {
        val cv = ContentValues().apply { put("read", 1) }
        if (feedId != null) {
            db.update("article", cv, "feed_id = ?", arrayOf(feedId.toString()))
        } else {
            db.update("article", cv, null, null)
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        val cv = ContentValues().apply {
            put("starred", if (starred) 1 else 0)
        }
        db.update("article", cv, "id = ?", arrayOf(id.toString()))
    }

    fun updateAiSummary(id: Long, summary: String) {
        val cv = ContentValues().apply {
            put("ai_summary", summary)
        }
        db.update("article", cv, "id = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): Article? {
        val sql = """
            SELECT a.*, f.title as feed_title
            FROM article a
            JOIN feed f ON a.feed_id = f.id
            WHERE a.id = ?
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) cursorToArticle(it) else null }
    }

    fun getArticles(feedId: Long? = null, filterUnreadOnly: Boolean = false, filterStarredOnly: Boolean = false, limit: Int = 100): List<Article> {
        val list = mutableListOf<Article>()
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (feedId != null) {
            conditions.add("a.feed_id = ?")
            args.add(feedId.toString())
        }
        if (filterUnreadOnly) {
            conditions.add("a.read = 0")
        }
        if (filterStarredOnly) {
            conditions.add("a.starred = 1")
        }

        val whereClause = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val sql = """
            SELECT a.*, f.title as feed_title
            FROM article a
            JOIN feed f ON a.feed_id = f.id
            $whereClause
            ORDER BY a.published_at DESC
            LIMIT $limit
        """.trimIndent()

        val cursor = db.rawQuery(sql, if (args.isEmpty()) null else args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToArticle(it))
            }
        }
        return list
    }

    private fun cursorToArticle(c: Cursor) = Article(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        feedId = c.getLong(c.getColumnIndexOrThrow("feed_id")),
        guid = c.getString(c.getColumnIndexOrThrow("guid")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        author = c.getString(c.getColumnIndexOrThrow("author")) ?: "",
        link = c.getString(c.getColumnIndexOrThrow("link")) ?: "",
        contentHtml = c.getString(c.getColumnIndexOrThrow("content_html")) ?: "",
        summaryText = c.getString(c.getColumnIndexOrThrow("summary_text")) ?: "",
        imageUrl = c.getString(c.getColumnIndexOrThrow("image_url")) ?: "",
        publishedAt = c.getLong(c.getColumnIndexOrThrow("published_at")),
        read = c.getInt(c.getColumnIndexOrThrow("read")) == 1,
        starred = c.getInt(c.getColumnIndexOrThrow("starred")) == 1,
        aiSummary = c.getString(c.getColumnIndexOrThrow("ai_summary")) ?: "",
        feedTitle = c.getString(c.getColumnIndexOrThrow("feed_title")) ?: ""
    )
}
