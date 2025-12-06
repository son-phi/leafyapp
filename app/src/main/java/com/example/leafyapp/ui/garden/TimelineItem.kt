package com.example.leafyapp.ui.garden

import com.example.leafyapp.data.model.TaskType

sealed class TimelineItem {
    abstract val dateMillis: Long

    data class Header(override val dateMillis: Long, val key: String) : TimelineItem()
    // Sự kiện 1: Ngày thêm cây vào vườn
    data class PlantAdded(
        override val dateMillis: Long,
        val plantName: String,
        val imagePath: String?
    ) : TimelineItem()

    // Sự kiện 2: Lịch sử chăm sóc (Đã hoàn thành task)
    data class CareEvent(
        override val dateMillis: Long,
        val taskType: TaskType
    ) : TimelineItem()

    // Sự kiện 3: Lịch sử bệnh
    data class DiseaseEvent(
        override val dateMillis: Long,
        val diseaseName: String
    ) : TimelineItem()
}