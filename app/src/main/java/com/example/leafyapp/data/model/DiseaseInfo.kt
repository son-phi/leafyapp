package com.example.leafyapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

/** Nhãn "khỏe" của model chẩn đoán – không phải bệnh nên không có tài liệu trong `diseases`. */
const val HEALTHY_AI_LABEL = "healthy"

/** QT01: độ tin cậy dưới 60% là chưa chắc chắn. */
const val RELIABLE_CONFIDENCE = 60.0

/** Một việc trong kế hoạch điều trị (mẫu trong danh mục). */
data class PlanTask(
    val key: String = "",
    val title: String = "",
    val description: String = "",
    val taskType: String = CareTaskType.OTHER.name,
    val dueInDays: Int = 0,
    /** 0 = làm một lần. */
    val repeatIntervalDays: Int = 0,
    val occurrences: Int = 1,
    val priority: String = PRIORITY_NORMAL
) : Serializable {
    companion object {
        const val PRIORITY_URGENT = "URGENT"
        const val PRIORITY_HIGH = "HIGH"
        const val PRIORITY_NORMAL = "NORMAL"
    }
}

/** Đề xuất chỉnh lịch chăm sóc hiện có: SCALE (nhân chu kỳ), PAUSE (tạm dừng `days` ngày), SET (đặt chu kỳ). */
data class PlanAdjustment(
    val taskType: String = CareTaskType.OTHER.name,
    val action: String = ACTION_SCALE,
    val factor: Double? = null,
    val days: Int? = null,
    val reason: String = ""
) : Serializable {
    companion object {
        const val ACTION_SCALE = "SCALE"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_SET = "SET"
    }
}

/** Kế hoạch ở một mức độ: thông tin chăm sóc, việc cần làm, đề xuất chỉnh lịch. */
data class LevelPlan(
    val careInfo: List<String> = emptyList(),
    val tasks: List<PlanTask> = emptyList(),
    val adjustments: List<PlanAdjustment> = emptyList()
) : Serializable

/** Tách văn bản nhiều dòng ("• bước 1\n• bước 2") thành danh sách. */
fun String?.lines2(): List<String> =
    this.orEmpty().split('\n').map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotEmpty() }

/**
 * `diseases/{aiLabel}` – trường gốc theo thiết kế (khớp web admin): nameVi, scientificName, aiLabel, imageUrl,
 * imagePath, affectedSpeciesIds, causes, symptoms, treatmentMild/Moderate/Severe, prevention (văn bản nhiều dòng).
 * Trường [mở rộng]: plans (việc cần làm + đề xuất đổi lịch theo mức), requiresPesticide, safetyNote.
 */
data class DiseaseInfo(
    @DocumentId val id: String = "",
    val aiLabel: String = "",
    val nameVi: String = "",
    val scientificName: String? = null,
    val imageUrl: String? = null,
    val imagePath: String = "",
    val affectedSpeciesIds: List<String> = emptyList(),
    val causes: String = "",
    val symptoms: String = "",
    val treatmentMild: String = "",
    val treatmentModerate: String = "",
    val treatmentSevere: String = "",
    val prevention: String? = null,
    /** Key: MILD | MODERATE | SEVERE */
    val plans: Map<String, LevelPlan> = emptyMap(),
    val requiresPesticide: Boolean = false,
    val safetyNote: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @Exclude fun causeList() = causes.lines2()
    @Exclude fun preventionList() = prevention.lines2()

    /** Hướng xử lý theo mức (văn bản của thiết kế gốc). */
    @Exclude fun treatmentFor(severity: Severity): List<String> = when (severity) {
        Severity.NONE -> emptyList()
        Severity.MILD -> treatmentMild.lines2()
        Severity.MODERATE -> treatmentModerate.lines2().ifEmpty { treatmentMild.lines2() }
        Severity.SEVERE -> treatmentSevere.lines2().ifEmpty { treatmentModerate.lines2().ifEmpty { treatmentMild.lines2() } }
    }
}

