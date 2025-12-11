package com.example.leafyapp.data.model

data class DiseaseLog(
    var id: String = "",
    var plantId: String = "",
    val diseaseName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)