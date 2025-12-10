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
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.data.repository.GardenRepository
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

    // 1. THAY DAO BẰNG REPOSITORY
    // private val gardenDao = AppDatabase.getDatabase(application).gardenDao() -> XÓA
    private val repository = GardenRepository() // -> THÊM MỚI

    // 2. Lấy dữ liệu từ Repository
    val allUserPlants: LiveData<List<UserPlant>> = repository.userPlants
    private val _allTasksSource = repository.allTasksWithPlant

    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())

    // --- LOGIC TASK GROUP (Đã sửa để dùng Repository) ---
    val groupedTasksForSelectedDate = MediatorLiveData<List<PlantTasksGroup>>().apply {
        // Khi ngày chọn thay đổi
        addSource(_selectedDate) { date ->
            val tasks = _allTasksSource.value
            if (tasks != null) filterAndGroupTasks(date, tasks)
        }
        // Khi danh sách Task từ Firebase thay đổi
        addSource(_allTasksSource) { tasks ->
            val date = _selectedDate.value ?: System.currentTimeMillis()
            filterAndGroupTasks(date, tasks)
        }
        // Khi lịch sử hoàn thành thay đổi (để cập nhật tick xanh)
        addSource(repository.taskHistory) {
            val date = _selectedDate.value ?: System.currentTimeMillis()
            val tasks = _allTasksSource.value
            if (tasks != null) filterAndGroupTasks(date, tasks)
        }
    }

    private fun filterAndGroupTasks(dateMillis: Long, allTasks: List<TaskWithPlant>) {
        viewModelScope.launch(Dispatchers.Default) {
            val viewingDate = getStartOfDay(dateMillis)
            val validTasks = ArrayList<TaskWithPlant>()

            for (item in allTasks) {
                val task = item.task
                val startDate = getStartOfDay(task.startDate)

                if (viewingDate >= startDate) {
                    val diffMillis = viewingDate - startDate
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

                    if (diffDays % task.frequencyDays == 0L) {
                        // --- SỬA: Lấy lịch sử từ RAM (Repository) thay vì query DB ---
                        val historyDates = repository.getHistoryDatesForTask(task.id)

                        val isCompleted = historyDates.any { getStartOfDay(it) == viewingDate }
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

    // --- CÁC HÀM CRUD (Gọi sang Repository) ---

    fun insert(plant: UserPlant) = viewModelScope.launch {
        repository.insertUserPlant(plant)
        // saveCreationDate logic cũ vẫn dùng được vì lưu ở SharedPreferences
        saveCreationDate(plant.id, System.currentTimeMillis())
    }

    fun delete(plant: UserPlant) = viewModelScope.launch {
        repository.deleteUserPlant(plant)
    }

    fun updatePlantName(plant: UserPlant, newName: String) = viewModelScope.launch {
        val updatedPlant = plant.copy(nickname = newName)
        repository.updateUserPlant(updatedPlant)
    }

    fun deleteTask(task: CareTask) = viewModelScope.launch {
        repository.deleteTask(task)
    }

    fun insertTask(task: CareTask) = viewModelScope.launch {
        repository.insertTask(task)
        if (task.isAutoReminder) {
            // Task ID giờ là String, cần hashCode() để tạo PendingIntent ID (Int)
            scheduleAlarm(task, task.id.hashCode())
        }
    }

    // Thêm hàm này vào GardenViewModel
    fun updateTask(task: CareTask) = viewModelScope.launch {
        repository.updateTask(task)
        if (task.isAutoReminder) {
            scheduleAlarm(task, task.id.hashCode())
        }
    }

    fun markTaskAsCompleted(task: CareTask, completedDate: Long) = viewModelScope.launch {
        // 1. Lưu history lên Firebase
        val history = TaskHistory(taskId = task.id, completedDate = completedDate)
        repository.insertHistory(history)

        // 2. Cập nhật Next Due Date cho Task
        val oneDayMillis = 24L * 60 * 60 * 1000
        val newNextDue = completedDate + (task.frequencyDays * oneDayMillis)
        val updatedTask = task.copy(nextDueDate = newNextDue)

        repository.updateTask(updatedTask)

        // 3. Đặt lại báo thức
        if (task.isAutoReminder) {
            scheduleAlarm(updatedTask, task.id.hashCode())
        }
    }

    // --- CÁC HÀM TRA CỨU (Thay thế SQL Query bằng lọc List trên RAM) ---

    // Kiểm tra xem đã có cây thuộc loài này trong vườn chưa (check theo plantId - species ID)
    suspend fun checkPlantExists(speciesId: Int): Boolean {
        val plants = allUserPlants.value ?: emptyList()
        return plants.any { it.plantId == speciesId }
    }

    // Đếm số lượng cây thuộc loài này
    suspend fun getPlantCount(speciesId: Int): Int {
        val plants = allUserPlants.value ?: emptyList()
        return plants.count { it.plantId == speciesId }
    }

    // --- TIMELINE (Viết lại hoàn toàn để không dùng DAO) ---
    // Lưu ý: userPlantId bây giờ là String (UUID từ Firebase)
    fun getPlantTimeline(userPlantId: String): LiveData<List<TimelineItem>> = androidx.lifecycle.liveData(Dispatchers.Default) {
        val rawEvents = ArrayList<TimelineItem>()

        // 1. Lấy thông tin cây (Tìm trong list đã tải về)
        val plants = allUserPlants.value ?: emptyList()
        val plant = plants.find { it.id == userPlantId }

        if (plant != null) {
            // Lấy ngày tạo (ưu tiên lấy từ SharedPreferences nếu có, hoặc dùng dateAdded từ object)
            val creationDate = getOrInitCreationDate(userPlantId, plant.dateAdded)
            rawEvents.add(TimelineItem.PlantAdded(creationDate, plant.nickname, plant.imagePath))
        }

        // 2. Lấy các Task và Lịch sử hoàn thành của cây này
        val allTasks = _allTasksSource.value ?: emptyList()
        val plantTasks = allTasks.filter { it.plant.id == userPlantId } // Lọc task của cây này

        for (item in plantTasks) {
            val task = item.task
            // Lấy lịch sử từ Repository
            val historyDates = repository.getHistoryDatesForTask(task.id)
            for (date in historyDates) {
                rawEvents.add(TimelineItem.CareEvent(date, task.type))
            }
        }

        // 3. Lấy nhật ký bệnh (Lưu Local SharedPreferences - Giữ nguyên logic cũ)
        val prefs = getApplication<Application>().getSharedPreferences("plant_diseases", Context.MODE_PRIVATE)
        val logs = prefs.getStringSet("disease_logs", emptySet()) ?: emptySet()

        for (log in logs) {
            val parts = log.split("|") // Format: "PlantID|DiseaseName|Time"
            if (parts.size == 3) {
                val pId = parts[0] // ID cây
                val dName = parts[1]
                val dTime = parts[2].toLongOrNull()

                // So sánh String ID
                if (pId == userPlantId && dTime != null) {
                    rawEvents.add(TimelineItem.DiseaseEvent(dTime, dName))
                }
            }
        }

        // 4. Sắp xếp và Gom nhóm (Giữ nguyên logic cũ)
        rawEvents.sortByDescending { it.dateMillis }

        val groupedList = ArrayList<TimelineItem>()
        val calendar = Calendar.getInstance()
        var lastMonthKey = ""

        for (item in rawEvents) {
            calendar.timeInMillis = item.dateMillis
            val currentMonthKey = "${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.YEAR)}"

            if (currentMonthKey != lastMonthKey) {
                groupedList.add(TimelineItem.Header(item.dateMillis, currentMonthKey))
                lastMonthKey = currentMonthKey
            }
            groupedList.add(item)
        }

        emit(groupedList)
    }

    // --- HELPER FUNCTIONS ---

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

    // Sửa lại hàm này dùng String ID thay vì Int
    private fun saveCreationDate(plantId: String, date: Long) {
        val prefs = getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE)
        prefs.edit().putLong("dob_$plantId", date).apply()
    }

    private fun getOrInitCreationDate(plantId: String, fallbackDate: Long): Long {
        val prefs = getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE)
        val savedDate = prefs.getLong("dob_$plantId", 0L)
        return if (savedDate != 0L) savedDate else fallbackDate
    }

    // Logic Alarm giữ nguyên
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

    // --- XỬ LÝ LƯU BỆNH CÂY (Dùng SharedPreferences) ---
    // Hàm này giúp lưu lại lịch sử cây bị bệnh để hiện thị lên Timeline
    fun markPlantsAsInfected(plants: List<UserPlant>, diseaseName: String) = viewModelScope.launch(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prefs = getApplication<Application>().getSharedPreferences("plant_diseases", Context.MODE_PRIVATE)

        // Lấy danh sách cũ
        val existingSet = prefs.getStringSet("disease_logs", HashSet())?.toMutableSet() ?: HashSet()

        for (plant in plants) {
            // Format lưu trữ: "ID_Cây|Tên_Bệnh|Thời_Gian"
            val logEntry = "${plant.id}|$diseaseName|$now"
            existingSet.add(logEntry)
        }

        // Lưu ngược lại vào máy
        prefs.edit().putStringSet("disease_logs", existingSet).apply()
    }
}