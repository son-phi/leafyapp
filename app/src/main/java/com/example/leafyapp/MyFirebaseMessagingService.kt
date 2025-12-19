package com.example.leafyapp

import android.util.Log
import com.example.leafyapp.ui.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Log để kiểm tra tin nhắn đến từ Topic nào (garden_ID hay user_UID)
        Log.d("FCM_LEAFY", "Nhận tin nhắn từ: ${remoteMessage.from}")

        // Xử lý Data-only message (Đảm bảo luôn chạy qua bộ lọc này)
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Trích xuất các trường dữ liệu từ Server JS
        val type = data["type"] ?: "GENERAL"
        val relatedUserId = data["ownerId"] ?: ""
        val screen = data["screen"] ?: ""
        val gardenId = data["gardenId"] ?: ""
        val taskId = data["taskId"] ?: ""

        // 2. Ưu tiên lấy Title/Body theo cấu trúc của Robot Scheduler hoặc Thông báo sự kiện
        val title = data["taskTitle"] ?: data["title"] ?: "Thông báo Leafy"
        val body = data["taskBody"] ?: data["body"] ?: "Bạn có cập nhật mới."

        // 3. BỘ LỌC THÔNG MINH (Smart Filter)
        var shouldShow = false
        when (type) {
            "due_now" -> {
                // Nhắc đúng giờ: Chỉ hiện cho chính chủ Task
                // (Áp dụng cho cả cây cá nhân và cây gia đình)
                if (currentUid == relatedUserId) shouldShow = true
            }
            "escalate", "NEW_TASK", "NEW_PLANT", "TASK_COMPLETED", "NEW_MEMBER" -> {
                // Các tin tức gia đình: Chỉ hiện cho những người KHÁC
                if (currentUid != relatedUserId) shouldShow = true
            }
            else -> {
                // Các thông báo chung khác
                shouldShow = true
            }
        }

        // 4. HIỂN THỊ QUA HELPER
        if (shouldShow) {
            val helper = NotificationHelper(this)
            helper.createNotificationChannel()

            // Tạo ID duy nhất để thông báo không đè lên nhau
            val notificationId = if (taskId.isNotEmpty()) taskId.hashCode() else System.currentTimeMillis().toInt()

            helper.showNotification(notificationId, title, body, screen, gardenId)
            Log.d("FCM_LEAFY", "✅ HIỂN THỊ: $type | Title: $title")
        } else {
            Log.d("FCM_LEAFY", "🚫 CHẶN: $type (Do quy tắc trùng UID: $relatedUserId)")
        }
    }
}