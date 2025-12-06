package com.example.leafyapp.data.model

data class Disease(
    val id: Int = 0,
    val diseaseName: String = "", // Map từ cột "disease"
    val reasons: List<String> = emptyList(), // Firestore sẽ lưu dưới dạng Array
    val solutions: List<String> = emptyList(),
    val plants: List<String> = emptyList()
)
