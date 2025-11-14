package com.example.leafyapp.data.model

data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>,
    val name: String
)

data class Main(
    val temp: Double // Nhiệt độ (°C)
)

data class Weather(
    val main: String, // Ví dụ: "Clouds", "Rain", "Clear"
    val description: String
)