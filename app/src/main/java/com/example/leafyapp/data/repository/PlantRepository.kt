package com.example.leafyapp.data.repository

import android.net.Uri
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.HealthStatus
import com.example.leafyapp.data.model.Species
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Calendar
import java.util.Date

/** Một lịch chăm sóc người dùng đã xác nhận trên form Thêm cây trồng. */
data class ScheduleDraft(
    val id: String,
    val taskType: CareTaskType,
    val title: String,
    val repeatIntervalDays: Int,
    val startAt: Date,
    val timeOfDay: String,
    val note: String
)

data class NewPlantRequest(
    val plantId: String,
    val species: Species,
    val name: String,
    val plantedDate: Date,
    val photo: File?,
    val uploadPhoto: Boolean,
    val schedules: List<ScheduleDraft>,
    val identificationId: String?
)

/** Hồ sơ cây trồng: users/{uid}/plants (luồng 8.3, 8.6). */
object PlantRepository {

    private const val WRITE_TIMEOUT_MS = 20_000L
    private const val UPLOAD_TIMEOUT_MS = 60_000L

    private val db get() = FirebaseFirestore.getInstance()
    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw RepositoryError("Bạn cần đăng nhập để thêm cây trồng.")

    private fun plantsRef(userId: String) = db.collection("users").document(userId).collection("plants")

    /** Sinh id trước để lần "Thử lại" ghi đè đúng tài liệu cũ, không tạo cây trùng. */
    fun newPlantId(): String = plantsRef(uid).document().id
    fun newScheduleId(plantId: String): String = plantsRef(uid).document(plantId).collection("careSchedules").document().id

    /**
     * QT08: chỉ trả về khi Storage và Firestore đã xác nhận.
     * @throws ImageUploadError khi tải ảnh lỗi (chưa ghi gì vào Firestore)
     * @throws RepositoryError khi ghi Firestore lỗi
     */
    suspend fun addPlant(request: NewPlantRequest): String {
        val userId = uid
        val plantRef = plantsRef(userId).document(request.plantId)

        val imageUrl = if (request.photo != null && request.uploadPhoto) {
            uploadCover(userId, request.plantId, request.photo)
        } else null

        // Trường theo thiết kế (Plants) + [mở rộng] healthStatus, lastCheckAt
        val plant = hashMapOf(
            "userId" to userId,
            "speciesId" to request.species.id,
            "name" to request.name.trim(),
            "imageUrl" to imageUrl,
            "plantedDate" to Timestamp(startOfDay(request.plantedDate)),
            "useSpeciesThresholds" to true,
            "customThreshold" to null,
            "healthStatus" to HealthStatus.UNKNOWN.name,
            "lastCheckAt" to null,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(plantRef, plant)
        request.schedules.forEach { s ->
            // startAt mang cả giờ nhắc; [mở rộng] title, nextDueAt
            val start = Timestamp(withTime(s.startAt, s.timeOfDay))
            batch.set(
                plantRef.collection("careSchedules").document(s.id), hashMapOf(
                    "plantId" to request.plantId,
                    "taskType" to s.taskType.name,
                    "title" to s.title,
                    "startAt" to start,
                    "repeatIntervalDays" to s.repeatIntervalDays,
                    "nextDueAt" to start,
                    "note" to s.note,
                    "active" to true
                )
            )
        }
        // Identifications.speciesId = loài cuối cùng (AI đề xuất hoặc người dùng chọn)
        request.identificationId?.let { idf ->
            batch.set(
                db.collection("users").document(userId).collection("identifications").document(idf),
                mapOf("speciesId" to request.species.id),
                SetOptions.merge()
            )
        }

        try {
            withTimeout(WRITE_TIMEOUT_MS) { batch.commit().await() }
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Ghi hồ sơ cây trồng")
        }
        return request.plantId
    }

    private suspend fun uploadCover(userId: String, plantId: String, file: File): String {
        return try {
            val ref = FirebaseStorage.getInstance().reference.child("users/$userId/plants/$plantId/cover.jpg")
            withTimeout(UPLOAD_TIMEOUT_MS) {
                ref.putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
                ref.downloadUrl.await().toString()
            }
        } catch (e: Exception) {
            val mapped = ErrorMapper.map(e, "Tải ảnh cây lên Storage")
            throw ImageUploadError(mapped.userMessage, e)
        }
    }

    private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    /** Ghép ngày bắt đầu với giờ nhắc "HH:mm". */
    fun withTime(date: Date, timeOfDay: String): Date {
        val (h, m) = timeOfDay.split(":").map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 8 } to it.getOrElse(1) { 0 } }
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }
}
