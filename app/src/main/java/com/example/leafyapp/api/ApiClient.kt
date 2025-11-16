package com.example.leafyapp.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "http://10.1.0.52:8000/"

    // Tạo logging cho debug API
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Tăng timeout để upload ảnh + chạy model không bị choke
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)    // thời gian kết nối
        .writeTimeout(120, TimeUnit.SECONDS)     // thời gian upload file
        .readTimeout(120, TimeUnit.SECONDS)      // chờ server trả về kết quả
        .retryOnConnectionFailure(true)
        .addInterceptor(logging)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
