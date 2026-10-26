package com.gathin.tvhome.feature.notification.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gathin.tvhome.feature.notification.data.TvNotification
import org.json.JSONObject


interface NotificationStore {
    fun loadAll(limit: Int): List<TvNotification>
    fun upsert(item: TvNotification, maxCapacity: Int)
    fun markRead(id: String)
    fun markAllRead()
    fun delete(id: String)
    fun clearAll()
}

class NotificationDatabase(
    context: Context
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION), NotificationStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                severity TEXT NOT NULL,
                action_type TEXT,
                action_params TEXT,
                image_url TEXT,
                timestamp INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_ts ON $TABLE(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN image_url TEXT")
            } catch (_: Exception) {}
        }
    }

    override fun loadAll(limit: Int): List<TvNotification> {
        val result = ArrayList<TvNotification>()
        val db = readableDatabase
        db.query(
            TABLE,
            COLUMNS,
            null,
            null,
            null,
            null,
            "timestamp DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val source = cursor.getString(1) ?: "nas"
                val title = cursor.getString(2) ?: ""
                val message = cursor.getString(3) ?: ""
                val severity = cursor.getString(4) ?: "info"
                val actionType = cursor.getString(5)?.ifEmpty { null }
                val actionParamsRaw = cursor.getString(6)
                val imageUrl = cursor.getString(7)?.ifEmpty { null }
                val timestamp = cursor.getLong(8)
                val isRead = cursor.getInt(9) != 0
                result.add(
                    TvNotification(
                        id = id,
                        source = source,
                        title = title,
                        message = message,
                        severity = severity,
                        actionType = actionType,
                        actionParams = decodeParams(actionParamsRaw),
                        timestampEpochMs = timestamp,
                        isRead = isRead,
                        imageUrl = imageUrl
                    )
                )
            }
        }
        return result
    }

    override fun upsert(item: TvNotification, maxCapacity: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", item.id)
            put("source", item.source)
            put("title", item.title)
            put("message", item.message)
            put("severity", item.severity)
            put("action_type", item.actionType)
            put("action_params", encodeParams(item.actionParams))
            put("image_url", item.imageUrl)
            put("timestamp", item.timestampEpochMs)
            put("is_read", if (item.isRead) 1 else 0)
        }
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL(
            """
            DELETE FROM $TABLE WHERE id NOT IN (
                SELECT id FROM $TABLE ORDER BY timestamp DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(maxCapacity)
        )
    }

    override fun markRead(id: String) {
        val values = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
    }

    override fun markAllRead() {
        val values = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update(TABLE, values, "is_read = 0", null)
    }

    override fun delete(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
    }

    override fun clearAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    private fun encodeParams(params: Map<String, Any?>?): String? {
        if (params.isNullOrEmpty()) return null
        val json = JSONObject()
        for ((k, v) in params) {
            json.put(k, v ?: JSONObject.NULL)
        }
        return json.toString()
    }

    private fun decodeParams(raw: String?): Map<String, Any?>? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val map = HashMap<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)
                map[key] = if (value == JSONObject.NULL) null else value
            }
            map
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val DB_NAME = "tv_notifications.db"
        private const val DB_VERSION = 2
        private const val TABLE = "notifications"
        private val COLUMNS = arrayOf(
            "id", "source", "title", "message", "severity",
            "action_type", "action_params", "image_url", "timestamp", "is_read"
        )
    }
}
