package com.example.leafyapp.data.repository

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Lỗi đã được diễn giải cho người dùng (chuỗi tiếng Việt), kèm nguyên nhân gốc để log. */
class RepositoryError(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/** Lỗi tải ảnh lên Storage: hồ sơ cây chưa được ghi, người dùng có thể thử lại hoặc lưu không kèm ảnh. */
class ImageUploadError(userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

object ErrorMapper {
    private const val TAG = "LeafyRepository"

    fun map(e: Throwable, action: String): RepositoryError {
        Log.e(TAG, "$action thất bại", e)
        val msg = when (e) {
            is RepositoryError -> return e
            is TimeoutCancellationException ->
                "Chưa nhận được xác nhận từ máy chủ. Kiểm tra kết nối mạng rồi thử lại."
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Không có quyền truy cập dữ liệu (PERMISSION_DENIED). Hãy đăng nhập lại hoặc kiểm tra Security Rules."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Không kết nối được máy chủ Firebase. Kiểm tra kết nối mạng rồi thử lại."
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
                else -> "Lỗi dữ liệu (${e.code.name}). Vui lòng thử lại."
            }
            is StorageException -> "Không tải được ảnh lên (${e.errorCode})."
            is FirebaseNetworkException -> "Không có kết nối mạng."
            is SocketTimeoutException -> "Máy chủ AI phản hồi quá lâu. Vui lòng thử lại."
            is HttpException -> "Máy chủ AI báo lỗi (${e.code()})."
            is IOException -> "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại."
            else -> e.message ?: "Đã có lỗi xảy ra."
        }
        return RepositoryError(msg, e)
    }
}
