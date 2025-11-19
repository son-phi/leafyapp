package com.example.leafyapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.leafyapp.data.model.Disease
import com.example.leafyapp.data.model.Plant

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "leafyapp.db"
        private const val DATABASE_VERSION = 1

        // TABLES
        private const val TABLE_PLANTS = "plants"
        private const val TABLE_DISEASES = "diseases"

        // PLANT COLS
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

        // DISEASE COLS
        private const val COL_D_ID = "id"
        private const val COL_D_NAME = "disease"
        private const val COL_D_REASON1 = "reason1"
        private const val COL_D_REASON2 = "reason2"
        private const val COL_D_REASON3 = "reason3"
        private const val COL_D_REASON4 = "reason4"
        private const val COL_D_SOLU1 = "solu1"
        private const val COL_D_SOLU2 = "solu2"
        private const val COL_D_SOLU3 = "solu3"
        private const val COL_D_SOLU4 = "solu4"
        private const val COL_D_CAY1 = "cay1"
        private const val COL_D_CAY2 = "cay2"
        private const val COL_D_CAY3 = "cay3"
        private const val COL_D_CAY4 = "cay4"
    }

    override fun onCreate(db: SQLiteDatabase) {}
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // -----------------------
    // GET PLANT
    // -----------------------
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

    // -----------------------
    // GET DISEASE
    // -----------------------
    fun getDiseaseById(id: Int): Disease? {
        val db = readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM diseases WHERE id = ?",
            arrayOf(id.toString())
        )

        cursor.use { c ->

            if (!c.moveToFirst()) return null

            val name = c.getString(c.getColumnIndexOrThrow("disease"))

            val reasons = listOf(
                c.getString(c.getColumnIndexOrThrow("reason1")),
                c.getString(c.getColumnIndexOrThrow("reason2")),
                c.getString(c.getColumnIndexOrThrow("reason3")),
                c.getString(c.getColumnIndexOrThrow("reason4"))
            ).filterNotNull().filter { it.isNotBlank() }

            val solutions = listOf(
                c.getString(c.getColumnIndexOrThrow("solu1")),
                c.getString(c.getColumnIndexOrThrow("solu2")),
                c.getString(c.getColumnIndexOrThrow("solu3")),
                c.getString(c.getColumnIndexOrThrow("solu4"))
            ).filterNotNull().filter { it.isNotBlank() }

            val plants = listOf(
                c.getString(c.getColumnIndexOrThrow("cay1")),
                c.getString(c.getColumnIndexOrThrow("cay2")),
                c.getString(c.getColumnIndexOrThrow("cay3")),
                c.getString(c.getColumnIndexOrThrow("cay4"))
            ).filterNotNull().filter { it.isNotBlank() }

            return Disease(
                id = id,
                diseaseName = name,
                reasons = reasons,
                solutions = solutions,
                plants = plants
            )
        }
    }

}
