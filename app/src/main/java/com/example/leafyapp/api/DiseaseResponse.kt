package com.example.leafyapp.api

import com.google.gson.annotations.SerializedName

/** Phản hồi của POST /predict_disease: {success, filename, result{...}}. */
data class DiseaseResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("result") val result: DiseaseResult? = null
)

/** Kết quả chẩn đoán lá (houseplant_api/disease_pipeline._result). */
data class DiseaseResult(
    /** Tên tiếng Việt do server đặt; app dùng tên trong Firestore `diseases`. */
    @SerializedName("disease") val disease: String? = null,
    /** = aiLabel: healthy | leaf_spot | … ; null khi không xác định được. */
    @SerializedName("disease_id") val diseaseId: String? = null,
    /** 0–100 */
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("severity") val severity: DiseaseSeverity? = null,
    /** Có khi ảnh kém hoặc kết quả chưa chắc chắn. */
    @SerializedName("note") val note: String? = null
)

data class DiseaseSeverity(
    @SerializedName("percent_area") val percentArea: Double = 0.0,
    /** Không bệnh | Rất nhẹ | Nhẹ | Trung bình | Nặng | Rất nặng */
    @SerializedName("level") val level: String? = null
)

/** Phản hồi POST /analyze: loài (top_k, cùng dạng /predict) + bệnh. */
data class AnalyzeResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("species") val species: PredictionResponse? = null,
    @SerializedName("disease") val disease: DiseaseResult? = null
)
