package com.example.leafyapp.data.model

import java.io.Serializable

data class UserPlant(
    // Đổi id từ Long thành String để khớp với Document ID của Firebase
    // Nếu vẫn muốn giữ Room, bạn cần mapping. Ở đây mình hướng dẫn chuyển sang String cho chuẩn Cloud.
    var id: String = "",
    var userId: String = "", // QUAN TRỌNG: ID của người dùng sở hữu cây

    val plantId: Int = 0, // ID của loại cây trong database gốc (static db)
    val nickname: String = "",
    val imagePath: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
) : Serializable {
    // Firebase cần constructor rỗng
    constructor() : this("", "", 0, "", null)
}

data class CareTask(
    var id: String = "",
    var userPlantId: String = "", // String để khớp với UserPlant.id mới

    val type: TaskType = TaskType.WATER, // Enum cần xử lý riêng hoặc đổi sang String
    val frequencyDays: Int = 1,
    val timeHour: Int = 8,
    val timeMinute: Int = 0,

    val startDate: Long = System.currentTimeMillis(),
    val nextDueDate: Long = System.currentTimeMillis(),

    val isAutoReminder: Boolean = true,
    val lastCompletedDate: Long? = null
) : Serializable {
    constructor() : this("", "", TaskType.WATER, 1, 8, 0)
}

// Enum vẫn giữ nguyên, nhưng khi lưu lên Firebase nó sẽ lưu dạng String "WATER"
enum class TaskType(val displayName: String) {
    WATER("Watering"),
    MIST("Misting"),
    FERTILIZER("Fertilizing"),
    ROTATE("Rotating"),
    CUT("Cutting")
}