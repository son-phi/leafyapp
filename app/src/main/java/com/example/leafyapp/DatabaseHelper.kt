package com.example.leafyapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.Disease

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // --- 1. Constants ---
    companion object {
        private const val DATABASE_NAME = "leafyapp.db"
        private const val DATABASE_VERSION = 1

        // Tên bảng
        private const val TABLE_PLANTS = "plants"
        private const val TABLE_DISEASES = "diseases"

        // Cột bảng 'plants'
        private const val COL_PLANT_ID = "id"
        private const val COL_PLANT_NAME = "name"
        private const val COL_PLANT_SCIENTIFIC_NAME = "scientific_name"
        private const val COL_PLANT_DESCRIPTION = "description"
        private const val COL_PLANT_LIGHT = "light"
        private const val COL_PLANT_WATERING = "watering"
        private const val COL_PLANT_SOIL = "soil"
        private const val COL_PLANT_FERTILIZER = "fertilizer"
        private const val COL_PLANT_TEMPERATURE = "temperature"
        private const val COL_PLANT_HUMIDITY = "humidity"
        private const val COL_PLANT_IMAGE = "image"

        // Cột bảng 'diseases'
        private const val COL_DISEASE_ID = "id"
        private const val COL_DISEASE_PLANT_ID = "plant_id"
        private const val COL_DISEASE_PESTS = "pests"
        private const val COL_DISEASE_SOLUTIONS = "solutions"
    }

    // --- 2. onCreate/onUpgrade ---

    // Ghi chú: Các lệnh CREATE TABLE chỉ nên được thực hiện nếu bạn KHÔNG sao chép file DB
    // có sẵn từ thư mục assets. Nếu bạn sao chép, bạn có thể để hàm này trống.
    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_PLANTS_TABLE = """
            CREATE TABLE $TABLE_PLANTS (
                $COL_PLANT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PLANT_NAME TEXT,
                $COL_PLANT_SCIENTIFIC_NAME TEXT,
                $COL_PLANT_DESCRIPTION TEXT,
                $COL_PLANT_LIGHT TEXT,
                $COL_PLANT_WATERING TEXT,
                $COL_PLANT_SOIL TEXT,
                $COL_PLANT_FERTILIZER TEXT,
                $COL_PLANT_TEMPERATURE TEXT,
                $COL_PLANT_HUMIDITY TEXT,
                $COL_PLANT_IMAGE TEXT
            )
        """.trimIndent()

        val CREATE_DISEASES_TABLE = """
            CREATE TABLE $TABLE_DISEASES (
                $COL_DISEASE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DISEASE_PLANT_ID INTEGER,
                $COL_DISEASE_PESTS TEXT,
                $COL_DISEASE_SOLUTIONS TEXT,
                FOREIGN KEY($COL_DISEASE_PLANT_ID) REFERENCES $TABLE_PLANTS($COL_PLANT_ID)
            )
        """.trimIndent()

        db.execSQL(CREATE_PLANTS_TABLE)
        db.execSQL(CREATE_DISEASES_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLANTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DISEASES")
        onCreate(db)
    }

    // --- 3. Query Functions ---

    /**
     * Lấy thông tin chi tiết của một cây từ bảng 'plants' dựa trên ID.
     */
    fun getPlantById(id: Int): Plant? {
        val db = this.readableDatabase
        var plant: Plant? = null
        val selectQuery = "SELECT * FROM $TABLE_PLANTS WHERE $COL_PLANT_ID = ?"

        // Sử dụng c.use để đảm bảo Cursor được đóng
        db.rawQuery(selectQuery, arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(c.getColumnIndexOrThrow(COL_PLANT_NAME))
                val scientificName = c.getString(c.getColumnIndexOrThrow(COL_PLANT_SCIENTIFIC_NAME))
                val description = c.getString(c.getColumnIndexOrThrow(COL_PLANT_DESCRIPTION))
                val light = c.getString(c.getColumnIndexOrThrow(COL_PLANT_LIGHT))
                val watering = c.getString(c.getColumnIndexOrThrow(COL_PLANT_WATERING))
                val soil = c.getString(c.getColumnIndexOrThrow(COL_PLANT_SOIL))
                val fertilizer = c.getString(c.getColumnIndexOrThrow(COL_PLANT_FERTILIZER))
                val temperature = c.getString(c.getColumnIndexOrThrow(COL_PLANT_TEMPERATURE))
                val humidity = c.getString(c.getColumnIndexOrThrow(COL_PLANT_HUMIDITY))
                val image = c.getString(c.getColumnIndexOrThrow(COL_PLANT_IMAGE))

                plant = Plant(
                    id = id,
                    name = name,
                    scientificName = scientificName,
                    description = description,
                    light = light,
                    watering = watering,
                    soil = soil,
                    fertilizer = fertilizer,
                    temperature = temperature,
                    humidity = humidity,
                    image = image
                )
            }
        }
        return plant
    }

    /**
     * Lấy danh sách các bệnh liên quan từ bảng 'diseases' dựa trên plant_id.
     */
    fun getDiseasesByPlantId(plantId: Int): List<Disease> {
        val db = this.readableDatabase
        val diseases = mutableListOf<Disease>()
        val selectQuery = "SELECT * FROM $TABLE_DISEASES WHERE $COL_DISEASE_PLANT_ID = ?"

        // Sử dụng c.use để đảm bảo Cursor được đóng
        db.rawQuery(selectQuery, arrayOf(plantId.toString())).use { c ->
            while (c.moveToNext()) {
                val id = c.getInt(c.getColumnIndexOrThrow(COL_DISEASE_ID))
                val pests = c.getString(c.getColumnIndexOrThrow(COL_DISEASE_PESTS))
                val solutions = c.getString(c.getColumnIndexOrThrow(COL_DISEASE_SOLUTIONS))

                diseases.add(
                    Disease(
                        id = id,
                        plantId = plantId,
                        pests = pests,
                        solutions = solutions
                    )
                )
            }
        }
        return diseases
    }
}