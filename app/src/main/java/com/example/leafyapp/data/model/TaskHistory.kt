package com.example.leafyapp.data.model

import java.io.Serializable

data class TaskHistory(
    var id: String = "",        // Thay cho historyId (Long) -> Document ID của Firebase
    var taskId: String = "",    // Thay cho taskId (Long) -> ID của CareTask (String)
    val completedDate: Long = 0L // Giữ nguyên
) : Serializable {
    // Constructor rỗng bắt buộc cho Firebase
    constructor() : this("", "", 0L)
}