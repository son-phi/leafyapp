package com.example.leafyapp.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.leafyapp.MainActivity
import com.example.leafyapp.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "leafy_garden_channel"
        const val CHANNEL_NAME = "Garden Care Reminders"
    }

    // Tạo Notification Channel (Bắt buộc cho Android 8.0+)
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // Mức độ quan trọng cao (có tiếng, rung)
            ).apply {
                description = "Nhắc nhở chăm sóc cây"
                enableLights(true)
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // Hiển thị thông báo
    fun showNotification(notificationId: Int, title: String, message: String) {
        // Intent mở App khi bấm vào thông báo
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Sửa lỗi Crash trên Android 12+ bằng cách thêm FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop) // Icon nhỏ trên thanh trạng thái (Dùng tạm icon nước)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Bấm vào thì tự biến mất
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}