package com.example.leafyapp.ui.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType

/**
 * Hẹn giờ nhắc cho lịch chăm sóc (users/{uid}/plants/{plantId}/careSchedules) bằng AlarmManager.
 * Mỗi lịch giữ đúng một báo thức tại `nextDueAt`; gọi [sync] mỗi khi danh sách lịch thay đổi.
 */
object CareReminderScheduler {

    private const val TAG = "CareReminder"
    private const val PREFS = "care_reminders"
    private const val KEY_IDS = "scheduled_ids"

    fun requestCode(scheduleId: String): Int = ("care:" + scheduleId).hashCode()

    private fun pendingIntent(context: Context, scheduleId: String, title: String? = null, message: String? = null): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "leafy.CARE_REMINDER.$scheduleId"
            putExtra("TASK_ID", requestCode(scheduleId))
            if (title != null) putExtra("TASK_TITLE", title)
            if (message != null) putExtra("TASK_MESSAGE", message)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(scheduleId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun title(s: CareSchedule): String = when (CareTaskType.parse(s.taskType)) {
        CareTaskType.WATERING -> "💧 Đến giờ tưới nước"
        CareTaskType.FERTILIZING -> "🧪 Đến lịch bón phân"
        CareTaskType.PRUNING -> "✂️ Đến lịch cắt tỉa"
        CareTaskType.SPRAYING -> "🌫️ Đến lịch phun thuốc"
        CareTaskType.HEALTH_CHECK -> "🔍 Đến lịch kiểm tra sức khỏe cây"
        CareTaskType.OTHER -> "🌿 ${s.title.ifBlank { "Công việc chăm sóc" }}"
    }

    /** Đặt (hoặc thay) báo thức cho một lịch. Bỏ qua lịch tắt hoặc hạn đã qua. */
    fun schedule(context: Context, s: CareSchedule, plantName: String) {
        val due = (s.nextDueAt ?: s.startAt)?.toDate()?.time ?: return
        if (!s.active || due <= System.currentTimeMillis()) {
            cancel(context, s.id)
            return
        }
        val message = listOf(plantName, s.note).filter { it.isNotBlank() }.joinToString(": ")
        val pi = pendingIntent(context, s.id, title(s), message)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
            }
            remember(context, s.id, add = true)
        } catch (e: SecurityException) {
            Log.w(TAG, "Không đặt được báo thức chính xác: ${e.message}")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
            remember(context, s.id, add = true)
        }
    }

    fun cancel(context: Context, scheduleId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, scheduleId))
        remember(context, scheduleId, add = false)
    }

    /** Đồng bộ toàn bộ: đặt báo thức cho lịch hiện có, hủy báo thức của lịch đã xóa. */
    fun sync(context: Context, schedules: List<CareSchedule>, plantNames: Map<String, String>) {
        val current = schedules.map { it.id }.toSet()
        (scheduledIds(context) - current).forEach { cancel(context, it) }
        schedules.forEach { schedule(context, it, plantNames[it.plantId].orEmpty()) }
    }

    private fun scheduledIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_IDS, emptySet()).orEmpty()

    private fun remember(context: Context, id: String, add: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = scheduledIds(context).toMutableSet()
        if (add) ids += id else ids -= id
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
    }
}
