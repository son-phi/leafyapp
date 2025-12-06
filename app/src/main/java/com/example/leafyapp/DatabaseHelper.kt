package com.example.leafyapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.leafyapp.data.model.Disease
import com.example.leafyapp.data.model.Plant
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "leafyapp.db"
        private const val DATABASE_VERSION = 11 // Giữ version đã tăng

        // ... các hằng số khác của bạn ...
        private const val TABLE_PLANTS = "plants"
        private const val COL_PLANT_ID = "id"
        private const val COL_PLANT_NAME = "name"
        private const val COL_PLANT_SCI = "scientific_name"
        private const val COL_PLANT_DESC = "description"
        private const val COL_PLANT_LIGHT = "light"
        private const val COL_PLANT_WATER = "watering"
        private const val COL_PLANT_SOIL = "soil"
        private const val COL_PLANT_FERT = "fertilizer"
        private const val COL_PLANT_TEMP = "temperature"
        private const val COL_PLANT_HUM = "humidity"
        private const val COL_PLANT_IMG = "image"
    }

    private val dbPath: String = context.getDatabasePath(DATABASE_NAME).path

    init {
        Log.d("DatabaseHelper", "Init: Checking database...")
        installOrUpdateDatabase()
    }

    private fun installOrUpdateDatabase() {
        if (!checkDatabase()) {
            Log.d("DatabaseHelper", "Database does not exist. Attempting to copy from assets.")
            // Phải gọi getReadableDatabase() để Android tạo thư mục /databases
            this.readableDatabase
            this.close()
            try {
                copyDatabase()
                Log.d("DatabaseHelper", "Database copied successfully.")
            } catch (e: IOException) {
                Log.e("DatabaseHelper", "Error copying database", e)
                throw Error("Error copying database: ${e.message}")
            }
        } else {
            Log.d("DatabaseHelper", "Database already exists.")
            // Mở DB để kiểm tra version
            // Nếu version thay đổi, onUpgrade sẽ được gọi tự động
            this.readableDatabase.close()
        }
    }

    private fun checkDatabase(): Boolean {
        val dbFile = File(dbPath)
        val exists = dbFile.exists()
        Log.d("DatabaseHelper", "Checking if DB exists at $dbPath -> $exists")
        return exists
    }

    @Throws(IOException::class)
    private fun copyDatabase() {
        val inputStream: InputStream = context.assets.open(DATABASE_NAME)
        val outputStream: OutputStream = FileOutputStream(dbPath)

        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.d("DatabaseHelper", "onCreate called. This should not happen if DB is copied correctly.")
        // Để trống
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("DatabaseHelper", "onUpgrade called from version $oldVersion to $newVersion")
        if (newVersion > oldVersion) {
            val file = File(dbPath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d("DatabaseHelper", "Old database deleted: $deleted")
            }
            try {
                copyDatabase()
                Log.d("DatabaseHelper", "Database re-copied on upgrade.")
            } catch (e: IOException) {
                Log.e("DatabaseHelper", "Error copying database on upgrade", e)
            }
        }
    }

    // ... (Các hàm get và search của bạn giữ nguyên) ...
     fun getPlantById(id: Int): Plant? {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_PLANTS WHERE $COL_PLANT_ID = ?"
        db.rawQuery(query, arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return Plant(
                id = id,
                name = c.getString(c.getColumnIndexOrThrow(COL_PLANT_NAME)),
                scientificName = c.getString(c.getColumnIndexOrThrow(COL_PLANT_SCI)),
                description = c.getString(c.getColumnIndexOrThrow(COL_PLANT_DESC)),
                light = c.getString(c.getColumnIndexOrThrow(COL_PLANT_LIGHT)),
                watering = c.getString(c.getColumnIndexOrThrow(COL_PLANT_WATER)),
                soil = c.getString(c.getColumnIndexOrThrow(COL_PLANT_SOIL)),
                fertilizer = c.getString(c.getColumnIndexOrThrow(COL_PLANT_FERT)),
                temperature = c.getString(c.getColumnIndexOrThrow(COL_PLANT_TEMP)),
                humidity = c.getString(c.getColumnIndexOrThrow(COL_PLANT_HUM)),
                image = c.getString(c.getColumnIndexOrThrow(COL_PLANT_IMG))
            )
        }
    }

    fun getDiseaseById(id: Int): Disease? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM diseases WHERE id = ?", arrayOf(id.toString()))
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val name = c.getString(c.getColumnIndexOrThrow("disease"))
            return Disease(id, name, listOf(), listOf(), listOf())
        }
    }

    fun searchPlants(query: String): List<Plant> {
        val db = readableDatabase
        val resultList = mutableListOf<Plant>()
        val sqlQuery = "SELECT * FROM $TABLE_PLANTS WHERE LOWER($COL_PLANT_NAME) LIKE LOWER(?) OR LOWER($COL_PLANT_SCI) LIKE LOWER(?)"
        val searchPattern = "%$query%"
        db.rawQuery(sqlQuery, arrayOf(searchPattern, searchPattern)).use { c ->
            while (c.moveToNext()) {
                resultList.add(Plant(c.getInt(c.getColumnIndexOrThrow(COL_PLANT_ID)), c.getString(c.getColumnIndexOrThrow(COL_PLANT_NAME)), "", "", "", "", "", "", "", "", ""))
            }
        }
        return resultList
    }
}