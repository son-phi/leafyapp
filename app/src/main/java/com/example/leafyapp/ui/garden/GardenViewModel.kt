package com.example.leafyapp.ui.garden

import android.app.Application
import android.content.Context
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import com.example.leafyapp.data.model.DiseaseLog
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.messaging.FirebaseMessaging

data class PlantTasksGroup(val plant: UserPlant, val tasks: List<CareTask>)

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GardenRepository()

    val allUserPlants: LiveData<List<UserPlant>> = repository.userPlants
    private val _allTasksSource = repository.allTasksWithPlant
    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())
    val allDiseaseLogs: LiveData<List<DiseaseLog>> = repository.diseaseLogs

    private val _currentGarden = MutableLiveData<Garden?>(null)
    val currentGarden: LiveData<Garden?> = _currentGarden

    // --- HÀM CHUYỂN ĐỔI CHẾ ĐỘ & ĐĂNG KÝ FCM ---
    fun setGardenMode(garden: Garden?) {
        val oldGarden = _currentGarden.value

        // 1. Nếu đang ở vườn cũ -> Hủy đăng ký kênh cũ
        if (oldGarden != null) {
            unsubscribeFromGardenTopic(oldGarden.id)
        }

        _currentGarden.value = garden
        repository.switchGardenMode(garden?.id)

        // 2. Nếu vào vườn mới -> Đăng ký kênh mới
        if (garden != null) {
            subscribeToGardenTopic(garden.id)
        }
    }

    // --- LOGIC FCM TOPIC ---
    private fun subscribeToGardenTopic(gardenId: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("garden_$gardenId")
    }

    private fun unsubscribeFromGardenTopic(gardenId: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic("garden_$gardenId")
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
        // 1. Lấy ID của vườn hiện tại (nếu đang ở Family Mode)
        val currentGardenId = _currentGarden.value?.id

        // 2. Tạo Task mới có kèm gardenId (hoặc null nếu là Task cá nhân)
        val taskWithGardenId = task.copy(gardenId = currentGardenId)

        // 3. Lưu Task đã có đầy đủ thông tin lên Repository
        repository.insertTask(taskWithGardenId)
    }

    fun updateTask(task: CareTask) = viewModelScope.launch {
        repository.updateTask(task)
    }

    fun markTaskAsCompleted(task: CareTask, completedDate: Long) = viewModelScope.launch {
        val history = TaskHistory(taskId = task.id, completedDate = completedDate)
        repository.insertHistory(history)

        val oneDayMillis = 24L * 60 * 60 * 1000
        val newNextDue = completedDate + (task.frequencyDays * oneDayMillis)
        val updatedTask = task.copy(nextDueDate = newNextDue)

        repository.updateTask(updatedTask)
    }

    // --- LOGIC JOIN/LEAVE ---

    fun joinGardenByCode(inviteCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) { onError("Login required"); return }
        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val checkQuery = db.collection("gardens").whereArrayContains("members", currentUser.uid).get().await()
                if (!checkQuery.isEmpty) { withContext(Dispatchers.Main) { onError("Đã có vườn rồi") }; return@launch }

                val codeQuery = db.collection("gardens").whereEqualTo("inviteCode", inviteCode).get().await()
                if (codeQuery.isEmpty) { withContext(Dispatchers.Main) { onError("Mã sai") }; return@launch }

                val gardenDoc = codeQuery.documents[0]
                db.collection("gardens").document(gardenDoc.id)
                    .update("members", FieldValue.arrayUnion(currentUser.uid)).await()

                withContext(Dispatchers.Main) {
                    val newGarden = gardenDoc.toObject(Garden::class.java)?.apply { id = gardenDoc.id }
                    setGardenMode(newGarden)
                    onSuccess()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "") } }
        }
    }

    fun leaveCurrentGarden(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val garden = _currentGarden.value
        if (currentUser == null || garden == null) return

        val db = FirebaseFirestore.getInstance()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("gardens").document(garden.id)
                    .update("members", FieldValue.arrayRemove(currentUser.uid)).await()

                withContext(Dispatchers.Main) {
                    setGardenMode(null)
                    onSuccess()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "") } }
        }
    }

    // --- HELPER (Group Task) ---

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
            val resultList = groupedMap.map { (plant, taskWithPlantList) -> PlantTasksGroup(plant, taskWithPlantList.map { it.task }) }
            withContext(Dispatchers.Main) { groupedTasksForSelectedDate.value = resultList }
        }
    }

    private fun getStartOfDay(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    fun getSelectedDayStart(): Long = getStartOfDay(_selectedDate.value ?: System.currentTimeMillis())
    fun setSelectedDate(date: Date) { _selectedDate.value = date.time }

    private fun saveCreationDate(plantId: String, date: Long) {
        getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE).edit().putLong("dob_$plantId", date).apply()
    }
    private fun getOrInitCreationDate(plantId: String, fallbackDate: Long): Long {
        val savedDate = getApplication<Application>().getSharedPreferences("plant_birthdays", Context.MODE_PRIVATE).getLong("dob_$plantId", 0L)
        return if (savedDate != 0L) savedDate else fallbackDate
    }
    suspend fun checkPlantExists(speciesId: Int): Boolean = (allUserPlants.value ?: emptyList()).any { it.plantId == speciesId }
    suspend fun getPlantCount(speciesId: Int): Int = (allUserPlants.value ?: emptyList()).count { it.plantId == speciesId }

    // --- TIMELINE (ĐÃ KHÔI PHỤC FULL CODE) ---
    fun getPlantTimeline(userPlantId: String): LiveData<List<TimelineItem>> = androidx.lifecycle.liveData(Dispatchers.Default) {
        val rawEvents = ArrayList<TimelineItem>()

        // 1. Thêm cây
        val plants = allUserPlants.value ?: emptyList()
        val plant = plants.find { it.id == userPlantId }
        if (plant != null) {
            val creationDate = getOrInitCreationDate(userPlantId, plant.dateAdded)
            rawEvents.add(TimelineItem.PlantAdded(creationDate, plant.nickname, plant.imagePath))
        }

        // 2. Chăm sóc
        val allTasks = _allTasksSource.value ?: emptyList()
        val plantTasks = allTasks.filter { it.plant.id == userPlantId }
        for (item in plantTasks) {
            val task = item.task
            val historyDates = repository.getHistoryDatesForTask(task.id)
            for (date in historyDates) {
                rawEvents.add(TimelineItem.CareEvent(date, task.type))
            }
        }

        // 3. Bệnh tật
        val allLogs = repository.diseaseLogs.value ?: emptyList()
        val infectedLogs = allLogs.filter { it.plantId == userPlantId }
        for (log in infectedLogs) {
            rawEvents.add(TimelineItem.DiseaseEvent(log.timestamp, log.diseaseName))
        }

        // 4. Sắp xếp
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

    fun markPlantsAsInfected(plants: List<UserPlant>, diseaseName: String) = viewModelScope.launch {
        for (plant in plants) {
            repository.insertDiseaseLog(DiseaseLog(plantId = plant.id, diseaseName = diseaseName, timestamp = System.currentTimeMillis()))
        }
    }
}