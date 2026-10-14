package com.mink.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mink.app.data.model.Episode

class EpisodeDao(private val dbHelper: AppDb) {
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    fun insertOrUpdate(ep: Episode): Long {
        val cv = ContentValues().apply {
            put("podcast_id", ep.podcastId)
            put("guid", ep.guid)
            put("title", ep.title)
            put("description_html", ep.descriptionHtml)
            put("audio_url", ep.audioUrl)
            put("duration_sec", ep.durationSec)
            put("published_at", ep.publishedAt)
            put("artwork_url", ep.artworkUrl)
            put("position_sec", ep.positionSec)
            put("played", if (ep.played) 1 else 0)
            put("downloaded_path", ep.downloadedPath)
        }
        return db.insertWithOnConflict("episode", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertAll(episodes: List<Episode>) {
        db.beginTransaction()
        try {
            for (ep in episodes) {
                val cv = ContentValues().apply {
                    put("podcast_id", ep.podcastId)
                    put("guid", ep.guid)
                    put("title", ep.title)
                    put("description_html", ep.descriptionHtml)
                    put("audio_url", ep.audioUrl)
                    put("duration_sec", ep.durationSec)
                    put("published_at", ep.publishedAt)
                    put("artwork_url", ep.artworkUrl)
                }
                // Ignore conflicts so we don't overwrite playback position if already stored
                db.insertWithOnConflict("episode", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updatePosition(id: Long, positionSec: Long, played: Boolean) {
        val cv = ContentValues().apply {
            put("position_sec", positionSec)
            put("played", if (played) 1 else 0)
        }
        db.update("episode", cv, "id = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): Episode? {
        val cursor = db.rawQuery("SELECT * FROM episode WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) cursorToEpisode(it) else null }
    }

    fun getByPodcastId(podcastId: Long): List<Episode> {
        val list = mutableListOf<Episode>()
        val cursor = db.rawQuery("SELECT * FROM episode WHERE podcast_id = ? ORDER BY published_at DESC", arrayOf(podcastId.toString()))
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEpisode(it))
            }
        }
        return list
    }

    private fun cursorToEpisode(c: Cursor) = Episode(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        podcastId = c.getLong(c.getColumnIndexOrThrow("podcast_id")),
        guid = c.getString(c.getColumnIndexOrThrow("guid")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        descriptionHtml = c.getString(c.getColumnIndexOrThrow("description_html")) ?: "",
        audioUrl = c.getString(c.getColumnIndexOrThrow("audio_url")),
        durationSec = c.getLong(c.getColumnIndexOrThrow("duration_sec")),
        publishedAt = c.getLong(c.getColumnIndexOrThrow("published_at")),
        artworkUrl = c.getString(c.getColumnIndexOrThrow("artwork_url")) ?: "",
        positionSec = c.getLong(c.getColumnIndexOrThrow("position_sec")),
        played = c.getInt(c.getColumnIndexOrThrow("played")) == 1,
        downloadedPath = c.getString(c.getColumnIndexOrThrow("downloaded_path"))
    )
}
