package com.example.leafyapp.data.repository

import com.example.leafyapp.data.model.CareLog
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.PlantProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Hồ sơ một cây trong vườn (8.6): theo dõi realtime đúng 1 cây (plant + careSchedules + careLogs + diagnoses),
 * không gắn listener cho cả vườn để tiết kiệm lượt đọc Firestore.
 */
object PlantDetailRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: throw RepositoryError("Bạn cần đăng nhập.")
    private fun plantRef(plantId: String) = db.collection("users").document(uid).collection("plants").document(plantId)

    fun plant(plantId: String): Flow<PlantProfile?> = callbackFlow {
        val reg = plantRef(plantId).addSnapshotListener { s, e ->
            if (e != null) close(e) else trySend(s?.toObject(PlantProfile::class.java))
        }
        awaitClose { reg.remove() }
    }

    fun schedules(plantId: String): Flow<List<CareSchedule>> = callbackFlow {
        val reg = plantRef(plantId).collection("careSchedules").addSnapshotListener { s, e ->
            if (e != null) close(e) else trySend(s?.toObjects(CareSchedule::class.java).orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun logs(plantId: String): Flow<List<CareLog>> = callbackFlow {
        val reg = plantRef(plantId).collection("careLogs").orderBy("performedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e -> if (e != null) close(e) else trySend(s?.toObjects(CareLog::class.java).orEmpty()) }
        awaitClose { reg.remove() }
    }

    fun diagnoses(plantId: String): Flow<List<Diagnosis>> = callbackFlow {
        val reg = plantRef(plantId).collection("diagnoses").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e -> if (e != null) close(e) else trySend(s?.toObjects(Diagnosis::class.java).orEmpty()) }
        awaitClose { reg.remove() }
    }

    /**
     * Đánh dấu xong: ghi careLogs; hạn kế tiếp = ngày làm thực tế + chu kỳ.
     * Việc điều trị làm đủ `occurrences` lần thì kết thúc (active = false). [s.completedCount] đã được tính từ nhật ký.
     */
    suspend fun complete(plantId: String, s: CareSchedule, at: Long = System.currentTimeMillis(), note: String = "") {
        val plant = plantRef(plantId)
        val batch = db.batch()
        batch.set(plant.collection("careLogs").document(), hashMapOf(
            "plantId" to plantId, "careScheduleId" to s.id, "taskType" to s.taskType, "title" to s.title,
            "performedAt" to Timestamp(Date(at)), "note" to note))
        val update = mutableMapOf<String, Any?>(
            "nextDueAt" to Timestamp(GardenRepository.nextDueAfter(at, s.repeatIntervalDays, s.timeOfDay)))
        if (s.kind == CareSchedule.KIND_TREATMENT && s.occurrences in 1..(s.completedCount + 1)) update["active"] = false
        batch.update(plant.collection("careSchedules").document(s.id), update)
        batch.commit().await()
    }

    /** Ghi nhật ký một việc làm ngoài lịch (careScheduleId = null). */
    suspend fun addLog(plantId: String, type: CareTaskType, title: String, at: Long, note: String) {
        plantRef(plantId).collection("careLogs").document().set(hashMapOf(
            "plantId" to plantId, "careScheduleId" to null, "taskType" to type.name, "title" to title,
            "performedAt" to Timestamp(Date(at)), "note" to note)).await()
    }

    /** Thêm hoặc sửa một lịch chăm sóc định kỳ do người dùng đặt (giờ nhắc nằm trong startAt). */
    suspend fun saveSchedule(plantId: String, id: String?, type: CareTaskType, title: String, intervalDays: Int,
                             timeOfDay: String, firstDue: Date, note: String): String {
        val col = plantRef(plantId).collection("careSchedules")
        val due = Timestamp(PlantRepository.withTime(firstDue, timeOfDay))
        return if (id == null) {
            val ref = col.document()
            ref.set(hashMapOf(
                "plantId" to plantId, "taskType" to type.name, "title" to title, "startAt" to due,
                "repeatIntervalDays" to intervalDays, "nextDueAt" to due, "note" to note, "active" to true)).await()
            ref.id
        } else {
            col.document(id).update(mapOf(
                "taskType" to type.name, "title" to title, "repeatIntervalDays" to intervalDays,
                "startAt" to due, "nextDueAt" to due, "note" to note)).await()
            id
        }
    }

    suspend fun setPaused(plantId: String, s: CareSchedule, paused: Boolean) {
        plantRef(plantId).collection("careSchedules").document(s.id).update("active", !paused).await()
    }

    suspend fun deleteSchedule(plantId: String, scheduleId: String) {
        plantRef(plantId).collection("careSchedules").document(scheduleId).delete().await()
    }

    suspend fun rename(plantId: String, name: String) {
        plantRef(plantId).update("name", name).await()
    }

    /** Xóa cây và toàn bộ lịch, nhật ký, chẩn đoán của cây. */
    suspend fun deletePlant(plantId: String) {
        val plant = plantRef(plantId)
        for (sub in listOf("careSchedules", "careLogs", "diagnoses")) {
            plant.collection(sub).get().await().documents.chunked(400).forEach { chunk ->
                db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
        }
        plant.delete().await()
    }
}
