package com.example.leafyapp.api

import com.google.gson.annotations.SerializedName

data class PredictionResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("predictions") val predictions: List<PredictionItem>? = null,

    // Dùng dự phòng nếu server khác trả về trực tiếp id/label/confidence
    @SerializedName("id") val id: Int = -1,
    @SerializedName("label") val label: String = "",
    @SerializedName("confidence") val confidence: Float = 0f
) {
    val topPrediction: PredictionItem?
        get() = predictions?.firstOrNull()

    val displayLabel: String
        get() = topPrediction?.species ?: label

    val displayConfidence: Float
        get() = topPrediction?.confidence ?: confidence
}

data class PredictionItem(
    @SerializedName("rank") val rank: Int = 1,
    @SerializedName("species") val species: String = "",
    @SerializedName("confidence") val confidence: Float = 0f,
    @SerializedName("percent") val percent: Float = 0f
)
