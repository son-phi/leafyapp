package com.example.leafyapp.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // IP Wi-Fi của máy chạy server (xem dòng "App mobile ... [Wi-Fi]" khi chạy `python main.py`).
    // Emulator Android: "http://10.0.2.2:8000/"
    private const val BASE_URL = "https://blurb-cattle-henna.ngrok-free.dev/"

    // Tạo logging cho debug API
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Tăng timeout + Thêm header ngrok-skip-browser-warning để tránh trang cảnh báo của Ngrok
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)    // thời gian kết nối (sai IP / bị chặn → báo lỗi sau 10 s thay vì 60 s)
        .writeTimeout(120, TimeUnit.SECONDS)     // thời gian upload file
        .readTimeout(120, TimeUnit.SECONDS)      // chờ server trả về kết quả
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("ngrok-skip-browser-warning", "true")
                .build()
            chain.proceed(request)
        }
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
