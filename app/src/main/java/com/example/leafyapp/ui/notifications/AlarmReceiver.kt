package com.example.leafyapp.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Lấy dữ liệu từ Intent (Tên cây, Loại task...)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Chăm sóc cây"
        val taskMessage = intent.getStringExtra("TASK_MESSAGE") ?: "Đến giờ chăm sóc vườn cây của bạn rồi!"
        val taskId = intent.getIntExtra("TASK_ID", 0)

        Log.d("AlarmReceiver", "Nhận được báo thức: $taskTitle")

        // Hiển thị thông báo
        val helper = NotificationHelper(context)
        helper.createNotificationChannel()
        helper.showNotification(taskId, taskTitle, taskMessage)
    }
}