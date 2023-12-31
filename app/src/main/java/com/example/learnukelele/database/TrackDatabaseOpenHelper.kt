package com.example.learnukelele.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.learnukelele.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class TrackDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    var context = context

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "track_database.db"
        const val TABLE_NAME = "tracks"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_TYPE = "type"
        const val COLUMN_SCORE = "score"
        const val COLUMN_IMAGE = "image"
        const val COLUMN_TRACK_DATA = "track_data"
        const val COLUMN_IS_EDITABLE = "isEditable"
    }
    init {
        addDefaultTracks()
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE $TABLE_NAME (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_TITLE TEXT," +
                    "$COLUMN_AUTHOR TEXT," +
                    "$COLUMN_TYPE TEXT," +
                    "$COLUMN_SCORE REAL," +
                    "$COLUMN_IMAGE BLOB," +
                    "$COLUMN_TRACK_DATA TEXT," +
                    "$COLUMN_IS_EDITABLE TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Not implemented
    }

    fun addTrack(track: Track) {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(COLUMN_TITLE, track.title)
            put(COLUMN_AUTHOR, track.author)
            put(COLUMN_TYPE, track.type)
            put(COLUMN_SCORE, track.score)
            put(COLUMN_IMAGE, toByteArray(track.image))
            put(COLUMN_TRACK_DATA, track.trackData.toString())
            put(COLUMN_IS_EDITABLE, if(track.isEditable){"editable"}else{"notEditable"})
        }

        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun removeTrack(trackId: Int) {
        val db = writableDatabase
        db.delete(
            TABLE_NAME,
            "$COLUMN_ID = ?",
            arrayOf(trackId.toString())
        )
        db.close()
    }

    fun getTrack(id: Int): Track? {
        val db = readableDatabase

        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID, COLUMN_TITLE, COLUMN_AUTHOR, COLUMN_TYPE, COLUMN_SCORE, COLUMN_IMAGE, COLUMN_TRACK_DATA, COLUMN_IS_EDITABLE),
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        )

        val track = if (cursor.moveToFirst()) {
            Track(
                cursor.getInt(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getDouble(4),
                toBitmap(cursor.getBlob(5)),
                JSONArray(cursor.getString(6)),
                (cursor.getString(7) == "editable")
            )
        } else {
            null
        }

        cursor.close()
        db.close()

        return track
    }

    fun getAllTracks(): List<Track> {
        val tracks = mutableListOf<Track>()

        val db = readableDatabase

        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID, COLUMN_TITLE, COLUMN_AUTHOR, COLUMN_TYPE, COLUMN_SCORE, COLUMN_IMAGE, COLUMN_TRACK_DATA, COLUMN_IS_EDITABLE),
            null,
            null,
            null,
            null,
            null
        )

        while (cursor.moveToNext()) {
            val track = Track(
                cursor.getInt(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getDouble(4),
                toBitmap(cursor.getBlob(5)),
                JSONArray(cursor.getString(6)),
                (cursor.getString(7) == "editable")
            )
            tracks.add(track)
        }

        cursor.close()
        db.close()

        return tracks
    }

    // Function to get the track score given it's id
    fun getTrackScore(trackId: Int): Double {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_SCORE),
            "$COLUMN_ID = ?",
            arrayOf(trackId.toString()),
            null,
            null,
            null,
            "1"
        )

        val trackScore: Double = if (cursor.moveToFirst()) {
            cursor.getDouble(0)
        } else {
            0.0
        }

        cursor.close()
        db.close()

        return trackScore
    }

    // Function to modify track score given it's id
    fun modifyTrackScore(trackId: Int, newScore: Double) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SCORE, newScore)
        }

        db.update(
            TABLE_NAME,
            values,
            "$COLUMN_ID = ?",
            arrayOf(trackId.toString())
        )

        db.close()
    }

    // Function that resets all scores
    fun resetAllTrackScores() {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SCORE, 0.0)
        }

        db.update(
            TABLE_NAME,
            values,
            null,
            null
        )
        db.close()
    }

    private fun addDefaultTracks(){
        val preferences = context.getSharedPreferences("track_db_prefs", Context.MODE_PRIVATE)

        if (!preferences.getBoolean("initialized", false)) {
            context.applicationContext.assets.list("trackdata")?.forEach { filename ->
                val trackInString = context.applicationContext.assets.open("trackdata/$filename").bufferedReader().use { it.readText() }
                val trackInJson = JSONObject(trackInString)
                val trackHeaderInJSOn = trackInJson.getJSONObject("track-header")

                val trackTitle = trackHeaderInJSOn.getString("name")
                val trackAuthor = trackHeaderInJSOn.getString("artist")
                val trackType = trackHeaderInJSOn.getString("type")
                val trackImageBitmap: Bitmap = if(trackHeaderInJSOn.has("image-name")){
                    val imageFilename = trackHeaderInJSOn.getString("image-name")
                    getBitmapFromAsset(context,"thumbnail/$imageFilename" )
                } else {
                    BitmapFactory.decodeResource(context.resources, R.drawable.track_icon_default)
                }
                val trackData = trackInJson.getJSONArray("track-data")

                val track = Track(
                    0,
                    trackTitle,
                    trackAuthor,
                    trackType,
                    0.0,
                    trackImageBitmap,
                    trackData,
                    false
                )
                println("We added track $track")
                addTrack(track)
            }
        }

        preferences.edit().putBoolean("initialized", true).apply()
    }

    private fun toByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun toBitmap(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun getBitmapFromAsset(context: Context, assetFileName: String): Bitmap {
        return try {
            val inputStream = context.assets.open(assetFileName)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(context.resources, R.drawable.track_icon_default)
        }
    }
}

data class Track(
    val id: Int,
    val title: String,
    val author: String,
    val type: String,
    val score: Double,
    val image: Bitmap,
    val trackData: JSONArray,
    val isEditable: Boolean
)

