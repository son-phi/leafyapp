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
import com.example.leafyapp.data.model.Garden
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.data.repository.GardenRepository
import com.example.leafyapp.ui.notifications.AlarmReceiver
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import com.example.leafyapp.data.model.DiseaseLog
import com.google.firebase.firestore.FieldValue // [QUAN TRỌNG] Để thêm thành viên vào mảng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Data class cho Group Task
data class PlantTasksGroup(val plant: UserPlant, val tasks: List<CareTask>)

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Repository
    private val repository = GardenRepository()

    // 2. LiveData dữ liệu
    val allUserPlants: LiveData<List<UserPlant>> = repository.userPlants
    private val _allTasksSource = repository.allTasksWithPlant
    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())

    val allDiseaseLogs: LiveData<List<DiseaseLog>> = repository.diseaseLogs

    // 3. [MỚI] Biến lưu Vườn hiện tại (để biết đang ở Vườn Riêng hay Chung)
    private val _currentGarden = MutableLiveData<Garden?>(null)
    val currentGarden: LiveData<Garden?> = _currentGarden

    private val tasksObserver = androidx.lifecycle.Observer<List<TaskWithPlant>> { tasks ->
        // Hễ danh sách thay đổi (do Firebase cập nhật) -> Đặt lại báo thức ngay
        rescheduleAllAlarms(tasks)
    }

    init {
        // Đăng ký lắng nghe
        _allTasksSource.observeForever(tasksObserver)
    }

    override fun onCleared() {
        // Hủy lắng nghe khi thoát app để tránh rò rỉ bộ nhớ
        _allTasksSource.removeObserver(tasksObserver)
        super.onCleared()
    }

    // --- HÀM CHUYỂN ĐỔI CHẾ ĐỘ (Gọi từ UI khi gạt Switch) ---
    fun setGardenMode(garden: Garden?) {
        _currentGarden.value = garden
        // Báo cho Repository biết để đổi đường dẫn Firebase
        repository.switchGardenMode(garden?.id)
    }

    // --- LOGIC TASK GROUP ---
    val groupedTasksForSelectedDate = MediatorLiveData<List<PlantTasksGroup>>().apply {
        addSource(_selectedDate) { date ->
            val tasks = _allTasksSource.value
            if (tasks != null) filterAndGroupTasks(date, tasks)
        }
        addSource(_allTasksSource) { tasks ->
            val date = _selectedDate.value ?: System.currentTimeMillis()
            filterAndGroupTasks(date, tasks)
        }
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

    // --- CÁC HÀM CRUD ---

    fun insert(plant: UserPlant) = viewModelScope.launch {
        repository.insertUserPlant(plant)
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
            // [SỬA] Truyền Garden hiện tại vào để tính delay
            scheduleAlarm(task, task.id.hashCode(), _currentGarden.value)
        }
    }

    fun updateTask(task: CareTask) = viewModelScope.launch {
        repository.updateTask(task)
        if (task.isAutoReminder) {
            // [SỬA] Truyền Garden hiện tại
            scheduleAlarm(task, task.id.hashCode(), _currentGarden.value)
        }
    }

    fun markTaskAsCompleted(task: CareTask, completedDate: Long) = viewModelScope.launch {
        // 1. Lưu history
        val history = TaskHistory(taskId = task.id, completedDate = completedDate)
        repository.insertHistory(history)

        // 2. Cập nhật Next Due Date
        val oneDayMillis = 24L * 60 * 60 * 1000
        val newNextDue = completedDate + (task.frequencyDays * oneDayMillis)
        val updatedTask = task.copy(nextDueDate = newNextDue)

        repository.updateTask(updatedTask)

        // 3. Đặt lại báo thức
        if (task.isAutoReminder) {
            // [SỬA] Truyền Garden hiện tại
            scheduleAlarm(updatedTask, task.id.hashCode(), _currentGarden.value)
        }
    }

    // --- TRA CỨU ---

    suspend fun checkPlantExists(speciesId: Int): Boolean {
        val plants = allUserPlants.value ?: emptyList()
        return plants.any { it.plantId == speciesId }
    }

    suspend fun getPlantCount(speciesId: Int): Int {
        val plants = allUserPlants.value ?: emptyList()
        return plants.count { it.plantId == speciesId }
    }

    // --- TIMELINE ---
    fun getPlantTimeline(userPlantId: String): LiveData<List<TimelineItem>> = androidx.lifecycle.liveData(Dispatchers.Default) {
        val rawEvents = ArrayList<TimelineItem>()

        // 1. SỰ KIỆN: THÊM CÂY
        val plants = allUserPlants.value ?: emptyList()
        val plant = plants.find { it.id == userPlantId }
        if (plant != null) {
            val creationDate = getOrInitCreationDate(userPlantId, plant.dateAdded)
            rawEvents.add(TimelineItem.PlantAdded(creationDate, plant.nickname, plant.imagePath))
        }

        // 2. SỰ KIỆN: CHĂM SÓC (Task History)
        val allTasks = _allTasksSource.value ?: emptyList()
        val plantTasks = allTasks.filter { it.plant.id == userPlantId }

        for (item in plantTasks) {
            val task = item.task
            val historyDates = repository.getHistoryDatesForTask(task.id)
            for (date in historyDates) {
                rawEvents.add(TimelineItem.CareEvent(date, task.type))
            }
        }

        // 3. [SỬA] SỰ KIỆN: BỆNH TẬT (Lấy từ Repository thay vì SharedPreferences)
        val allLogs = repository.diseaseLogs.value ?: emptyList()

        // Lọc ra những log thuộc về cây này
        val infectedLogs = allLogs.filter { it.plantId == userPlantId }

        for (log in infectedLogs) {
            rawEvents.add(TimelineItem.DiseaseEvent(log.timestamp, log.diseaseName))
        }

        // 4. SẮP XẾP & GOM NHÓM (Logic cũ)
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

    private fun saveCreationDate(plantId: String, date: Long) {
        val prefs = getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE)
        prefs.edit().putLong("dob_$plantId", date).apply()
    }

    private fun getOrInitCreationDate(plantId: String, fallbackDate: Long): Long {
        val prefs = getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE)
        val savedDate = prefs.getLong("dob_$plantId", 0L)
        return if (savedDate != 0L) savedDate else fallbackDate
    }

    // --- [MỚI] LOGIC ALARM CHO GIA ĐÌNH ---
    private fun scheduleAlarm(task: CareTask, taskId: Int, garden: Garden? = null) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

        // 1. TÍNH TOÁN ĐỘ TRỄ (DELAY)
        var delayMillis: Long = 0

        if (garden != null && currentUid != null) {
            val myIndex = garden.members.indexOf(currentUid)
            if (myIndex > 0) {
                // Người thứ 1: 0p, Người thứ 2: 30p, Người thứ 3: 60p...
                delayMillis = myIndex * 1L * 60 * 1000
            }
        }

        // Thời gian báo thức thực tế
        val triggerTime = task.nextDueDate + delayMillis

        // 2. CHUẨN BỊ INTENT
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TASK_TITLE", "Đến giờ chăm sóc: ${task.type.displayName}")
            putExtra("TASK_MESSAGE", "Đừng quên nhiệm vụ của bạn nhé!")
            putExtra("TASK_ID", taskId)

            // Gửi thông tin để Receiver check Firebase (quan trọng cho logic hủy kèo)
            putExtra("GARDEN_ID", garden?.id)
            putExtra("TASK_FIRESTORE_ID", task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. ĐẶT BÁO THỨC
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    // --- [MỚI] TỰ ĐỘNG ĐẶT BÁO THỨC KHI DỮ LIỆU VỀ ---
    private fun rescheduleAllAlarms(tasks: List<TaskWithPlant>) {
        val garden = _currentGarden.value

        for (item in tasks) {
            val task = item.task
            // Chỉ đặt báo thức nếu:
            // 1. Task có bật nhắc nhở tự động
            // 2. Task chưa hoàn thành hôm nay (Logic check LastCompletedDate)
            // 3. Thời gian nhắc nhở (NextDueDate) chưa trôi qua quá lâu (hoặc tùy logic ông)

            if (task.isAutoReminder) {
                // Tính toán lại xem hôm nay đã làm chưa
                val todayStart = getStartOfDay(System.currentTimeMillis())
                val lastCompleted = if (task.lastCompletedDate != null) getStartOfDay(task.lastCompletedDate!!) else 0L

                if (lastCompleted != todayStart) {
                    // Chưa làm -> Đặt báo thức
                    scheduleAlarm(task, task.id.hashCode(), garden)
                }
            }
        }
    }

    fun markPlantsAsInfected(plants: List<UserPlant>, diseaseName: String) = viewModelScope.launch {
        // Không dùng SharedPreferences nữa!
        // Duyệt qua từng cây được chọn và tạo log
        for (plant in plants) {
            val log = DiseaseLog(
                plantId = plant.id,
                diseaseName = diseaseName,
                timestamp = System.currentTimeMillis()
            )
            // Gọi Repository để đẩy lên Firestore (Vườn riêng hay chung do Repo tự lo)
            repository.insertDiseaseLog(log)
        }
    }

    // --- LOGIC NHẬP MÃ MỜI (JOIN GARDEN) ---
    fun joinGardenByCode(inviteCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onError("Vui lòng đăng nhập!")
            return
        }

        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // BƯỚC 1: KIỂM TRA XEM ĐANG CÓ VƯỜN NÀO CHƯA
                val checkQuery = db.collection("gardens")
                    .whereArrayContains("members", currentUser.uid)
                    .get()
                    .await()

                if (!checkQuery.isEmpty) {
                    // Đã có vườn rồi -> Chặn luôn
                    withContext(Dispatchers.Main) {
                        onError("Bạn đang ở trong một Vườn Gia Đình rồi. Hãy rời đi trước khi tham gia vườn mới!")
                    }
                    return@launch
                }

                // BƯỚC 2: TÌM VƯỜN THEO MÃ CODE
                val codeQuery = db.collection("gardens")
                    .whereEqualTo("inviteCode", inviteCode)
                    .limit(1)
                    .get()
                    .await()

                if (codeQuery.isEmpty) {
                    withContext(Dispatchers.Main) {
                        onError("Mã mời không chính xác hoặc vườn không tồn tại.")
                    }
                    return@launch
                }

                // BƯỚC 3: THÊM USER VÀO VƯỜN ĐÓ
                val gardenDoc = codeQuery.documents[0]

                // Dùng FieldValue.arrayUnion để thêm vào mảng (tránh ghi đè danh sách cũ)
                db.collection("gardens").document(gardenDoc.id)
                    .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(currentUser.uid))
                    .await()

                // Thành công!
                withContext(Dispatchers.Main) {
                    // Cập nhật ngay trạng thái ViewModel sang vườn mới
                    val newGarden = gardenDoc.toObject(Garden::class.java)?.apply { id = gardenDoc.id }
                    setGardenMode(newGarden)
                    onSuccess()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Lỗi kết nối: ${e.message}")
                }
            }
        }
    }

    // --- HÀM RỜI VƯỜN (LEAVE GARDEN) ---
    fun leaveCurrentGarden(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val garden = _currentGarden.value

        if (currentUser == null || garden == null) {
            onError("Lỗi xác thực!")
            return
        }

        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Dùng arrayRemove để xóa UID khỏi mảng members
                db.collection("gardens").document(garden.id)
                    .update("members", FieldValue.arrayRemove(currentUser.uid))
                    .await()

                withContext(Dispatchers.Main) {
                    // Xóa thành công -> Quay về vườn cá nhân
                    setGardenMode(null)
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Lỗi: ${e.message}")
                }
            }
        }
    }
}