package com.example.leafyapp.data.model

import java.io.Serializable

// Class này giúp gộp dữ liệu: Lấy Task kèm theo thông tin Cây của task đó
data class TaskWithPlant(
    val task: CareTask,
    val plant: UserPlant
) : Serializable