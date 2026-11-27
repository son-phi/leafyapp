package com.example.leafyapp.api

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    @Multipart
    @POST("/predict")
    suspend fun predictPlant(
        @Part image: MultipartBody.Part
    ): PredictionResponse

    /** Nhận diện loài cây (8.1): trả về top_k loài kèm độ tin cậy (0–1). */
    @Multipart
    @POST("/predict")
    suspend fun identifySpecies(
        @Part image: MultipartBody.Part,
        @Query("top_k") topK: Int = 3
    ): PredictionResponse

    @Multipart
    @POST("/predict_disease")
    suspend fun predictDisease(
        @Part image: MultipartBody.Part
    ): PredictionResponse

    /** Chẩn đoán bệnh trên lá (8.4) – đọc đúng cấu trúc {result:{disease_id, confidence, severity}}. */
    @Multipart
    @POST("/predict_disease")
    suspend fun diagnoseLeaf(
        @Part image: MultipartBody.Part
    ): DiseaseResponse

    /** Chẩn đoán từ màn hình chính (8.2): một lần gọi trả loài + bệnh. */
    @Multipart
    @POST("/analyze")
    suspend fun analyze(
        @Part image: MultipartBody.Part,
        @Query("top_k") topK: Int = 3
    ): AnalyzeResponse
}
