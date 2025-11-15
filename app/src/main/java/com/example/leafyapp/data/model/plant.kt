package com.example.leafyapp.data.model

data class Plant(
    val id: Int,
    val name: String,
    val scientificName: String, // scientific_name
    val description: String,
    val light: String,
    val watering: String,
    val soil: String,
    val fertilizer: String,
    val temperature: String,
    val humidity: String,
    val image: String
)