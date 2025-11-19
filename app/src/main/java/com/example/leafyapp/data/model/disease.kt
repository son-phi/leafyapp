package com.example.leafyapp.data.model

data class Disease(
    val id: Int,
    val diseaseName: String,
    val reasons: List<String>,
    val solutions: List<String>,
    val plants: List<String>
)
