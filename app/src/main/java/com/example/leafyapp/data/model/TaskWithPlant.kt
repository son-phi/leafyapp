package com.example.leafyapp.data.model

import androidx.room.Embedded
import androidx.room.Relation

// Class này giúp gộp dữ liệu: Lấy Task kèm theo thông tin Cây của task đó
data class TaskWithPlant(
    @Embedded val task: CareTask,
    @Relation(
        parentColumn = "userPlantId",
        entityColumn = "id"
    )
    val plant: UserPlant
)