package com.example.leafyapp.data.model

import java.io.Serializable

data class Plant(
    val id: Int = 0,
    val name: String = "",
    val scientificName: String = "",
    val description: String = "",
    val light: String = "",
    val watering: String = "",
    val soil: String = "",
    val fertilizer: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val image: String = "",
    val popularity: Int = 0 // Trường dùng để sắp xếp Trending
) : Serializable {
    // Constructor rỗng bắt buộc để Firebase convert dữ liệu
    constructor() : this(0, "", "", "", "", "", "", "", "", "", "", 0)
}