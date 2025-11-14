package com.example.leafyapp.api

data class PredictionResponse(
    val id: Int,
    val label: String,
    val confidence: Float
)
