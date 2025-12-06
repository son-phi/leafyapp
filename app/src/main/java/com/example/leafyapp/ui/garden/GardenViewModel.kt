package com.example.leafyapp.ui.garden

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.AppDatabase
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.ui.notifications.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

// Data class cho Group Task
data class PlantTasksGroup(val plant: UserPlant, val tasks: List<CareTask>)

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val gardenDao = AppDatabase.getDatabase(application).gardenDao()

    val allUserPlants: LiveData<List<UserPlant>> = gardenDao.getAllUserPlants()

    // --- Logic Task (Giữ nguyên) ---
    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())
    private val _allTasksSource = gardenDao.getAllTasks()

    val groupedTasksForSelectedDate = MediatorLiveData<List<PlantTasksGroup>>().apply {
        addSource(_selectedDate) { date ->
            val tasks = _allTasksSource.value
            if (tasks != null) filterAndGroupTasks(date, tasks)
        }
        addSource(_allTasksSource) { tasks ->
            val date = _selectedDate.value ?: System.currentTimeMillis()
            filterAndGroupTasks(date, tasks)
        }
    }

    private fun filterAndGroupTasks(dateMillis: Long, allTasks: List<TaskWithPlant>) {
        viewModelScope.launch(Dispatchers.IO) {
            val viewingDate = getStartOfDay(dateMillis)
            val validTasks = ArrayList<TaskWithPlant>()

            for (item in allTasks) {
                val task = item.task
                val startDate = getStartOfDay(task.startDate)

                if (viewingDate >= startDate) {
                    val diffMillis = viewingDate - startDate
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

                    if (diffDays % task.frequencyDays == 0L) {
                        val history = gardenDao.getHistoryForTask(task.id)
                        val isCompleted = history.any { getStartOfDay(it) == viewingDate }
                        val displayTask = task.copy(lastCompletedDate = if (isCompleted) viewingDate else null)
                        validTasks.add(item.copy(task = displayTask))
                    }
                }
            }

            val groupedMap = validTasks.groupBy { it.plant }
            val resultList = groupedMap.map { (plant, taskWithPlantList) ->
                PlantTasksGroup(plant, taskWithPlantList.map { it.task })
            }

            withContext(Dispatchers.Main) {
                groupedTasksForSelectedDate.value = resultList
            }
        }
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

    fun getSelectedDayStart(): Long {
        val time = _selectedDate.value ?: System.currentTimeMillis()
        return getStartOfDay(time)
    }


    fun setSelectedDate(date: Date) {
        _selectedDate.value = date.time
    }

    fun insert(plant: UserPlant) = viewModelScope.launch(Dispatchers.IO) {
        val newPlantId = gardenDao.insertUserPlant(plant)
        // Lưu NGAY giờ hiện tại làm ngày thêm cây
        saveCreationDate(newPlantId.toInt(), System.currentTimeMillis())
    }

    fun delete(plant: UserPlant) = viewModelScope.launch(Dispatchers.IO) {
        gardenDao.deleteUserPlant(plant)
    }

    fun updatePlantName(plant: UserPlant, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        val updatedPlant = plant.copy(nickname = newName)
        gardenDao.updateUserPlant(updatedPlant)
    }

    fun deleteTask(task: CareTask) = viewModelScope.launch(Dispatchers.IO) {
        gardenDao.deleteTask(task)
    }

    fun insertTask(task: CareTask) = viewModelScope.launch(Dispatchers.IO) {
        val rowId = gardenDao.insertTask(task)
        if (task.isAutoReminder) {
            val id = if (task.id > 0) task.id.toInt() else rowId.toInt()
            scheduleAlarm(task, id)
        }
    }

    fun markTaskAsCompleted(task: CareTask, completedDate: Long) =
        viewModelScope.launch(Dispatchers.IO) {

            // 1. Lưu history (dùng completedDate đã chuẩn)
            val history = TaskHistory(taskId = task.id, completedDate = completedDate)
            gardenDao.insertHistory(history)

            // 2. Cập nhật nextDueDate cho task
            val oneDayMillis = 24L * 60 * 60 * 1000
            val newNextDue = completedDate + (task.frequencyDays * oneDayMillis)

            val updatedTask = task.copy(nextDueDate = newNextDue)
            // ✅ DÙNG UPDATE, KHÔNG INSERT
            gardenDao.updateTask(updatedTask)

            // 3. Nếu có nhắc giờ thì đặt lại alarm
            if (task.isAutoReminder) {
                scheduleAlarm(updatedTask, task.id.toInt())
            }

            // 4. Làm mới lại list cho ngày đang xem
            val currentDate = _selectedDate.value ?: System.currentTimeMillis()
            val currentTasks = _allTasksSource.value ?: emptyList()
            filterAndGroupTasks(currentDate, currentTasks)
        }


    private fun scheduleAlarm(task: CareTask, taskId: Int) {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(getApplication(), AlarmReceiver::class.java).apply {
            putExtra("TASK_TITLE", "Đến giờ chăm sóc: ${task.type.displayName}")
            putExtra("TASK_MESSAGE", "Đừng quên nhiệm vụ của bạn nhé!")
            putExtra("TASK_ID", taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(), taskId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.nextDueDate, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.nextDueDate, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.nextDueDate, pendingIntent)
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    suspend fun checkPlantExists(plantId: Int): Boolean {
        return gardenDao.isPlantInGarden(plantId)
    }

    // --- HÀM MỚI: Lấy số lượng cây ---
    suspend fun getPlantCount(plantId: Int): Int {
        return gardenDao.countPlantsById(plantId)
    }

    // --- HÀM MỚI: Lấy Timeline ---

    fun getPlantTimeline(plantId: Int): LiveData<List<TimelineItem>> = androidx.lifecycle.liveData(Dispatchers.IO) {
        // 1. Tạo list chứa dữ liệu thô (Raw Data)
        val rawEvents = ArrayList<TimelineItem>()

        //ngày thêm cây vào vườn
        val fixedCreationDate = getOrInitCreationDate(plantId)
        val plant = gardenDao.getUserPlantById(plantId)
        if (plant != null) {
            rawEvents.add(TimelineItem.PlantAdded(fixedCreationDate, plant.nickname, plant.imagePath))
        }

        //lấy nhiệm vụ đã hoàn thành vào timeline
        val tasks = gardenDao.getTasksForPlant(plantId)
        for (task in tasks) {
            val historyDates = gardenDao.getHistoryForTask(task.id)
            for (date in historyDates) {
                rawEvents.add(TimelineItem.CareEvent(date, task.type))
            }
        }

        //Lấy tên bệnh vào timeline
        val prefs = getApplication<Application>().getSharedPreferences("plant_diseases", Context.MODE_PRIVATE)
        val logs = prefs.getStringSet("disease_logs", emptySet()) ?: emptySet()

        for (log in logs) {
            // Tách chuỗi "ID|Tên|Ngày"
            val parts = log.split("|")
            if (parts.size == 3) {
                val pId = parts[0].toIntOrNull()
                val dName = parts[1]
                val dTime = parts[2].toLongOrNull()

                // Nếu đúng là cây này thì thêm vào list
                if (pId == plantId && dTime != null) {
                    rawEvents.add(TimelineItem.DiseaseEvent(dTime, dName))
                }
            }
        }

        // 2. Sắp xếp dữ liệu thô: Mới nhất lên đầu
        rawEvents.sortByDescending { it.dateMillis }

        // 3. --- GOM NHÓM THEO THÁNG ---
        val groupedList = ArrayList<TimelineItem>()
        val calendar = Calendar.getInstance()
        var lastMonthKey = "" // Dùng để kiểm tra tháng trùng

        for (item in rawEvents) {
            calendar.timeInMillis = item.dateMillis

            // Tạo key đại diện cho tháng (VD: "12-2025")
            val currentMonthKey = "${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.YEAR)}"

            // Nếu tháng này khác tháng trước -> Chèn Header
            if (currentMonthKey != lastMonthKey) {
                groupedList.add(TimelineItem.Header(item.dateMillis, currentMonthKey))
                lastMonthKey = currentMonthKey
            }
            groupedList.add(item)
        }

        emit(groupedList)
    }
    private fun saveCreationDate(plantId: Int, date: Long) {
        val prefs = getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE)
        prefs.edit().putLong("dob_$plantId", date).apply()
    }

    // Hàm lấy ngày hoặc tạo mới nếu chưa có (cho cây cũ)
    private fun getOrInitCreationDate(plantId: Int): Long {
        val prefs = getApplication<Application>().getSharedPreferences(
            "plant_birthdays",
            Context.MODE_PRIVATE
        )
        // 1. Thử lấy ngày đã lưu
        val savedDate = prefs.getLong("dob_$plantId", 0L)
        if (savedDate != 0L) {
            return savedDate
        } else {
            // 2. Nếu chưa có (Cây cũ từ trước khi update code)
            // Lấy luôn giờ hiện tại làm mốc cố định
            val now = System.currentTimeMillis()
            // 3. Lưu lại ngay để lần sau mở lại nó vẫn là giờ này (không đổi nữa)
            saveCreationDate(plantId, now)
            return now
        }
    }

    fun markPlantsAsInfected(plants: List<UserPlant>, diseaseName: String) = viewModelScope.launch(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prefs = getApplication<Application>().getSharedPreferences("plant_diseases", Context.MODE_PRIVATE)
        val existingSet = prefs.getStringSet("disease_logs", HashSet())?.toMutableSet() ?: HashSet()

        for (plant in plants) {
            val logEntry = "${plant.id}|$diseaseName|$now"
            existingSet.add(logEntry)
        }

        prefs.edit().putStringSet("disease_logs", existingSet).apply()

    }
}