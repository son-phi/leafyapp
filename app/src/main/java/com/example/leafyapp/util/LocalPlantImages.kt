package com.example.leafyapp.util

import android.content.Context
import java.io.File

/** Bản sao ảnh cây trên máy (dùng khi chưa bật Storage hoặc để hiển thị nhanh, không cần mạng). */
object LocalPlantImages {
    private fun dir(context: Context) = File(context.filesDir, "plant_covers").apply { mkdirs() }

    fun file(context: Context, plantId: String) = File(dir(context), "$plantId.jpg")

    fun save(context: Context, plantId: String, source: File) {
        runCatching { source.copyTo(file(context, plantId), overwrite = true) }
    }

    fun pathIfExists(context: Context, plantId: String): String? =
        file(context, plantId).takeIf { it.exists() }?.absolutePath

    fun delete(context: Context, plantId: String) {
        runCatching { file(context, plantId).delete() }
    }
}
