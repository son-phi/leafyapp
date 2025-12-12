package com.example.leafyapp.ui.garden

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.DiseaseLog
import com.example.leafyapp.data.model.Garden
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.data.repository.GardenRepository
import com.example.leafyapp.ui.notifications.AlarmReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

data class PlantTasksGroup(val plant: UserPlant, val tasks: List<CareTask>)

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GardenRepository()

    // 1. KHO TỔNG (Chứa TẤT CẢ cây, dùng để tra cứu Timeline)
    private val _masterPlantList = MutableLiveData<List<UserPlant>>()

    // 2. KHO HIỂN THỊ (Đã lọc theo chế độ, dùng cho UI màn hình chính)
    val allUserPlants = MediatorLiveData<List<UserPlant>>()

    private val _allTasksSource = repository.allTasksWithPlant
    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())
    val allDiseaseLogs: LiveData<List<DiseaseLog>> = repository.diseaseLogs

    private val _currentGarden = MutableLiveData<Garden?>(null)
    val currentGarden: LiveData<Garden?> = _currentGarden

    // Observer theo dõi Repository
    private val repoPlantsObserver = Observer<List<UserPlant>> {
        loadCombinedPlants() // Repo thay đổi -> Tải lại Master List
    }

    init {
        // A. Theo dõi Repository
        repository.userPlants.observeForever(repoPlantsObserver)

        // B. Logic bộ lọc thông minh cho UI
        val filterLogic = {
            val all = _masterPlantList.value ?: emptyList()
            val garden = _currentGarden.value

            val filtered = if (garden == null) {
                // Chế độ CÁ NHÂN: Chỉ lấy cây có gardenId == null
                all.filter { it.gardenId == null }
            } else {
                // Chế độ GIA ĐÌNH: Chỉ lấy cây có gardenId khớp
                all.filter { it.gardenId == garden.id }
            }
            allUserPlants.value = filtered
        }

        // Khi Master List hoặc Chế độ Vườn thay đổi -> Chạy lại bộ lọc
        allUserPlants.addSource(_masterPlantList) { filterLogic() }
        allUserPlants.addSource(_currentGarden) { filterLogic() }

        loadCombinedPlants()
    }

    override fun onCleared() {
        super.onCleared()
        repository.userPlants.removeObserver(repoPlantsObserver)
    }

    // =========================================================================
    // 1. TIMELINE (Tra cứu trên KHO TỔNG _masterPlantList)
    // =========================================================================

    fun getPlantTimeline(userPlantId: String): LiveData<List<TimelineItem>> {
        // Tìm trong Kho Tổng (vì cây có thể không nằm trong list hiển thị hiện tại)
        return _masterPlantList.switchMap { plants ->
            val plant = plants.find { it.id == userPlantId }

            if (plant != null) {
                Log.d("DEBUG_TIMELINE", "Load Timeline: ${plant.nickname} - GardenID: ${plant.gardenId}")
            }

            val diseaseFlow = if (plant != null) {
                repository.listenToDiseaseLogs(userPlantId, plant.gardenId)
            } else {
                flowOf(emptyList())
            }

            diseaseFlow.map { diseaseLogs ->
                val rawEvents = ArrayList<TimelineItem>()

                if (plant != null) {
                    rawEvents.add(TimelineItem.PlantAdded(
                        getOrInitCreationDate(userPlantId, plant.dateAdded),
                        plant.nickname,
                        plant.imagePath
                    ))
                }

                for (log in diseaseLogs) {
                    rawEvents.add(TimelineItem.DiseaseEvent(log.timestamp, log.diseaseName))
                }

                // Task thì vẫn lấy chung (vì Task chưa phân luồng triệt để, nhưng vẫn filter theo ID cây là ổn)
                val allTasks = _allTasksSource.value ?: emptyList()
                val plantTasks = allTasks.filter { it.plant.id == userPlantId }
                for (item in plantTasks) {
                    val task = item.task
                    val historyDates = repository.getHistoryDatesForTask(task.id)
                    for (date in historyDates) {
                        rawEvents.add(TimelineItem.CareEvent(date, task.type.displayName))
                    }
                }

                rawEvents.sortByDescending { it.dateMillis }
                val groupedList = ArrayList<TimelineItem>()
                val calendar = Calendar.getInstance()
                var lastMonthKey = ""

                for (item in rawEvents) {
                    calendar.timeInMillis = item.dateMillis
                    val currentMonthKey = "${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.YEAR)}"
                    if (currentMonthKey != lastMonthKey) {
                        groupedList.add(TimelineItem.Header(item.dateMillis, currentMonthKey))
                        lastMonthKey = currentMonthKey
                    }
                    groupedList.add(item)
                }
                groupedList
            }.asLiveData()
        }
    }

    // =========================================================================
    // 2. LOAD CÂY (Nạp vào KHO TỔNG)
    // =========================================================================

    fun loadCombinedPlants() = viewModelScope.launch(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid ?: return@launch

        val combinedList = ArrayList<UserPlant>()
        try {
            // Cây cá nhân
            val pQuery = db.collection("users").document(uid).collection("user_plants").get().await()
            val pList = pQuery.toObjects(UserPlant::class.java)
            pList.forEachIndexed { i, p ->
                p.id = pQuery.documents[i].id
                p.gardenId = null
            }
            combinedList.addAll(pList)

            // Cây gia đình
            val gQuery = db.collection("gardens").whereArrayContains("members", uid).get().await()

            for (doc in gQuery.documents) {
                val gId = doc.id
                val subQuery = db.collection("gardens").document(gId).collection("plants").get().await()
                val fList = subQuery.toObjects(UserPlant::class.java)
                fList.forEachIndexed { i, p ->
                    p.id = subQuery.documents[i].id
                    p.nickname = "${p.nickname} (Family)"
                    p.gardenId = gId
                }
                combinedList.addAll(fList)
            }

            withContext(Dispatchers.Main) {
                // [QUAN TRỌNG] Đổ vào Kho Tổng -> Logic bộ lọc ở trên sẽ tự chạy để update UI
                _masterPlantList.value = combinedList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =========================================================================
    // 3. CÁC HÀM KHÁC (GIỮ NGUYÊN)
    // =========================================================================

    fun markPlantsAsInfected(plants: List<UserPlant>, diseaseName: String) = viewModelScope.launch {
        for (plant in plants) {
            val log = DiseaseLog(plantId = plant.id, diseaseName = diseaseName, timestamp = System.currentTimeMillis())
            repository.insertDiseaseLog(log, plant.gardenId)
        }
    }

    fun setGardenMode(garden: Garden?) {
        val oldGarden = _currentGarden.value
        if (oldGarden != null) unsubscribeFromGardenTopic(oldGarden.id)
        _currentGarden.value = garden
        // Lưu ý: Repository vẫn switch mode để các logic cũ hoạt động
        repository.switchGardenMode(garden?.id)
        if (garden != null) subscribeToGardenTopic(garden.id)
    }
    private fun subscribeToGardenTopic(id: String) = FirebaseMessaging.getInstance().subscribeToTopic("garden_$id")
    private fun unsubscribeFromGardenTopic(id: String) = FirebaseMessaging.getInstance().unsubscribeFromTopic("garden_$id")

    fun insert(plant: UserPlant) = viewModelScope.launch { repository.insertUserPlant(plant); saveCreationDate(plant.id, System.currentTimeMillis()) }
    fun delete(plant: UserPlant) = viewModelScope.launch { repository.deleteUserPlant(plant) }
    fun updatePlantName(plant: UserPlant, newName: String) = viewModelScope.launch { repository.updateUserPlant(plant.copy(nickname = newName)) }

    fun insertTask(task: CareTask) = viewModelScope.launch {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val currentGardenId = _currentGarden.value?.id
        val taskToSave = task.copy(gardenId = currentGardenId, ownerId = currentUid)
        repository.insertTask(taskToSave)
        if (currentGardenId == null) scheduleLocalAlarm(taskToSave)
    }
    fun updateTask(task: CareTask) = viewModelScope.launch {
        repository.updateTask(task); cancelLocalAlarm(task)
        if (task.gardenId == null) scheduleLocalAlarm(task)
    }
    fun deleteTask(task: CareTask) = viewModelScope.launch { repository.deleteTask(task); cancelLocalAlarm(task) }
    fun markTaskAsCompleted(task: CareTask, completedDate: Long) = viewModelScope.launch {
        repository.insertHistory(TaskHistory(taskId = task.id, completedDate = completedDate))
        val oneDayMillis = 24L * 60 * 60 * 1000
        val newNextDue = completedDate + (task.frequencyDays * oneDayMillis)
        repository.updateTask(task.copy(nextDueDate = newNextDue))
        cancelLocalAlarm(task)
    }

    private fun scheduleLocalAlarm(task: CareTask) {
        if (task.timeHour == -1) return
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", task.id); putExtra("task_title", "Đến giờ chăm cây!"); putExtra("task_message", "Nhiệm vụ: ${task.type.displayName}")
        }
        val pendingIntent = PendingIntent.getBroadcast(context, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, task.timeHour); set(Calendar.MINUTE, task.timeMinute); set(Calendar.SECOND, 0) }
        if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: Exception) {}
    }
    private fun cancelLocalAlarm(task: CareTask) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    fun joinGardenByCode(code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val check = db.collection("gardens").whereArrayContains("members", uid).get().await()
                if (!check.isEmpty) { withContext(Dispatchers.Main) { onError("Đã có vườn rồi") }; return@launch }
                val query = db.collection("gardens").whereEqualTo("inviteCode", code).get().await()
                if (query.isEmpty) { withContext(Dispatchers.Main) { onError("Mã sai") }; return@launch }
                val doc = query.documents[0]
                db.collection("gardens").document(doc.id).update("members", FieldValue.arrayUnion(uid)).await()
                withContext(Dispatchers.Main) { setGardenMode(doc.toObject(Garden::class.java)?.apply { id = doc.id }); onSuccess() }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "") } }
        }
    }
    fun leaveCurrentGarden(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val garden = _currentGarden.value ?: return
        val db = FirebaseFirestore.getInstance()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("gardens").document(garden.id).update("members", FieldValue.arrayRemove(uid)).await()
                withContext(Dispatchers.Main) { setGardenMode(null); onSuccess() }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "") } }
        }
    }

    private fun getStartOfDay(time: Long): Long { val c = Calendar.getInstance().apply { timeInMillis = time; set(11, 0); set(12, 0); set(13, 0); set(14, 0) }; return c.timeInMillis }
    fun getSelectedDayStart() = getStartOfDay(_selectedDate.value ?: System.currentTimeMillis())
    fun setSelectedDate(date: Date) { _selectedDate.value = date.time }
    private fun saveCreationDate(id: String, date: Long) = getApplication<Application>().getSharedPreferences("plant_birthdays", 0).edit().putLong("dob_$id", date).apply()
    private fun getOrInitCreationDate(id: String, fb: Long) = getApplication<Application>().getSharedPreferences("plant_birthdays", 0).getLong("dob_$id", 0L).let { if (it != 0L) it else fb }
    suspend fun getPlantCount(sid: Int) = (allUserPlants.value ?: emptyList()).count { it.plantId == sid }

    val groupedTasksForSelectedDate = MediatorLiveData<List<PlantTasksGroup>>().apply {
        addSource(_selectedDate) { processGrouping() }
        addSource(_allTasksSource) { processGrouping() }
        addSource(repository.taskHistory) { processGrouping() }
    }
    private fun processGrouping() {
        val date = _selectedDate.value ?: System.currentTimeMillis()
        val tasks = _allTasksSource.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val viewingDate = getStartOfDay(date)
            val validTasks = ArrayList<TaskWithPlant>()
            for (item in tasks) {
                val startDate = getStartOfDay(item.task.startDate)
                if (viewingDate >= startDate) {
                    val diff = TimeUnit.MILLISECONDS.toDays(viewingDate - startDate)
                    if (diff % item.task.frequencyDays == 0L) {
                        val history = repository.getHistoryDatesForTask(item.task.id)
                        val isDone = history.any { getStartOfDay(it) == viewingDate }
                        validTasks.add(item.copy(task = item.task.copy(lastCompletedDate = if (isDone) viewingDate else null)))
                    }
                }
            }
            val grouped = validTasks.groupBy { it.plant }.map { PlantTasksGroup(it.key, it.value.map { t -> t.task }) }
            withContext(Dispatchers.Main) { groupedTasksForSelectedDate.value = grouped }
        }
    }
}