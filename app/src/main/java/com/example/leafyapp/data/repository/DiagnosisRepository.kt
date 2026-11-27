package com.example.leafyapp.data.repository

import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.DiseaseInfo
import com.example.leafyapp.data.model.HEALTHY_AI_LABEL
import com.example.leafyapp.data.model.PlanTask
import com.example.leafyapp.data.model.PlantProfile
import com.example.leafyapp.data.model.ScheduleChange
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.model.TreatmentSnapshot
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.Calendar
import java.util.Date

/** Kết quả /analyze: loài (null nếu lỗi ánh xạ) + bệnh. */
data class HomeAnalysis(val species: IdentifyResult?, val disease: com.example.leafyapp.api.DiseaseResult)

/** Kết quả chẩn đoán đã gộp, chờ người dùng xác nhận các việc điều trị / đổi lịch trước khi lưu. */
data class DiagnosisDraft(
    val plant: PlantProfile,
    val aiLabel: String,
    val disease: DiseaseInfo?,
    val confidence: Double,
    val modelSeverityLevel: String?,
    val severity: Severity,
    val lesionAreaPercent: Double,
    val modelVersion: String?,
    val snapshot: TreatmentSnapshot?,
    val proposedChanges: List<ScheduleChange>,
    val previousReliable: Diagnosis?,
    /** Ghi chú của model khi ảnh kém hoặc chưa chắc chắn. */
    val modelNote: String? = null
) {
    /** QT01: < 60% hoặc model tự báo chưa chắc chắn → không đáng tin. */
    val reliable: Boolean get() = confidence >= com.example.leafyapp.data.model.RELIABLE_CONFIDENCE && modelNote.isNullOrBlank()
    val isHealthy: Boolean get() = aiLabel == HEALTHY_AI_LABEL
}

/**
 * Luồng chẩn đoán cây trong vườn (8.4):
 *  1. [prepare]: đọc bệnh + phần riêng của loài, gộp kế hoạch ở đúng mức độ, tính đề xuất chỉnh lịch.
 *  2. Người dùng chọn việc điều trị và đề xuất muốn áp dụng (QT06 – không tự áp).
 *  3. [save]: 1 batch: lưu diagnosis, thay kế hoạch điều trị cũ của cây (SUPERSEDED), tạo task điều trị mới,
 *     áp dụng đổi lịch đã chọn, cập nhật healthStatus nếu kết quả đáng tin (QT02).
 */
object DiagnosisRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: throw IllegalStateException("Chưa đăng nhập")
    private val diseaseCache = mutableMapOf<String, DiseaseInfo>()

    private fun plantRef(plantId: String) = db.collection("users").document(uid).collection("plants").document(plantId)

    /** POST /predict_disease với ảnh đã nén. */
    suspend fun analyze(imageFile: java.io.File): com.example.leafyapp.api.DiseaseResult {
        val res = try {
            val body = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            com.example.leafyapp.api.ApiClient.instance.diagnoseLeaf(
                okhttp3.MultipartBody.Part.createFormData("file", imageFile.name, body))
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Gọi API /predict_disease")
        }
        return res.result ?: throw RepositoryError("Không phân tích được ảnh. Hãy chụp lại cận cảnh lá.")
    }

    /** Chẩn đoán từ màn hình chính (8.2): POST /analyze → loài (đã ánh xạ danh mục) + bệnh. */
    suspend fun analyzeHome(imageFile: java.io.File): HomeAnalysis {
        val res = try {
            val body = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            com.example.leafyapp.api.ApiClient.instance.analyze(
                okhttp3.MultipartBody.Part.createFormData("file", imageFile.name, body))
        } catch (e: Exception) {
            throw ErrorMapper.map(e, "Gọi API /analyze")
        }
        val disease = res.disease ?: throw RepositoryError("Không phân tích được ảnh. Hãy chụp lại cận cảnh lá.")
        val species = res.species?.let { runCatching { IdentificationRepository.resultFrom(it) }.getOrNull() }
        return HomeAnalysis(species, disease)
    }

    /** Kế hoạch xem trước khi chưa gắn với cây nào (chưa có lịch để đề xuất chỉnh). */
    suspend fun previewPlan(speciesId: String?, aiLabel: String, modelSeverityLevel: String?): Pair<DiseaseInfo?, TreatmentSnapshot?> {
        val disease = getDisease(aiLabel) ?: return null to null
        val severity = severityOf(aiLabel, disease, modelSeverityLevel)
        val sd = speciesId?.let { getSpecies(it) }
        return disease to TreatmentPlanBuilder.build(disease, sd, severity)
    }

    /** Cây trong vườn của người dùng (cấu trúc mới). */
    suspend fun myPlants(): List<PlantProfile> =
        db.collection("users").document(uid).collection("plants").get().await().toObjects(PlantProfile::class.java)

    fun severityOf(aiLabel: String, disease: DiseaseInfo?, modelSeverityLevel: String?): Severity =
        if (aiLabel == HEALTHY_AI_LABEL || disease == null) Severity.NONE
        else Severity.fromModelLevel(modelSeverityLevel).let { if (it == Severity.NONE) Severity.MILD else it }

    suspend fun getPlant(plantId: String): PlantProfile =
        plantRef(plantId).get().await().toObject(PlantProfile::class.java)
            ?: throw RepositoryError("Không tìm thấy hồ sơ cây.")

    suspend fun getDisease(aiLabel: String): DiseaseInfo? {
        if (aiLabel.isBlank() || aiLabel == HEALTHY_AI_LABEL) return null
        diseaseCache[aiLabel]?.let { return it }
        return db.collection("diseases").document(aiLabel).get().await()
            .toObject(DiseaseInfo::class.java)?.also { diseaseCache[aiLabel] = it }
    }

    /** Loài (kèm species.diseaseOverrides – phần riêng của loài với từng bệnh), lấy từ danh mục đã nạp sẵn. */
    suspend fun getSpecies(speciesId: String): Species? =
        if (speciesId.isBlank()) null else SpeciesRepository.getById(speciesId)

    suspend fun history(plantId: String): List<Diagnosis> =
        plantRef(plantId).collection("diagnoses").orderBy("createdAt", Query.Direction.DESCENDING).get().await()
            .toObjects(Diagnosis::class.java)

    private suspend fun schedules(plantId: String): List<CareSchedule> =
        plantRef(plantId).collection("careSchedules").get().await().toObjects(CareSchedule::class.java)

    suspend fun prepare(
        plant: PlantProfile, aiLabel: String, confidence: Double, modelSeverityLevel: String?,
        lesionAreaPercent: Double, modelVersion: String?, modelNote: String? = null
    ): DiagnosisDraft {
        val disease = getDisease(aiLabel)
        val severity = severityOf(aiLabel, disease, modelSeverityLevel)
        val sd = getSpecies(plant.speciesId)
        val snapshot = TreatmentPlanBuilder.build(disease, sd, severity)
        // Triệu chứng sinh lý bình thường của loài (vd. lá vàng sau mùa hoa của cây củ): không đề xuất đổi lịch
        val changes = if (snapshot?.normalCondition == true) emptyList()
        else TreatmentPlanBuilder.proposeChanges(TreatmentPlanBuilder.adjustments(disease, sd, severity), schedules(plant.id))
        val previous = history(plant.id).firstOrNull { it.reliable }
        return DiagnosisDraft(plant, aiLabel, disease, confidence, modelSeverityLevel, severity, lesionAreaPercent,
            modelVersion, snapshot, changes, previous, modelNote)
    }

    /** @return id của diagnosis vừa lưu. */
    suspend fun save(
        draft: DiagnosisDraft,
        selectedTaskKeys: Set<String>,
        appliedScheduleIds: Set<String>,
        imageUrl: String? = null,
        timeOfDay: String = "08:00"
    ): String {
        val plant = plantRef(draft.plant.id)
        val diagRef = plant.collection("diagnoses").document()
        val now = Timestamp.now()
        val batch = db.batch()

        // 1. Kế hoạch điều trị cũ còn dở của cây được đóng lại (lần chẩn đoán mới thay kế hoạch)
        schedules(draft.plant.id).filter { it.kind == CareSchedule.KIND_TREATMENT && it.active }.forEach {
            batch.update(plant.collection("careSchedules").document(it.id), mapOf("active" to false))
        }

        // 2. Việc điều trị người dùng đã chọn → careSchedules có sourceDiagnosisId
        draft.snapshot?.tasks.orEmpty().filter { it.key in selectedTaskKeys }.forEach { t ->
            val due = dueDate(t, timeOfDay)
            batch.set(plant.collection("careSchedules").document(), hashMapOf(
                "plantId" to draft.plant.id, "taskType" to t.taskType, "title" to t.title, "note" to t.description,
                "startAt" to due, "nextDueAt" to due, "repeatIntervalDays" to t.repeatIntervalDays.coerceAtLeast(1),
                "active" to true, "sourceDiagnosisId" to diagRef.id, "occurrences" to t.occurrences.coerceAtLeast(1)))
        }

        // 3. Đổi lịch chăm sóc đã được người dùng đồng ý (QT06). Tạm dừng = dời hạn kế tiếp tới ngày tiếp tục.
        val changes = draft.proposedChanges.map { it.copy(applied = it.scheduleId in appliedScheduleIds) }
        changes.filter { it.applied }.forEach { c ->
            val ref = plant.collection("careSchedules").document(c.scheduleId)
            if (c.action == "PAUSE") batch.update(ref, mapOf("nextDueAt" to Timestamp(addDays(Date(), c.pauseDays))))
            else batch.update(ref, mapOf("repeatIntervalDays" to c.afterIntervalDays))
        }

        // 4. Bản ghi chẩn đoán (đề xuất đổi lịch + ghi chú model nằm trong treatmentSnapshot)
        val snapshot = (draft.snapshot ?: if (draft.modelNote.isNullOrBlank()) null else TreatmentSnapshot())
            ?.copy(scheduleChanges = changes, modelNote = draft.modelNote.orEmpty())
        val diagnosis = Diagnosis(
            plantId = draft.plant.id, speciesId = draft.plant.speciesId,
            diseaseId = draft.disease?.aiLabel, aiLabel = draft.aiLabel, imageUrl = imageUrl,
            confidence = draft.confidence, severity = draft.severity.name,
            lesionAreaPercent = draft.lesionAreaPercent, treatmentSnapshot = snapshot, createdAt = now
        )
        batch.set(diagRef, diagnosis)

        // 5. Hồ sơ cây: chỉ kết quả đáng tin mới đổi tình trạng sức khỏe (QT02)
        val plantUpdate = mutableMapOf<String, Any?>("lastCheckAt" to now)
        if (diagnosis.reliable) plantUpdate["healthStatus"] = TreatmentPlanBuilder.healthStatusOf(diagnosis)
        batch.update(plant, plantUpdate)

        batch.commit().await()
        return diagRef.id
    }

    private fun dueDate(t: PlanTask, timeOfDay: String): Timestamp {
        val (h, m) = timeOfDay.split(":").map { it.toIntOrNull() ?: 0 }.let { it[0] to it.getOrElse(1) { 0 } }
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, t.dueInDays)
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // việc "hôm nay" mà giờ nhắc đã qua → nhắc sau 1 giờ thay vì báo quá hạn ngay
        if (cal.timeInMillis < System.currentTimeMillis()) cal.timeInMillis = System.currentTimeMillis() + 3_600_000L
        return Timestamp(cal.time)
    }

    private fun addDays(d: Date, days: Int): Date =
        Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, days) }.time
}
