package com.example.leafyapp

/** Cấu hình tính năng phụ thuộc hạ tầng Firebase. */
object AppConfig {
    /**
     * Firebase Cloud Storage chỉ bật được khi project ở gói Blaze.
     * - false: không tải ảnh cây lên, `imageUrl` = null; ảnh được giữ trên máy (thư mục plant_covers)
     *   để vẫn hiển thị trong vườn.
     * - true: tải ảnh lên users/{uid}/plants/{plantId}/cover.jpg (cần deploy storage.rules).
     */
    const val STORAGE_ENABLED = false
}
