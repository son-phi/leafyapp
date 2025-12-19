package com.example.leafyapp

import android.util.Log
import com.example.leafyapp.ui.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Firebase có 2 loại tin nhắn: Notification và Data.
        // Để App tự xử lý logic hiển thị (lọc người nhận), ta nên xử lý phần Data.
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data, remoteMessage.notification)
        }
    }

    private fun handleDataMessage(data: Map<String, String>, notification: RemoteMessage.Notification?) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Lấy loại thông báo
        val type = data["type"] ?: "GENERAL"

        // 2. ĐỒNG BỘ KEY: Ưu tiên lấy taskTitle/taskBody từ Server JS gửi về
        // Server JS của bạn gửi: taskTitle, taskBody cho hẹn giờ
        // Server JS của bạn gửi: notification payload cho New Task/New Plant
        val title = data["taskTitle"] ?: data["title"] ?: notification?.title ?: "Thông báo Leafy"
        val body = data["taskBody"] ?: data["body"] ?: notification?.body ?: "Bạn có cập nhật mới."

        // 3. Lấy ID liên quan (Người tạo hoặc Người được giao)
        val relatedUserId = data["ownerId"] ?: ""

        // 4. BỘ LỌC THÔNG MINH
        var shouldShow = false
        when (type) {
            "due_now" -> {
                // Robot báo đúng giờ: Chỉ hiện cho người được giao (ownerId)
                if (currentUid == relatedUserId) shouldShow = true
            }
            "escalate" -> {
                // Robot báo trễ: Chỉ hiện cho những người KHÁC trong vườn để nhắc giùm
                if (currentUid != relatedUserId) shouldShow = true
            }
            "NEW_TASK", "NEW_PLANT", "TASK_COMPLETED" -> {
                // Sự kiện từ thành viên khác: Chỉ hiện cho người KHÔNG gây ra sự kiện
                if (currentUid != relatedUserId) shouldShow = true
            }
            else -> shouldShow = true
        }

        // 5. HIỆN THÔNG BÁO
        if (shouldShow) {
            val helper = NotificationHelper(this)
            helper.createNotificationChannel()

            // Dùng taskId hoặc timestamp để các thông báo không đè lên nhau
            val taskId = data["taskId"] ?: ""
            val notificationId = if (taskId.isNotEmpty()) taskId.hashCode() else System.currentTimeMillis().toInt()

            helper.showNotification(notificationId, title, body)
            Log.d("FCM_LEAFY", "SUCCESS: Hiển thị $type - $title")
        } else {
            Log.d("FCM_LEAFY", "FILTERED: Chặn thông báo $type do quy tắc trùng UID")
        }
    }
}