package com.example.leafyapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.Disease

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "leafyapp.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_PLANTS = "plants"
        private const val TABLE_DISEASES = "diseases"

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

        private const val COL_DISEASE_ID = "id"
        private const val COL_DISEASE_PLANT_ID = "plant_id"
        private const val COL_DISEASE_PESTS = "pests"
        private const val COL_DISEASE_SOLUTIONS = "solutions"
    }

    // Không tạo bảng, chỉ dùng DB đã copy
    override fun onCreate(db: SQLiteDatabase) {}

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    // ===== GET PLANT =====
    fun getPlantById(id: Int): Plant? {
        val db = readableDatabase
        var plant: Plant? = null

        val query = "SELECT * FROM $TABLE_PLANTS WHERE $COL_PLANT_ID = ?"

        db.rawQuery(query, arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) {
                plant = Plant(
                    id = id,
                    name = c.getString(c.getColumnIndexOrThrow(COL_PLANT_NAME)),
                    scientificName = c.getString(c.getColumnIndexOrThrow(COL_PLANT_SCIENTIFIC_NAME)),
                    description = c.getString(c.getColumnIndexOrThrow(COL_PLANT_DESCRIPTION)),
                    light = c.getString(c.getColumnIndexOrThrow(COL_PLANT_LIGHT)),
                    watering = c.getString(c.getColumnIndexOrThrow(COL_PLANT_WATERING)),
                    soil = c.getString(c.getColumnIndexOrThrow(COL_PLANT_SOIL)),
                    fertilizer = c.getString(c.getColumnIndexOrThrow(COL_PLANT_FERTILIZER)),
                    temperature = c.getString(c.getColumnIndexOrThrow(COL_PLANT_TEMPERATURE)),
                    humidity = c.getString(c.getColumnIndexOrThrow(COL_PLANT_HUMIDITY)),
                    image = c.getString(c.getColumnIndexOrThrow(COL_PLANT_IMAGE))
                )
            }
        }

        return plant
    }

    // ===== GET DISEASES =====

    fun getDiseasesByPlantId(plantId: Int): List<Disease> {
        val db = readableDatabase
        val list = mutableListOf<Disease>()

        val query = "SELECT * FROM $TABLE_DISEASES WHERE $COL_DISEASE_PLANT_ID = ?"

        db.rawQuery(query, arrayOf(plantId.toString())).use { c ->
            while (c.moveToNext()) {
                list.add(
                    Disease(
                        id = c.getInt(c.getColumnIndexOrThrow(COL_DISEASE_ID)),
                        plantId = plantId,
                        pests = c.getString(c.getColumnIndexOrThrow(COL_DISEASE_PESTS)),
                        solutions = c.getString(c.getColumnIndexOrThrow(COL_DISEASE_SOLUTIONS))
                    )
                )
            }
        }

        return list
    }

    // ===== DEBUG COUNT =====
    fun getPlantCount(): Int {
        val db = readableDatabase
        val c = db.rawQuery("SELECT COUNT(*) FROM $TABLE_PLANTS", null)
        c.moveToFirst()
        val count = c.getInt(0)
        c.close()
        return count
    }
}
