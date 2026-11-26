package com.example.leafyapp

import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.DiseaseInfo
import com.example.leafyapp.data.model.LevelPlan
import com.example.leafyapp.data.model.PlanAdjustment
import com.example.leafyapp.data.model.PlanTask
import com.example.leafyapp.data.model.DiseaseOverride
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.model.lines2
import com.example.leafyapp.data.repository.TreatmentPlanBuilder
import org.junit.Assert.assertNull
import com.example.leafyapp.data.model.TaskType
import com.example.leafyapp.data.model.Threshold
import com.example.leafyapp.data.repository.GardenRepository
import com.example.leafyapp.data.repository.IdentificationRepository
import com.example.leafyapp.data.repository.SpeciesRepository
import com.example.leafyapp.util.ImageUtils
import com.example.leafyapp.util.ThresholdUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PlantFlowLogicTest {

    @Test
    fun careTaskType_parsesNewAndLegacyValues() {
        assertEquals(CareTaskType.WATERING, CareTaskType.parse("WATERING"))
        assertEquals(CareTaskType.WATERING, CareTaskType.parse("WATER"))
        assertEquals(CareTaskType.FERTILIZING, CareTaskType.parse("FERTILIZER"))
        assertEquals(CareTaskType.PRUNING, CareTaskType.parse("CUT"))
        assertEquals(CareTaskType.OTHER, CareTaskType.parse("MIST"))
        assertEquals(CareTaskType.OTHER, CareTaskType.parse("ROTATE"))
        assertEquals(CareTaskType.HEALTH_CHECK, CareTaskType.parse("health_check"))
        assertEquals(CareTaskType.OTHER, CareTaskType.parse(null))
    }

    @Test
    fun legacyTypeMapping_roundTripsMainTypes() {
        assertEquals(TaskType.WATER, GardenRepository.toLegacyType(CareTaskType.WATERING, "Tưới nước"))
        assertEquals(TaskType.ROTATE, GardenRepository.toLegacyType(CareTaskType.OTHER, "Xoay chậu"))
        assertEquals(CareTaskType.FERTILIZING, GardenRepository.fromLegacyType(TaskType.FERTILIZER))
    }

    @Test
    fun threshold_validation() {
        val ok = Threshold(25.0, 60.0, 18.0, 30.0, 40.0, 70.0, 1000.0, 10000.0)
        assertTrue(ThresholdUtils.validate(ok).isEmpty())
        val bad = ok.copy(soilMoistureMin = 70.0, temperatureMin = 35.0)
        assertEquals(2, ThresholdUtils.validate(bad).size)
    }

    @Test
    fun nextDue_isCompletionDatePlusInterval_atReminderTime() {
        val completed = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 26, 15, 40, 0) }.timeInMillis
        val next = Calendar.getInstance().apply { time = GardenRepository.nextDueAfter(completed, 7, "08:30") }
        assertEquals(2026, next.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, next.get(Calendar.MONTH))
        assertEquals(3, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, next.get(Calendar.MINUTE))
    }

    @Test
    fun reliableThreshold_is60Percent() {
        assertEquals(60.0, IdentificationRepository.RELIABLE_THRESHOLD, 0.0)
    }

    @Test
    fun searchNormalize_removesVietnameseAccents() {
        assertEquals("luoi ho", SpeciesRepository.normalize("Lưỡi Hổ"))
        assertEquals("dua dan", SpeciesRepository.normalize("Đưa đàn"))
    }

    @Test
    fun driveLink_isConvertedToDirectUrl() {
        assertEquals(
            "https://drive.google.com/uc?export=view&id=ABC_123",
            ImageUtils.directImageUrl("https://drive.google.com/file/d/ABC_123/view?usp=drive_link")
        )
        assertEquals("https://x.y/a.jpg", ImageUtils.directImageUrl("https://x.y/a.jpg"))
    }

    @Test
    fun modelSeverity_mapsSixLevelsToThree() {
        assertEquals(Severity.NONE, Severity.fromModelLevel("Không bệnh"))
        assertEquals(Severity.MILD, Severity.fromModelLevel("Rất nhẹ"))
        assertEquals(Severity.MILD, Severity.fromModelLevel("Nhẹ"))
        assertEquals(Severity.MODERATE, Severity.fromModelLevel("Trung bình"))
        assertEquals(Severity.SEVERE, Severity.fromModelLevel("Nặng"))
        assertEquals(Severity.SEVERE, Severity.fromModelLevel("Rất nặng"))
    }

    private val disease = DiseaseInfo(
        id = "tip_margin_necrosis", aiLabel = "tip_margin_necrosis", nameVi = "Cháy mép", causes = "• Khô",
        treatmentMild = "• Cắt mép lá khô\n• Tưới đều",
        plans = mapOf("MODERATE" to LevelPlan(
            careInfo = listOf("Xả muối"),
            tasks = listOf(PlanTask(key = "flush", title = "Xả muối"), PlanTask(key = "recheck", title = "Chụp lại")),
            adjustments = listOf(PlanAdjustment("WATERING", PlanAdjustment.ACTION_SCALE, factor = 1.5))
        ))
    )
    private val peaceLily = Species(
        id = "peace-lily", treatmentCautions = listOf("Không dùng nước máy"),
        diseaseOverrides = mapOf("tip_margin_necrosis" to DiseaseOverride(
            plans = mapOf("MODERATE" to LevelPlan(
                careInfo = listOf("Nhạy flo"),
                tasks = listOf(PlanTask(key = "flush", title = "Xả muối bằng nước lọc"), PlanTask(key = "water_source", title = "Đổi nước"))
            ))
        ))
    )

    @Test
    fun treatmentPlan_mergesSpeciesOverGenericAtSeverity() {
        val plan = TreatmentPlanBuilder.build(disease, peaceLily, Severity.MODERATE)!!
        assertEquals(listOf("Xả muối bằng nước lọc", "Chụp lại", "Đổi nước"), plan.tasks.map { it.title })
        assertEquals(listOf("Nhạy flo", "Xả muối"), plan.careInfo)
        assertEquals(listOf("Không dùng nước máy"), plan.avoid)
        assertNull(TreatmentPlanBuilder.build(disease, peaceLily, Severity.NONE))
        // Mức chưa có plans (vd. bệnh do web admin tạo): dùng văn bản treatmentX + việc mặc định
        val mild = TreatmentPlanBuilder.build(disease, peaceLily, Severity.MILD)!!
        assertEquals(listOf("Cắt mép lá khô", "Tưới đều"), mild.careInfo)
        assertEquals(listOf("remove_leaves", "recheck"), mild.tasks.map { it.key })
    }

    @Test
    fun scheduleChanges_onlyTouchActiveCareSchedules() {
        val schedules = listOf(
            CareSchedule(id = "w", taskType = "WATERING", repeatIntervalDays = 6),
            CareSchedule(id = "t", taskType = "WATERING", repeatIntervalDays = 2, sourceDiagnosisId = "d1")
        )
        val changes = TreatmentPlanBuilder.proposeChanges(
            TreatmentPlanBuilder.adjustments(disease, peaceLily, Severity.MODERATE), schedules)
        assertEquals(1, changes.size)
        assertEquals(6, changes[0].beforeIntervalDays)
        assertEquals(9, changes[0].afterIntervalDays)
    }

    @Test
    fun trend_comparesWithPreviousReliableDiagnosis() {
        val prev = Diagnosis(lesionAreaPercent = 20.0, confidence = 90.0)
        assertEquals(Diagnosis.TREND_IMPROVED, TreatmentPlanBuilder.trend(Diagnosis(lesionAreaPercent = 12.0, confidence = 90.0), prev))
        assertEquals(Diagnosis.TREND_STABLE, TreatmentPlanBuilder.trend(Diagnosis(lesionAreaPercent = 23.0, confidence = 90.0), prev))
        assertEquals(Diagnosis.TREND_WORSE, TreatmentPlanBuilder.trend(Diagnosis(lesionAreaPercent = 26.0, confidence = 90.0), prev))
        assertNull(TreatmentPlanBuilder.trend(Diagnosis(lesionAreaPercent = 26.0, confidence = 40.0), prev))
        assertEquals("HEALTHY", TreatmentPlanBuilder.healthStatusOf(Diagnosis(aiLabel = "healthy")))
    }

    @Test
    fun adminCreatedSpecies_stillGetsSuggestedSchedules() {
        val s = Species(id = "x", wateringCycleDays = 5, fertilizingCycleDays = 30)
        assertEquals(listOf("WATERING", "FERTILIZING", "HEALTH_CHECK"), s.suggestedTemplates().map { it.taskType })
    }

    @Test
    fun diseaseText_isSplitIntoSteps() {
        assertEquals(listOf("a", "b"), "• a\n- b\n\n".lines2())
    }
}
