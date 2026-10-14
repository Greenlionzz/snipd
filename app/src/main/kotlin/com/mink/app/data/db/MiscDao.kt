package com.mink.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mink.app.data.model.*

class MiscDao(private val dbHelper: AppDb) {
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    // --- Snips ---
    fun insertSnip(snip: Snip): Long {
        val cv = ContentValues().apply {
            put("episode_id", snip.episodeId)
            put("start_ms", snip.startMs)
            put("end_ms", snip.endMs)
            put("title", snip.title)
            put("transcript_text", snip.transcriptText)
            put("summary_text", snip.summaryText)
            put("user_note", snip.userNote)
            put("tags", snip.tags)
            put("created_at", snip.createdAt)
        }
        return db.insert("snip", null, cv)
    }

    fun updateSnipNote(id: Long, note: String, title: String) {
        val cv = ContentValues().apply {
            put("user_note", note)
            put("title", title)
        }
        db.update("snip", cv, "id = ?", arrayOf(id.toString()))
    }

    fun getAllSnips(): List<Snip> {
        val list = mutableListOf<Snip>()
        val sql = """
            SELECT s.*, e.title as ep_title, p.title as p_title
            FROM snip s
            JOIN episode e ON s.episode_id = e.id
            JOIN podcast p ON e.podcast_id = p.id
            ORDER BY s.created_at DESC
        """.trimIndent()
        val cursor = db.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Snip(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        episodeId = it.getLong(it.getColumnIndexOrThrow("episode_id")),
                        startMs = it.getLong(it.getColumnIndexOrThrow("start_ms")),
                        endMs = it.getLong(it.getColumnIndexOrThrow("end_ms")),
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        transcriptText = it.getString(it.getColumnIndexOrThrow("transcript_text")) ?: "",
                        summaryText = it.getString(it.getColumnIndexOrThrow("summary_text")) ?: "",
                        userNote = it.getString(it.getColumnIndexOrThrow("user_note")) ?: "",
                        tags = it.getString(it.getColumnIndexOrThrow("tags")) ?: "",
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        episodeTitle = it.getString(it.getColumnIndexOrThrow("ep_title")) ?: "",
                        podcastTitle = it.getString(it.getColumnIndexOrThrow("p_title")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun deleteSnip(id: Long) {
        db.delete("snip", "id = ?", arrayOf(id.toString()))
    }

    // --- Folders ---
    fun getAllFolders(): List<Folder> {
        val list = mutableListOf<Folder>()
        val cursor = db.rawQuery("SELECT * FROM folder ORDER BY sort ASC, name ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(Folder(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    sort = it.getInt(it.getColumnIndexOrThrow("sort"))
                ))
            }
        }
        return list
    }

    fun insertFolder(name: String): Long {
        val cv = ContentValues().apply {
            put("name", name)
        }
        return db.insert("folder", null, cv)
    }

    fun deleteFolder(id: Long) {
        db.delete("folder", "id = ?", arrayOf(id.toString()))
    }

    // --- Queue ---
    fun getQueue(): List<QueueItem> {
        val list = mutableListOf<QueueItem>()
        val sql = """
            SELECT q.id as q_id, q.sort, e.*, p.title as podcast_title, p.artwork_url as p_art
            FROM queue_item q
            JOIN episode e ON q.episode_id = e.id
            JOIN podcast p ON e.podcast_id = p.id
            ORDER BY q.sort ASC, q.id ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                val ep = Episode(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    podcastId = it.getLong(it.getColumnIndexOrThrow("podcast_id")),
                    guid = it.getString(it.getColumnIndexOrThrow("guid")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    descriptionHtml = it.getString(it.getColumnIndexOrThrow("description_html")) ?: "",
                    audioUrl = it.getString(it.getColumnIndexOrThrow("audio_url")),
                    durationSec = it.getLong(it.getColumnIndexOrThrow("duration_sec")),
                    publishedAt = it.getLong(it.getColumnIndexOrThrow("published_at")),
                    artworkUrl = it.getString(it.getColumnIndexOrThrow("artwork_url")) ?: "",
                    positionSec = it.getLong(it.getColumnIndexOrThrow("position_sec")),
                    played = it.getInt(it.getColumnIndexOrThrow("played")) == 1,
                    downloadedPath = it.getString(it.getColumnIndexOrThrow("downloaded_path"))
                )
                val pod = Podcast(
                    id = ep.podcastId,
                    feedUrl = "",
                    title = it.getString(it.getColumnIndexOrThrow("podcast_title")) ?: "",
                    artworkUrl = it.getString(it.getColumnIndexOrThrow("p_art")) ?: ""
                )
                list.add(QueueItem(
                    id = it.getLong(it.getColumnIndexOrThrow("q_id")),
                    episodeId = ep.id,
                    sort = it.getInt(it.getColumnIndexOrThrow("sort")),
                    episode = ep,
                    podcast = pod
                ))
            }
        }
        return list
    }

    fun addToQueue(episodeId: Long) {
        val cv = ContentValues().apply {
            put("episode_id", episodeId)
            put("sort", System.currentTimeMillis().toInt())
        }
        db.insertWithOnConflict("queue_item", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeFromQueue(episodeId: Long) {
        db.delete("queue_item", "episode_id = ?", arrayOf(episodeId.toString()))
    }

    // --- AI Usage ---
    fun recordAiUsage(usage: AiUsage) {
        val cv = ContentValues().apply {
            put("provider", usage.provider)
            put("model", usage.model)
            put("feature", usage.feature)
            put("input_tokens", usage.inputTokens)
            put("output_tokens", usage.outputTokens)
            put("at", usage.at)
        }
        db.insert("ai_usage", null, cv)
    }

    fun getAiUsageTotal(): Pair<Int, Int> {
        val cursor = db.rawQuery("SELECT SUM(input_tokens), SUM(output_tokens) FROM ai_usage", null)
        return cursor.use {
            if (it.moveToFirst()) {
                Pair(it.getInt(0), it.getInt(1))
            } else {
                Pair(0, 0)
            }
        }
    }

    // --- Episode AI ---
    fun getEpisodeAi(episodeId: Long): EpisodeAi? {
        val cursor = db.rawQuery("SELECT * FROM episode_ai WHERE episode_id = ?", arrayOf(episodeId.toString()))
        return cursor.use {
            if (it.moveToFirst()) {
                EpisodeAi(
                    episodeId = it.getLong(it.getColumnIndexOrThrow("episode_id")),
                    transcriptJson = it.getString(it.getColumnIndexOrThrow("transcript_json")) ?: "",
                    transcriptMode = it.getString(it.getColumnIndexOrThrow("transcript_mode")) ?: "notes",
                    chaptersJson = it.getString(it.getColumnIndexOrThrow("chapters_json")) ?: "",
                    summaryJson = it.getString(it.getColumnIndexOrThrow("summary_json")) ?: "",
                    guestsJson = it.getString(it.getColumnIndexOrThrow("guests_json")) ?: "",
                    model = it.getString(it.getColumnIndexOrThrow("model")) ?: "",
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                )
            } else null
        }
    }

    fun saveEpisodeAi(ai: EpisodeAi) {
        val cv = ContentValues().apply {
            put("episode_id", ai.episodeId)
            put("transcript_json", ai.transcriptJson)
            put("transcript_mode", ai.transcriptMode)
            put("chapters_json", ai.chaptersJson)
            put("summary_json", ai.summaryJson)
            put("guests_json", ai.guestsJson)
            put("model", ai.model)
            put("created_at", ai.createdAt)
        }
        db.insertWithOnConflict("episode_ai", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
