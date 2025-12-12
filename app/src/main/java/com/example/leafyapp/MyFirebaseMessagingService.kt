package com.example.leafyapp // Đổi package cho đúng

import android.util.Log
import com.example.leafyapp.ui.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Chỉ xử lý nếu có Data
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }

        // (Nếu có notification payload thì Android tự hiện, nhưng ở đây mình dùng data-only)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return
        val ownerId = data["ownerId"] ?: ""
        val taskId = data["taskId"] ?: ""
        val title = data["taskTitle"] ?: "Nhắc nhở"
        val body = data["taskBody"] ?: ""

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // --- LOGIC PHÂN QUYỀN (CHÌA KHÓA CỦA BẠN) ---

        var shouldShow = false

        if (type == "due_now") {
            // Case 1: Đúng giờ -> Chỉ hiện nếu MÌNH LÀ CHỦ TASK
            if (currentUid == ownerId) {
                shouldShow = true
            }
        } else if (type == "escalate") {
            // Case 2: Trễ 30p -> Chỉ hiện nếu MÌNH KHÔNG PHẢI CHỦ (Vợ/Thành viên khác)
            if (currentUid != ownerId) {
                shouldShow = true
            }
        }

        // --- HIỆN THÔNG BÁO ---
        if (shouldShow) {
            val helper = NotificationHelper(this)
            helper.createNotificationChannel()
            // Dùng hashCode của taskId làm ID để update lại thông báo cũ nếu cần
            helper.showNotification(taskId.hashCode(), title, body)
        }
    }
}