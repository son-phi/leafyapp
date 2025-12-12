package com.example.leafyapp.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.leafyapp.data.model.CareTask
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Lấy dữ liệu gửi kèm từ AlarmManager
        val taskId = intent.getIntExtra("TASK_ID", 0)
        val title = intent.getStringExtra("TASK_TITLE") ?: "Chăm sóc cây"
        val message = intent.getStringExtra("TASK_MESSAGE") ?: "Đến giờ chăm cây rồi!"

        // Dữ liệu quan trọng để check Family
        val gardenId = intent.getStringExtra("GARDEN_ID")
        val firestoreTaskId = intent.getStringExtra("TASK_FIRESTORE_ID")

        // 2. PHÂN LUỒNG XỬ LÝ
        if (gardenId == null || firestoreTaskId == null) {
            // TRƯỜNG HỢP A: Task Cá nhân -> Gọi Helper báo luôn
            triggerNotification(context, taskId, title, message)
        } else {
            // TRƯỜNG HỢP B: Task Gia đình -> Check Firebase
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val shouldRing = checkTaskStatusOnFirebase(gardenId, firestoreTaskId)

                    if (shouldRing) {
                        withContext(Dispatchers.Main) {
                            triggerNotification(context, taskId, title, message)
                        }
                    } else {
                        Log.d("AlarmReceiver", "Task $firestoreTaskId đã xong. Không báo nữa!")
                    }
                } catch (e: Exception) {
                    // Lỗi mạng -> Vẫn báo cho chắc
                    Log.e("AlarmReceiver", "Lỗi check Firebase", e)
                    withContext(Dispatchers.Main) {
                        triggerNotification(context, taskId, title, message)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    // --- HÀM CHECK FIREBASE (Logic giữ nguyên) ---
    private suspend fun checkTaskStatusOnFirebase(gardenId: String, taskId: String): Boolean {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection("gardens")
            .document(gardenId)
            .collection("tasks")
            .document(taskId)
            .get()
            .await()

        val task = snapshot.toObject(CareTask::class.java) ?: return true
        val lastCompleted = task.lastCompletedDate ?: 0L
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val completedDayStart = getStartOfDay(lastCompleted)

        return completedDayStart != todayStart
    }

    // --- [SỬA] GỌI NOTIFICATION HELPER ---
    private fun triggerNotification(context: Context, id: Int, title: String, msg: String) {
        // Khởi tạo Helper
        val helper = NotificationHelper(context)

        // Đảm bảo Channel đã được tạo (gọi thừa còn hơn bỏ sót)
        helper.createNotificationChannel()

        // Hiển thị thông báo
        helper.showNotification(id, title, msg)
    }

    private fun getStartOfDay(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}