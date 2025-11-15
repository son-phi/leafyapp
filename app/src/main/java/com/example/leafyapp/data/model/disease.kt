package com.example.leafyapp.data.model

data class Disease(
    val id: Int,
    val plantId: Int, //plant_id
    val pests: String, // Lưu trữ thông tin từ cột 'pests'
    val solutions: String // Lưu trữ thông tin từ cột 'solutions'
)