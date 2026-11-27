package com.example.leafyapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

/** Loại công việc chăm sóc (mục 8.7). */
enum class CareTaskType {
    WATERING, FERTILIZING, PRUNING, SPRAYING, HEALTH_CHECK, OTHER;

    companion object {
        /** Đọc cả giá trị mới và enum cũ (WATER, FERTILIZER, CUT, MIST, ROTATE). */
        fun parse(raw: String?): CareTaskType = when (raw?.uppercase()) {
            "WATERING", "WATER" -> WATERING
            "FERTILIZING", "FERTILIZER" -> FERTILIZING
            "PRUNING", "CUT" -> PRUNING
            "SPRAYING" -> SPRAYING
            "HEALTH_CHECK" -> HEALTH_CHECK
            else -> OTHER
        }
    }
}

enum class HealthStatus { UNKNOWN, HEALTHY, MILD, MODERATE, SEVERE }

/**
 * Hồ sơ cây trồng `users/{uid}/plants/{plantId}` – trường gốc theo thiết kế:
 * userId, speciesId, name, imageUrl, plantedDate, useSpeciesThresholds, customThreshold, createdAt.
 * [mở rộng] healthStatus, lastCheckAt: hiển thị tình trạng trong vườn mà không đọc toàn bộ lịch sử chẩn đoán.
 */
data class PlantProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val speciesId: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val plantedDate: Timestamp? = null,
    val useSpeciesThresholds: Boolean = true,
    val customThreshold: Threshold? = null,
    val healthStatus: String = HealthStatus.UNKNOWN.name,
    val lastCheckAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) : Serializable

/**
 * Lịch chăm sóc `users/{uid}/plants/{plantId}/careSchedules/{id}` – trường gốc theo thiết kế:
 * plantId, taskType, startAt (ngày + giờ nhắc), repeatIntervalDays, note, active.
 * [mở rộng] title (tên việc), nextDueAt (hạn kế tiếp = ngày làm thực tế + chu kỳ),
 * sourceDiagnosisId (việc điều trị sinh từ lần chẩn đoán nào), occurrences (số lần của việc điều trị, 0 = lặp mãi).
 */
data class CareSchedule(
    @DocumentId val id: String = "",
    val plantId: String = "",
    val taskType: String = CareTaskType.OTHER.name,
    val title: String = "",
    val startAt: Timestamp? = null,
    val repeatIntervalDays: Int = 7,
    val nextDueAt: Timestamp? = null,
    val note: String = "",
    val active: Boolean = true,
    val sourceDiagnosisId: String? = null,
    val occurrences: Int = 0
) : Serializable {
    /** Suy ra từ nhật ký (careLogs) khi tải – không lưu. */
    @get:Exclude @set:Exclude var completedCount: Int = 0
    @get:Exclude @set:Exclude var lastDoneAt: Timestamp? = null

    @get:Exclude val kind: String get() = if (sourceDiagnosisId != null) KIND_TREATMENT else KIND_CARE
    /** Giờ nhắc lấy từ startAt. */
    @get:Exclude val timeOfDay: String get() = startAt?.toDate()?.let {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(it)
    } ?: "08:00"
    /** ACTIVE | DONE (việc điều trị đã xong hoặc bị kế hoạch mới thay) | PAUSED (lịch định kỳ tạm dừng). */
    @get:Exclude val status: String get() = when {
        active -> STATUS_ACTIVE
        kind == KIND_TREATMENT -> STATUS_DONE
        else -> STATUS_PAUSED
    }

    companion object {
        const val KIND_CARE = "CARE"
        const val KIND_TREATMENT = "TREATMENT"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_DONE = "DONE"
        const val STATUS_PAUSED = "PAUSED"
    }
}

/**
 * Nhật ký chăm sóc `users/{uid}/plants/{plantId}/careLogs/{id}` – trường gốc theo thiết kế:
 * plantId, careScheduleId (null = việc làm ngoài lịch), taskType, performedAt, note.
 * [mở rộng] title: lịch có thể bị xóa sau đó mà nhật ký vẫn đọc được tên việc.
 */
data class CareLog(
    @DocumentId val id: String = "",
    val plantId: String = "",
    val careScheduleId: String? = null,
    val taskType: String = CareTaskType.OTHER.name,
    val title: String = "",
    val performedAt: Timestamp? = null,
    val note: String = ""
) : Serializable

data class IdentificationCandidate(
    val aiLabel: String = "",
    val confidence: Double = 0.0,
    val speciesId: String? = null
) : Serializable

/**
 * Lịch sử nhận diện `users/{uid}/identifications/{id}` – đúng các trường của thiết kế:
 * userId, speciesId (loài cuối cùng – AI hoặc người dùng chọn), imageUrl, aiLabel, confidence, topCandidates, createdAt.
 */
data class Identification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val speciesId: String? = null,
    val imageUrl: String? = null,
    val aiLabel: String = "",
    val confidence: Double = 0.0,
    val topCandidates: List<IdentificationCandidate> = emptyList(),
    val createdAt: Timestamp? = null
) : Serializable {
    @get:Exclude val reliable: Boolean get() = confidence >= RELIABLE_CONFIDENCE
}
