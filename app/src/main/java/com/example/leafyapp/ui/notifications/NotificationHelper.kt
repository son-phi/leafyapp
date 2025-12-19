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
        const val CHANNEL_ID = "leafy_garden_channel" //
        const val CHANNEL_NAME = "Garden Care Reminders"
    }

    // Tạo kênh thông báo (Bắt buộc cho Android 8.0+)
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

    // [CẬP NHẬT] Nhận thêm screen và gardenId để điều hướng
    fun showNotification(
        notificationId: Int,
        title: String,
        message: String,
        screen: String? = null,
        gardenId: String? = null
    ) {
        // Cấu hình Intent để mở MainActivity kèm dữ liệu điều hướng
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // Đưa thông tin từ Server vào Intent để MainActivity xử lý
            putExtra("screen", screen)
            putExtra("TARGET_GARDEN_ID", gardenId)
            putExtra("OPEN_GARDEN", true) //

            if (!gardenId.isNullOrEmpty()) {
                putExtra("IS_FAMILY_MODE", true)
            }
        }

        // Sử dụng notificationId làm requestCode để các thông báo không ghi đè Intent của nhau
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Hình App
            .setColor(ContextCompat.getColor(context, R.color.green)) // Màu xanh lá
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Hiển thị tin nhắn dài
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}