/** [mở rộng] species.diseaseOverrides[diseaseId]: phần RIÊNG của loài với một bệnh. */
data class DiseaseOverride(
    val likelyCauses: List<String> = emptyList(),
    @get:PropertyName("isNormalCondition") @set:PropertyName("isNormalCondition") var normalCondition: Boolean = false,
    val normalNote: String = "",
    val plans: Map<String, LevelPlan> = emptyMap()
) : Serializable

enum class Severity {
    NONE, MILD, MODERATE, SEVERE;

    companion object {
        /** 6 mức của model → 3 mức của app (Rất nhẹ/Nhẹ→MILD, Trung bình→MODERATE, Nặng/Rất nặng→SEVERE). */
        fun fromModelLevel(level: String?): Severity = when (level?.trim()?.lowercase()) {
            "rất nhẹ", "nhẹ" -> MILD
            "trung bình" -> MODERATE
            "nặng", "rất nặng" -> SEVERE
            else -> NONE
        }
    }
}

/** Đề xuất chỉnh một lịch chăm sóc cụ thể của cây (đã tính trước/sau). */
data class ScheduleChange(
    val scheduleId: String = "",
    val taskType: String = "",
    val title: String = "",
    val action: String = "",
    val beforeIntervalDays: Int = 0,
    val afterIntervalDays: Int = 0,
    val pauseDays: Int = 0,
    val reason: String = "",
    val applied: Boolean = false
)

/**
 * Diagnoses.treatmentSnapshot (trường gốc của thiết kế): bản sao hướng xử lý tại thời điểm chẩn đoán (QT04)
 * = phần chung của bệnh + phần riêng của loài ở đúng mức độ. Không đổi khi admin sửa danh mục.
 * Kèm các đề xuất đổi lịch người dùng đã chọn (QT06) và ghi chú của model.
 */
data class TreatmentSnapshot(
    val diseaseId: String? = null,
    val diseaseName: String = "",
    val speciesId: String? = null,
    val severity: String = Severity.NONE.name,
    val likelyCauses: List<String> = emptyList(),
    val careInfo: List<String> = emptyList(),
    val tasks: List<PlanTask> = emptyList(),
    val avoid: List<String> = emptyList(),
    val prevention: List<String> = emptyList(),
    @get:PropertyName("isNormalCondition") @set:PropertyName("isNormalCondition") var normalCondition: Boolean = false,
    val normalNote: String = "",
    val safetyNote: String = "",
    val scheduleChanges: List<ScheduleChange> = emptyList(),
    val modelNote: String = ""
)

/**
 * `users/{uid}/plants/{plantId}/diagnoses/{id}` – trường gốc theo thiết kế:
 * plantId, speciesId, diseaseId, imageUrl, confidence, severity, lesionAreaPercent, treatmentSnapshot, createdAt.
 * [mở rộng] aiLabel: phân biệt "khỏe" (healthy) với "không xác định được" khi diseaseId = null.
 * Độ tin cậy (QT01) và xu hướng (QT05) tính từ confidence / lần chẩn đoán trước, không lưu.
 */
data class Diagnosis(
    @DocumentId val id: String = "",
    val plantId: String = "",
    val speciesId: String? = null,
    val diseaseId: String? = null,
    val aiLabel: String = "",
    val imageUrl: String? = null,
    val confidence: Double = 0.0,
    val severity: String = Severity.NONE.name,
    val lesionAreaPercent: Double = 0.0,
    val treatmentSnapshot: TreatmentSnapshot? = null,
    val createdAt: Timestamp? = null
) {
    @get:Exclude val isHealthy: Boolean get() = aiLabel == HEALTHY_AI_LABEL
    /** QT01: < 60% hoặc model báo chưa chắc chắn → không đáng tin. */
    @get:Exclude val reliable: Boolean get() = confidence >= RELIABLE_CONFIDENCE && treatmentSnapshot?.modelNote.isNullOrBlank()

    companion object {
        const val TREND_IMPROVED = "IMPROVED"
        const val TREND_STABLE = "STABLE"
        const val TREND_WORSE = "WORSE"
    }
}
