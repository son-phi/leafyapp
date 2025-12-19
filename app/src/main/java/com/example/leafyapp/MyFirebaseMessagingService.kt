package com.example.leafyapp

import android.util.Log
import com.example.leafyapp.ui.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Firebase Data-only message: Luôn nhảy vào đây dù App đóng hay mở
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data, remoteMessage.notification)
        }
    }

    private fun handleDataMessage(data: Map<String, String>, notification: RemoteMessage.Notification?) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Lấy loại thông báo và ID người liên quan
        val type = data["type"] ?: "GENERAL"
        val relatedUserId = data["ownerId"] ?: ""

        // 2. Trích xuất nội dung (Đồng bộ với các Key từ Server JS)
        // Dùng taskTitle/taskBody cho Scheduler, title/body cho các thông báo khác
        val title = data["taskTitle"] ?: data["title"] ?: notification?.title ?: "Thông báo Leafy"
        val body = data["taskBody"] ?: data["body"] ?: notification?.body ?: "Bạn có cập nhật mới."

        // 3. [BỔ SUNG] Trích xuất dữ liệu điều hướng
        val screen = data["screen"] ?: ""
        val gardenId = data["gardenId"] ?: ""
        val taskId = data["taskId"] ?: ""

        // 4. BỘ LỌC THÔNG MINH (Giữ nguyên logic chuẩn của bạn)
        var shouldShow = false
        when (type) {
            "due_now" -> {
                // Nhắc đúng giờ: Chỉ hiện cho chủ Task
                if (currentUid == relatedUserId) shouldShow = true
            }
            "escalate" -> {
                // Báo trễ: Chỉ hiện cho người KHÁC trong vườn để nhắc giùm
                if (currentUid != relatedUserId) shouldShow = true
            }
            "NEW_TASK", "NEW_PLANT", "TASK_COMPLETED", "NEW_MEMBER" -> {
                // Tin tức vườn: Chỉ hiện cho người KHÔNG gây ra sự kiện
                if (currentUid != relatedUserId) shouldShow = true
            }
            else -> shouldShow = true
        }

        // 5. HIỆN THÔNG BÁO KÈM DỮ LIỆU ĐIỀU HƯỚNG
        if (shouldShow) {
            val helper = NotificationHelper(this)
            helper.createNotificationChannel()

            val notificationId = if (taskId.isNotEmpty()) taskId.hashCode() else System.currentTimeMillis().toInt()

            // Truyền thêm screen và gardenId vào helper để khi click sẽ mở đúng chỗ
            helper.showNotification(notificationId, title, body, screen, gardenId)

            Log.d("FCM_LEAFY", "SUCCESS: Hiển thị $type cho user $currentUid")
        } else {
            Log.d("FCM_LEAFY", "FILTERED: Chặn thông báo $type (Trùng UID người tạo)")
        }
    }
}