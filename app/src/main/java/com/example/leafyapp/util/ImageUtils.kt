package com.example.leafyapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Nén ảnh trước khi gửi API / tải lên Storage: cạnh dài tối đa 1280 px, JPEG chất lượng 85. */
object ImageUtils {

    const val MAX_EDGE = 1280
    const val JPEG_QUALITY = 85

    /** [source] là đường dẫn file hoặc chuỗi `content://`. Trả về file JPEG đã nén trong cache. */
    suspend fun compress(context: Context, source: String, maxEdge: Int = MAX_EDGE): File =
        withContext(Dispatchers.IO) {
            val open: () -> InputStream = {
                if (source.startsWith("content://") || source.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(source))
                        ?: throw IllegalStateException("Không đọc được ảnh")
                } else File(source).inputStream()
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("Ảnh không hợp lệ")

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val decoded = open().use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IllegalStateException("Ảnh không hợp lệ")

            val rotation = open().use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }

            val longEdge = max(decoded.width, decoded.height)
            val scale = if (longEdge > maxEdge) maxEdge.toFloat() / longEdge else 1f
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val output = if (scale < 1f || rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else decoded

            val file = File(context.cacheDir, "leafy_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (output !== decoded) decoded.recycle()
            output.recycle()
            file
        }

    /** Đổi link chia sẻ Google Drive sang link xem trực tiếp để Glide tải được. */
    fun directImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (!url.contains("drive.google.com")) return url
        val id = Regex("/d/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
            ?: Regex("[?&]id=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
            ?: return url
        return "https://drive.google.com/uc?export=view&id=$id"
    }

    fun percent(value: Double): Int = value.roundToInt()
}
