package com.example.leafyapp.ui.garden

// Bỏ import này đi vì không cần dùng Enum nữa, chỉ dùng String tên hiển thị thôi
// import com.example.leafyapp.data.model.TaskType

sealed class TimelineItem {
    abstract val dateMillis: Long

    // Header ngày tháng
    data class Header(override val dateMillis: Long, val key: String) : TimelineItem()

    // Sự kiện 1: Ngày thêm cây
    data class PlantAdded(
        override val dateMillis: Long,
        val plantName: String,
        val imagePath: String?
    ) : TimelineItem()

    // Sự kiện 2: Lịch sử chăm sóc
    data class CareEvent(
        override val dateMillis: Long,
        // [SỬA Ở ĐÂY] Đổi từ TaskType thành String để nhận được "Tưới nước", "Bón phân"...
        val taskType: String
    ) : TimelineItem()

    // Sự kiện 3: Lịch sử bệnh
    data class DiseaseEvent(
        override val dateMillis: Long,
        val diseaseName: String
    ) : TimelineItem()
}