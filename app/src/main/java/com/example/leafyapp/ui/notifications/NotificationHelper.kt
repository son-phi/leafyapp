package com.example.leafyapp.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.leafyapp.MainActivity
import com.example.leafyapp.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "leafy_garden_channel"
        const val CHANNEL_NAME = "Garden Care Reminders"
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nhắc nhở chăm sóc cây"
                enableLights(true)
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Truyền tín hiệu để MainActivity biết cần mở tab Garden
            putExtra("OPEN_GARDEN", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // 1. ĐỔI Ở ĐÂY: Dùng ic_launcher (hình App)
            .setSmallIcon(R.mipmap.ic_launcher)
            // 2. THÊM MÀU: Để icon nổi bật hơn với màu xanh lá
            .setColor(ContextCompat.getColor(context, R.color.green))
            .setContentTitle(title)
            .setContentText(message)
            // 3. THÊM STYLE: Để hiển thị được hết nội dung nếu tin nhắn dài
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}