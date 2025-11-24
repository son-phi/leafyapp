package com.example.leafyapp.ui.garden

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.AppDatabase
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.ui.notifications.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val gardenDao = AppDatabase.getDatabase(application).gardenDao()
    val allUserPlants: LiveData<List<UserPlant>> = gardenDao.getAllUserPlants()

    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())

    val tasksForSelectedDate: LiveData<List<TaskWithPlant>> = _selectedDate.switchMap { dateMillis ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateMillis

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val start = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val end = calendar.timeInMillis

        gardenDao.getTasksForDate(start, end)
    }

    fun insert(plant: UserPlant) = viewModelScope.launch(Dispatchers.IO) {
        gardenDao.insertUserPlant(plant)
    }

    fun delete(plant: UserPlant) = viewModelScope.launch(Dispatchers.IO) {
        gardenDao.deleteUserPlant(plant)
    }

    fun setSelectedDate(date: Date) {
        _selectedDate.value = date.time
    }

    // CẬP NHẬT HÀM NÀY: Vừa lưu DB vừa đặt báo thức
    fun insertTask(task: CareTask) = viewModelScope.launch(Dispatchers.IO) {
        val rowId = gardenDao.insertTask(task) // Lưu DB trả về ID dòng vừa thêm

        // Nếu người dùng bật Auto Reminder -> Đặt báo thức
        if (task.isAutoReminder) {
            scheduleAlarm(task, rowId.toInt())
        }
    }

    fun updateTask(task: CareTask) = viewModelScope.launch(Dispatchers.IO) {
        gardenDao.insertTask(task)
        // Khi update (hoàn thành xong -> dời sang ngày mới), cũng cần đặt lại báo thức
        if (task.isAutoReminder) {
            scheduleAlarm(task, task.id.toInt())
        }
    }

    // Hàm Đặt Báo Thức
    private fun scheduleAlarm(task: CareTask, taskId: Int) {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(getApplication(), AlarmReceiver::class.java).apply {
            putExtra("TASK_TITLE", "Đến giờ chăm sóc: ${task.type.displayName}")
            putExtra("TASK_MESSAGE", "Đừng quên nhiệm vụ của bạn nhé!")
            putExtra("TASK_ID", taskId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            taskId, // ID riêng biệt cho từng Task để không bị ghi đè
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Đặt lịch chính xác
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.nextDueDate,
                        pendingIntent
                    )
                } else {
                    // Nếu chưa có quyền, có thể set chuông thường hoặc nhắc user cấp quyền (xử lý sau)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.nextDueDate,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    task.nextDueDate,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}