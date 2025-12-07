package com.example.leafyapp.data.model

data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>,
    val name: String
)

data class Main(
    val temp: Double
)

data class Weather(
    val main: String,
    val description: String,
    val icon: String // Thêm dòng này (ví dụ: "10d", "01n")
)