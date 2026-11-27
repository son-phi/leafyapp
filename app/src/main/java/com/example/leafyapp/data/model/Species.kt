package com.example.leafyapp.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

/**
 * Ngưỡng môi trường (đúng 8 trường của thiết kế / web admin – IoT đồng bộ nguyên khối này xuống ESP32).
 * Độ ẩm đất, độ ẩm không khí: %; nhiệt độ: °C; ánh sáng: lux.
 */
data class Threshold(
    val soilMoistureMin: Double = 0.0,
    val soilMoistureMax: Double = 0.0,
    val temperatureMin: Double = 0.0,
    val temperatureMax: Double = 0.0,
    val airHumidityMin: Double = 0.0,
    val airHumidityMax: Double = 0.0,
    val lightMin: Double = 0.0,
    val lightMax: Double = 0.0
) : Serializable

/** [mở rộng] Lịch chăm sóc gợi ý của loài, dùng để điền sẵn khi thêm cây trồng. */
data class CareTemplate(
    val taskType: String = CareTaskType.OTHER.name,
    val repeatIntervalDays: Int = 7,
    val title: String = "",
    val note: String = ""
) : Serializable

data class Toxicity(
    val pets: Boolean = false,
    val humans: Boolean = false,
    val note: String = ""
) : Serializable

/** [mở rộng] Nội dung "Thông tin chăm sóc" hiển thị khi nhận diện / trong hồ sơ cây. */
data class CareGuide(
    val otherNames: List<String> = emptyList(),
    val englishName: String = "",
    val synonyms: List<String> = emptyList(),
    val family: String = "",
    val origin: String = "",
    val careLevel: String = "MEDIUM",
    val growthRate: String = "",
    val matureSize: String = "",
    val lightLabel: String = "",
    val lightDescription: String = "",
    val wateringDescription: String = "",
    val fertilizerDescription: String = "",
    /** true: loài không bón phân (vd. cây bắt mồi); fertilizingCycleDays khi đó chỉ để thỏa schema admin. */
    @get:PropertyName("noFertilizer") @set:PropertyName("noFertilizer") var noFertilizer: Boolean = false,
    val soilDescription: String = "",
    val temperatureDescription: String = "",
    val humidityDescription: String = "",
    val toxicity: Toxicity = Toxicity(),
    val propagation: String = "",
    val commonProblems: List<String> = emptyList(),
    val careNotes: List<String> = emptyList(),
    val seasonalNotes: String = ""
) : Serializable

/**
 * `species/{speciesId}` – trường gốc theo thiết kế (khớp web admin):
 * commonName, scientificName, aiLabel, imageUrl, imagePath, description, lightNeed, wateringCycleDays,
 * fertilizingCycleDays, threshold (+ searchName/searchPrefixes do admin dùng để tìm kiếm).
 * Trường [mở rộng] của app: careGuide, careTemplates, treatmentCautions, diseaseOverrides.
 */
data class Species(
    @DocumentId val id: String = "",
    val aiLabel: String = "",
    val commonName: String = "",
    val scientificName: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val imagePath: String = "",
    /** LOW | MEDIUM | HIGH | DIRECT (theo web admin) */
    val lightNeed: String = "",
    val wateringCycleDays: Int = 7,
    val fertilizingCycleDays: Int = 30,
    val threshold: Threshold = Threshold(),
    val careGuide: CareGuide = CareGuide(),
    val careTemplates: List<CareTemplate> = emptyList(),
    /** [mở rộng] Lưu ý khi xử lý bệnh cho loài này (vd. không dùng dầu neem, không làm ướt lá). */
    val treatmentCautions: List<String> = emptyList(),
    /** [mở rộng] Phần riêng của loài với từng bệnh, khóa = diseases.aiLabel (thay bảng careRules). */
    val diseaseOverrides: Map<String, DiseaseOverride> = emptyMap()
) : Serializable {
    // ----- Truy cập nhanh (không lưu Firestore) để code hiển thị không phải đi sâu vào careGuide -----
    @get:Exclude val otherNames get() = careGuide.otherNames
    @get:Exclude val englishName get() = careGuide.englishName
    @get:Exclude val synonyms get() = careGuide.synonyms
    @get:Exclude val family get() = careGuide.family
    @get:Exclude val origin get() = careGuide.origin
    @get:Exclude val careLevel get() = careGuide.careLevel
    @get:Exclude val growthRate get() = careGuide.growthRate
    @get:Exclude val matureSize get() = careGuide.matureSize
    @get:Exclude val lightNeedLabel get() = careGuide.lightLabel
    @get:Exclude val lightDescription get() = careGuide.lightDescription
    @get:Exclude val wateringDescription get() = careGuide.wateringDescription
    @get:Exclude val fertilizerDescription get() = careGuide.fertilizerDescription
    @get:Exclude val soilDescription get() = careGuide.soilDescription
    @get:Exclude val temperatureDescription get() = careGuide.temperatureDescription
    @get:Exclude val humidityDescription get() = careGuide.humidityDescription
    @get:Exclude val toxicity get() = careGuide.toxicity
    @get:Exclude val propagation get() = careGuide.propagation
    @get:Exclude val commonProblems get() = careGuide.commonProblems
    @get:Exclude val careNotes get() = careGuide.careNotes
    @get:Exclude val seasonalNotes get() = careGuide.seasonalNotes
    @get:Exclude val noFertilizer get() = careGuide.noFertilizer

    /**
     * Lịch gợi ý khi thêm cây. Loài do web admin tạo (chưa có careTemplates) → sinh từ chu kỳ tưới/bón
     * để chức năng Thêm cây trồng vẫn hoạt động.
     */
    @Exclude
    fun suggestedTemplates(): List<CareTemplate> = careTemplates.ifEmpty {
        buildList {
            add(CareTemplate(CareTaskType.WATERING.name, wateringCycleDays.coerceAtLeast(1), "Tưới nước", ""))
            if (!noFertilizer && fertilizingCycleDays in 1..180)
                add(CareTemplate(CareTaskType.FERTILIZING.name, fertilizingCycleDays, "Bón phân", ""))
            add(CareTemplate(CareTaskType.HEALTH_CHECK.name, 7, "Kiểm tra sức khỏe cây", "Chụp ảnh lá để kiểm tra sức khỏe cây."))
        }
    }
}
