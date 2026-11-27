package com.example.leafyapp.data.repository

import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.DiseaseInfo
import com.example.leafyapp.data.model.LevelPlan
import com.example.leafyapp.data.model.PlanAdjustment
import com.example.leafyapp.data.model.PlanTask
import com.example.leafyapp.data.model.ScheduleChange
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.model.TreatmentSnapshot
import kotlin.math.roundToInt

/**
 * Logic thuần (không Firebase): gộp kế hoạch điều trị LOÀI × BỆNH × MỨC ĐỘ, tính đề xuất chỉnh lịch và xu hướng.
 *   phần chung  = diseases/{id}: treatmentX (văn bản thiết kế gốc) + plans[mức] (việc cần làm, đổi lịch)
 *   phần riêng  = species.treatmentCautions + species.diseaseOverrides[diseaseId].plans[mức]
 * Bệnh do web admin tạo (chưa có plans) vẫn chạy: dùng văn bản treatmentX và các việc mặc định.
 */
object TreatmentPlanBuilder {

    /** Ngưỡng QT05: diện tích vết bệnh chênh ≥ 5 điểm % mới tính là tốt lên / xấu đi. */
    const val TREND_DELTA = 5.0

    /** Việc mặc định khi bệnh chưa có plans (vd. do admin tạo mới). */
    fun defaultTasks(severity: Severity): List<PlanTask> = listOf(
        PlanTask("remove_leaves", "Cắt bỏ lá bệnh", "Dùng kéo đã lau cồn, bỏ lá bệnh vào túi kín.",
            CareTaskType.PRUNING.name, 0, 0, 1,
            if (severity == Severity.SEVERE) PlanTask.PRIORITY_URGENT else PlanTask.PRIORITY_HIGH),
        PlanTask("recheck", "Chụp lại lá để kiểm tra tiến triển", "Chẩn đoán lại cùng vị trí lá để so sánh với lần này.",
            CareTaskType.HEALTH_CHECK.name, if (severity == Severity.SEVERE) 3 else 7)
    )

    fun build(disease: DiseaseInfo?, species: Species?, severity: Severity): TreatmentSnapshot? {
        if (disease == null || severity == Severity.NONE) return null
        val generic = disease.plans[severity.name]
        val override = species?.diseaseOverrides?.get(disease.aiLabel.ifBlank { disease.id })
        val specific = override?.plans?.get(severity.name) ?: LevelPlan()

        val tasks = LinkedHashMap<String, PlanTask>()
        (generic?.tasks ?: defaultTasks(severity)).forEach { tasks[it.key] = it }
        specific.tasks.forEach { tasks[it.key] = it }          // trùng key → bản của loài thay bản chung

        val genericInfo = generic?.careInfo?.takeIf { it.isNotEmpty() } ?: disease.treatmentFor(severity)
        return TreatmentSnapshot(
            diseaseId = disease.id.ifBlank { disease.aiLabel },
            diseaseName = disease.nameVi,
            speciesId = species?.id,
            severity = severity.name,
            likelyCauses = (override?.likelyCauses.orEmpty() + disease.causeList()).distinct(),
            careInfo = (specific.careInfo + genericInfo).distinct(),
            tasks = tasks.values.toList(),
            avoid = species?.treatmentCautions.orEmpty(),
            prevention = disease.preventionList(),
            normalCondition = override?.normalCondition == true,
            normalNote = override?.normalNote.orEmpty(),
            safetyNote = disease.safetyNote
        )
    }

    fun adjustments(disease: DiseaseInfo?, species: Species?, severity: Severity): List<PlanAdjustment> {
        if (disease == null || severity == Severity.NONE) return emptyList()
        val override = species?.diseaseOverrides?.get(disease.aiLabel.ifBlank { disease.id })
        val map = LinkedHashMap<Pair<String, String>, PlanAdjustment>()
        (disease.plans[severity.name]?.adjustments.orEmpty() + override?.plans?.get(severity.name)?.adjustments.orEmpty())
            .forEach { map[it.taskType to it.action] = it }
        return map.values.toList()
    }

    /** Áp đề xuất lên các lịch chăm sóc định kỳ đang chạy của cây → danh sách trước/sau cho người dùng chọn. */
    fun proposeChanges(adjustments: List<PlanAdjustment>, schedules: List<CareSchedule>): List<ScheduleChange> {
        val care = schedules.filter { it.active && it.kind == CareSchedule.KIND_CARE }
        return adjustments.flatMap { adj ->
            care.filter { matches(it, adj.taskType) }.mapNotNull { s ->
                val before = s.repeatIntervalDays
                when (adj.action) {
                    PlanAdjustment.ACTION_SCALE -> {
                        val after = (before * (adj.factor ?: 1.0)).roundToInt().coerceAtLeast(1)
                        if (after == before) null else ScheduleChange(s.id, s.taskType, s.title, adj.action, before, after, 0, adj.reason)
                    }
                    PlanAdjustment.ACTION_SET -> {
                        val after = adj.days ?: return@mapNotNull null
                        if (after == before) null else ScheduleChange(s.id, s.taskType, s.title, adj.action, before, after, 0, adj.reason)
                    }
                    PlanAdjustment.ACTION_PAUSE ->
                        ScheduleChange(s.id, s.taskType, s.title, adj.action, before, before, adj.days ?: 0, adj.reason)
                    else -> null
                }
            }
        }
    }

    private fun matches(s: CareSchedule, taskType: String): Boolean {
        if (taskType != CareTaskType.OTHER.name) return CareTaskType.parse(s.taskType).name == taskType
        // OTHER trong đề xuất = phun sương lên lá
        return CareTaskType.parse(s.taskType) == CareTaskType.OTHER && s.title.contains("phun sương", ignoreCase = true)
    }

    /** QT05: so với lần chẩn đoán ĐÁNG TIN gần nhất trước nó. Chỉ tính khi lần này cũng đáng tin. */
    fun trend(current: Diagnosis, previousReliable: Diagnosis?): String? =
        trend(current.reliable, current.lesionAreaPercent, previousReliable)

    fun trend(reliable: Boolean, lesionAreaPercent: Double, previousReliable: Diagnosis?): String? {
        if (!reliable || previousReliable == null) return null
        val delta = lesionAreaPercent - previousReliable.lesionAreaPercent
        return when {
            delta <= -TREND_DELTA -> Diagnosis.TREND_IMPROVED
            delta >= TREND_DELTA -> Diagnosis.TREND_WORSE
            else -> Diagnosis.TREND_STABLE
        }
    }

    /** Xu hướng của mọi lần chẩn đoán (danh sách mới → cũ), tính khi hiển thị thay vì lưu. */
    fun trends(history: List<Diagnosis>): Map<String, String?> = history.mapIndexed { i, d ->
        d.id to trend(d, history.drop(i + 1).firstOrNull { it.reliable })
    }.toMap()

    fun healthStatusOf(d: Diagnosis): String = when {
        d.isHealthy -> "HEALTHY"
        else -> Severity.valueOf(d.severity).let { if (it == Severity.NONE) "UNKNOWN" else it.name }
    }
}
