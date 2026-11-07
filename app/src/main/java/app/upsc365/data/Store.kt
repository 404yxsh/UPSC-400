package app.upsc365.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.upsc365.engine.Engine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** All user state lives in this local SQLite database. Nothing leaves the device. */
class Store(context: Context) : SQLiteOpenHelper(context.applicationContext, "upsc365.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE progress (ukey TEXT PRIMARY KEY, status INTEGER NOT NULL, done_on TEXT)")
        db.execSQL("CREATE TABLE review (ukey TEXT NOT NULL, ivl INTEGER NOT NULL, done_on TEXT NOT NULL, PRIMARY KEY (ukey, ivl))")
        db.execSQL("CREATE TABLE field (ukey TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (ukey, name))")
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema is at version 1; future migrations go here and must preserve user data.
    }

    // ------------------------------------------------------------ progress

    fun loadProgress(): Pair<Map<String, Engine.Status>, Map<String, LocalDate>> {
        val status = HashMap<String, Engine.Status>()
        val doneOn = HashMap<String, LocalDate>()
        readableDatabase.rawQuery("SELECT ukey, status, done_on FROM progress", null).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0)
                val st = Engine.Status.of(c.getInt(1))
                if (st == Engine.Status.PENDING) continue
                status[key] = st
                val on = c.getString(2)
                if (on != null) doneOn[key] = LocalDate.parse(on)
            }
        }
        return Pair(status, doneOn)
    }

    fun setProgress(key: String, status: Engine.Status, doneOn: String?) {
        val db = writableDatabase
        if (status == Engine.Status.PENDING) {
            db.delete("progress", "ukey = ?", arrayOf(key))
        } else {
            val cv = ContentValues()
            cv.put("ukey", key)
            cv.put("status", status.code)
            cv.put("done_on", doneOn)
            db.insertWithOnConflict("progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ------------------------------------------------------------ reviews

    fun loadCompletedReviews(): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT ukey, ivl FROM review", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) + "#" + c.getInt(1))
        }
        return out
    }

    fun markReview(key: String, interval: Int, on: String) {
        val cv = ContentValues()
        cv.put("ukey", key)
        cv.put("ivl", interval)
        cv.put("done_on", on)
        writableDatabase.insertWithOnConflict("review", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteReviews(key: String) {
        writableDatabase.delete("review", "ukey = ?", arrayOf(key))
    }

    // ------------------------------------------------------------ user fields (notes, references)

    /** Returns a map keyed "unitKey|fieldName". */
    fun loadFields(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT ukey, name, value FROM field", null).use { c ->
            while (c.moveToNext()) out[c.getString(0) + "|" + c.getString(1)] = c.getString(2)
        }
        return out
    }

    fun setField(key: String, name: String, value: String) {
        val db = writableDatabase
        if (value.isBlank()) {
            db.delete("field", "ukey = ? AND name = ?", arrayOf(key, name))
        } else {
            val cv = ContentValues()
            cv.put("ukey", key)
            cv.put("name", name)
            cv.put("value", value)
            db.insertWithOnConflict("field", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ------------------------------------------------------------ settings

    fun getKv(k: String): String? {
        readableDatabase.rawQuery("SELECT v FROM kv WHERE k = ?", arrayOf(k)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun setKv(k: String, v: String) {
        val cv = ContentValues()
        cv.put("k", k)
        cv.put("v", v)
        writableDatabase.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ------------------------------------------------------------ backup

    fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "upsc365")
        root.put("version", 1)
        root.put("exportedAt", LocalDateTime.now().toString())

        val progress = JSONArray()
        readableDatabase.rawQuery("SELECT ukey, status, done_on FROM progress", null).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                o.put("key", c.getString(0))
                o.put("status", c.getInt(1))
                o.put("doneOn", c.getString(2) ?: JSONObject.NULL)
                progress.put(o)
            }
        }
        root.put("progress", progress)

        val reviews = JSONArray()
        readableDatabase.rawQuery("SELECT ukey, ivl, done_on FROM review", null).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                o.put("key", c.getString(0))
                o.put("interval", c.getInt(1))
                o.put("doneOn", c.getString(2))
                reviews.put(o)
            }
        }
        root.put("reviews", reviews)

        val fields = JSONArray()
        readableDatabase.rawQuery("SELECT ukey, name, value FROM field", null).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                o.put("key", c.getString(0))
                o.put("name", c.getString(1))
                o.put("value", c.getString(2))
                fields.put(o)
            }
        }
        root.put("fields", fields)

        val kv = JSONObject()
        readableDatabase.rawQuery("SELECT k, v FROM kv", null).use { c ->
            while (c.moveToNext()) kv.put(c.getString(0), c.getString(1))
        }
        root.put("settings", kv)
        return root.toString(1)
    }

    /** Replaces all local data with the backup. Returns false (and changes nothing) if the file is not a valid backup. */
    fun importJson(text: String): Boolean {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return false
        }
        if (root.optString("app") != "upsc365") return false

        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("progress", null, null)
            db.delete("review", null, null)
            db.delete("field", null, null)
            db.delete("kv", null, null)

            val progress = root.optJSONArray("progress") ?: JSONArray()
            for (i in 0 until progress.length()) {
                val o = progress.getJSONObject(i)
                val cv = ContentValues()
                cv.put("ukey", o.getString("key"))
                cv.put("status", o.getInt("status"))
                if (o.isNull("doneOn")) cv.putNull("done_on") else cv.put("done_on", o.getString("doneOn"))
                db.insertWithOnConflict("progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val reviews = root.optJSONArray("reviews") ?: JSONArray()
            for (i in 0 until reviews.length()) {
                val o = reviews.getJSONObject(i)
                val cv = ContentValues()
                cv.put("ukey", o.getString("key"))
                cv.put("ivl", o.getInt("interval"))
                cv.put("done_on", o.getString("doneOn"))
                db.insertWithOnConflict("review", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val fields = root.optJSONArray("fields") ?: JSONArray()
            for (i in 0 until fields.length()) {
                val o = fields.getJSONObject(i)
                val cv = ContentValues()
                cv.put("ukey", o.getString("key"))
                cv.put("name", o.getString("name"))
                cv.put("value", o.getString("value"))
                db.insertWithOnConflict("field", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val settings = root.optJSONObject("settings") ?: JSONObject()
            val keys = settings.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val cv = ContentValues()
                cv.put("k", k)
                cv.put("v", settings.getString(k))
                db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }
}
