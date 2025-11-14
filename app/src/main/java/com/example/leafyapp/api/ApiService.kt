package com.example.leafyapp.api

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/predict_plant")
    suspend fun predictPlant(
        @Part image: MultipartBody.Part
    ): PredictionResponse

    @Multipart
    @POST("/predict_disease")
    suspend fun predictDisease(
        @Part image: MultipartBody.Part
    ): PredictionResponse
}
