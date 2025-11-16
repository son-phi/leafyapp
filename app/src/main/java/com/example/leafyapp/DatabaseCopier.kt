package com.example.leafyapp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DatabaseCopier {

    private const val DB_NAME = "leafyapp.db"

    fun copyDatabase(context: Context) {

        val dbPath: File = context.getDatabasePath(DB_NAME)

        Log.e("DB_COPY", "DB PATH = ${dbPath.absolutePath}")

        // Nếu DB đã tồn tại → KHÔNG copy
        if (dbPath.exists()) {
            Log.e("DB_COPY", "DB already exists → SKIP COPY")
            return
        }

        // Tạo thư mục /databases nếu chưa tồn tại
        dbPath.parentFile?.mkdirs()

        try {
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbPath).use { output ->
                    input.copyTo(output)
                }
            }

            Log.e("DB_COPY", "Database copied successfully!")
        } catch (e: Exception) {
            Log.e("DB_COPY", "ERROR copying DB: ${e.message}")
        }
    }
}
