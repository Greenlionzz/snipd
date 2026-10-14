package com.mink.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mink.app.core.Logger

class AppDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Logger.i("AppDb", "Creating database tables...")

        db.execSQL("""
            CREATE TABLE podcast (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                feed_url TEXT UNIQUE NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                description TEXT,
                artwork_url TEXT,
                website TEXT,
                itunes_id INTEGER DEFAULT 0,
                subscribed INTEGER DEFAULT 0,
                last_fetched INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE episode (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                podcast_id INTEGER NOT NULL REFERENCES podcast(id) ON DELETE CASCADE,
                guid TEXT NOT NULL,
                title TEXT NOT NULL,
                description_html TEXT,
                audio_url TEXT NOT NULL,
                duration_sec INTEGER DEFAULT 0,
                published_at INTEGER DEFAULT 0,
                artwork_url TEXT,
                position_sec INTEGER DEFAULT 0,
                played INTEGER DEFAULT 0,
                downloaded_path TEXT,
                UNIQUE(podcast_id, guid)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_episode_podcast_pub ON episode(podcast_id, published_at DESC)")

        db.execSQL("""
            CREATE TABLE episode_ai (
                episode_id INTEGER PRIMARY KEY REFERENCES episode(id) ON DELETE CASCADE,
                transcript_json TEXT,
                transcript_mode TEXT DEFAULT 'notes',
                chapters_json TEXT,
                summary_json TEXT,
                guests_json TEXT,
                model TEXT,
                created_at INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE snip (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                episode_id INTEGER NOT NULL REFERENCES episode(id) ON DELETE CASCADE,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                title TEXT,
                transcript_text TEXT,
                summary_text TEXT,
                user_note TEXT,
                tags TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_snip_episode ON snip(episode_id, created_at DESC)")

        db.execSQL("""
            CREATE TABLE chat_message (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scope_type TEXT NOT NULL,
                scope_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_chat_scope ON chat_message(scope_type, scope_id, created_at ASC)")

        db.execSQL("""
            CREATE TABLE folder (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sort INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE feed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT UNIQUE NOT NULL,
                title TEXT NOT NULL,
                site_url TEXT,
                icon_url TEXT,
                folder_id INTEGER REFERENCES folder(id) ON DELETE SET NULL,
                etag TEXT,
                last_modified TEXT,
                last_fetched INTEGER DEFAULT 0,
                error_count INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE article (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                feed_id INTEGER NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
                guid TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                link TEXT,
                content_html TEXT,
                summary_text TEXT,
                image_url TEXT,
                published_at INTEGER DEFAULT 0,
                read INTEGER DEFAULT 0,
                starred INTEGER DEFAULT 0,
                ai_summary TEXT,
                UNIQUE(feed_id, guid)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_article_feed_pub ON article(feed_id, published_at DESC)")
        db.execSQL("CREATE INDEX idx_article_read_pub ON article(read, published_at DESC)")

        db.execSQL("""
            CREATE TABLE queue_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                episode_id INTEGER NOT NULL UNIQUE REFERENCES episode(id) ON DELETE CASCADE,
                sort INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE ai_usage (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider TEXT NOT NULL,
                model TEXT NOT NULL,
                feature TEXT NOT NULL,
                input_tokens INTEGER DEFAULT 0,
                output_tokens INTEGER DEFAULT 0,
                at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Incremental migrations here as DB_VERSION increases
    }

    companion object {
        const val DB_NAME = "tuned.db"
        const val DB_VERSION = 1
    }
}
