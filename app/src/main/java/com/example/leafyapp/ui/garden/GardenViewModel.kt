package com.example.leafyapp.ui.garden

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.leafyapp.data.model.*
import com.example.leafyapp.data.repository.GardenRepository
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
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.lifecycle.Observer

data class PlantTasksGroup(val plant: UserPlant, val tasks: List<CareTask>)

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GardenRepository()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // 1. KHO TỔNG: Chứa tất cả cây (Personal + Family)
    val masterPlantList = MutableLiveData<List<UserPlant>>()

    // 2. KHO HIỂN THỊ: Lọc theo chế độ đang chọn
    val allUserPlants = MediatorLiveData<List<UserPlant>>()

    private val _allTasksSource = repository.allTasksWithPlant
    private val _selectedDate = MutableLiveData<Long>(System.currentTimeMillis())
    val allDiseaseLogs: LiveData<List<DiseaseLog>> = repository.diseaseLogs

    private val _currentGarden = MutableLiveData<Garden?>(null)
    val currentGarden: LiveData<Garden?> = _currentGarden

    private val repoPlantsObserver = Observer<List<UserPlant>> {
        loadCombinedPlants()
    }

    init {
        repository.userPlants.observeForever(repoPlantsObserver)

        // Đăng ký kênh thông báo cá nhân ngay khi khởi tạo ViewModel
        subscribeToPersonalTopic()

        // Logic lọc cây theo chế độ xem
        val filterLogic = {
            val all = masterPlantList.value ?: emptyList()
            val garden = _currentGarden.value
            val filtered = if (garden == null) {
                all.filter { it.gardenId == null }
            } else {
                all.filter { it.gardenId == garden.id }
            }
            allUserPlants.value = filtered
        }

        allUserPlants.addSource(masterPlantList) { filterLogic() }
        allUserPlants.addSource(_currentGarden) { filterLogic() }

        loadCombinedPlants()
    }

    override fun onCleared() {
        super.onCleared()
        repository.userPlants.removeObserver(repoPlantsObserver)
    }

    // =========================================================================
    // LOAD DỮ LIỆU TỪ FIREBASE
    // =========================================================================
    fun loadCombinedPlants() = viewModelScope.launch(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@launch
        val combinedList = ArrayList<UserPlant>()
        try {
            // Lấy cây cá nhân
            val pQuery = db.collection("users").document(uid).collection("user_plants").get().await()
            val pList = pQuery.toObjects(UserPlant::class.java)
            pList.forEachIndexed { i, p ->
                p.id = pQuery.documents[i].id
                p.gardenId = null
            }
            combinedList.addAll(pList)

            // Lấy cây gia đình
            val gQuery = db.collection("gardens").whereArrayContains("members", uid).get().await()
            for (doc in gQuery.documents) {
                val gId = doc.id
                val subQuery = db.collection("gardens").document(gId).collection("plants").get().await()
                val fList = subQuery.toObjects(UserPlant::class.java)
                fList.forEachIndexed { i, p ->
                    p.id = subQuery.documents[i].id
                    p.gardenId = gId
                }
                combinedList.addAll(fList)
            }

            withContext(Dispatchers.Main) {
                masterPlantList.value = combinedList
            }
        } catch (e: Exception) {
            Log.e("GardenViewModel", "Error loading plants: ${e.message}")
        }
    }

    // =========================================================================
    // TASK OPERATIONS (Đã Server-hóa hoàn toàn)
    // =========================================================================
    fun insertTask(task: CareTask) = viewModelScope.launch {
        val currentUid = auth.currentUser?.uid ?: ""
        val currentGardenId = _currentGarden.value?.id

        // Gán ownerId để Robot Server biết gửi thông báo cho ai
        val taskToSave = task.copy(
            gardenId = currentGardenId,
            ownerId = currentUid,
            id = UUID.randomUUID().toString() // Đảm bảo có ID duy nhất
        )

        repository.insertTask(taskToSave)
        // Không còn scheduleLocalAlarm ở đây nữa
    }

    fun updateTask(task: CareTask) = viewModelScope.launch {
        repository.updateTask(task)
        // Không còn cancel/scheduleLocalAlarm
    }

    fun deleteTask(task: CareTask) = viewModelScope.launch {
        repository.deleteTask(task)
    }

    fun markTaskAsCompleted(task: CareTask, completedDate: Long) = viewModelScope.launch {
        repository.insertHistory(TaskHistory(taskId = task.id, completedDate = completedDate))
        val oneDayMillis = 24L * 60 * 60 * 1000
        val newNextDue = completedDate + (task.frequencyDays * oneDayMillis)
        repository.updateTask(task.copy(nextDueDate = newNextDue, lastCompletedDate = completedDate))
    }

    // =========================================================================
    // GARDEN MANAGEMENT & TOPIC SUBSCRIPTION
    // =========================================================================

    fun setGardenMode(garden: Garden?) {
        // [SỬA ĐỔI]: Không unsubscribe vườn cũ để vẫn nhận được thông báo chạy ngầm
        _currentGarden.value = garden
        repository.switchGardenMode(garden?.id)
        if (garden != null) subscribeToGardenTopic(garden.id)
    }

    private fun subscribeToPersonalTopic() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().subscribeToTopic("user_$uid")
            .addOnCompleteListener { Log.d("FCM", "Subscribed to personal topic user_$uid") }
    }

    private fun subscribeToGardenTopic(id: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("garden_$id")
            .addOnCompleteListener { Log.d("FCM", "Subscribed to garden topic garden_$id") }
    }

    private fun unsubscribeFromGardenTopic(id: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic("garden_$id")
            .addOnCompleteListener { Log.d("FCM", "Unsubscribed from garden topic garden_$id") }
    }

    fun joinGardenByCode(code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Kiểm tra xem đã trong vườn nào chưa
                val check = db.collection("gardens").whereArrayContains("members", uid).get().await()
                if (!check.isEmpty) {
                    withContext(Dispatchers.Main) { onError("Bạn đã tham gia một vườn rồi.") }
                    return@launch
                }

                val query = db.collection("gardens").whereEqualTo("inviteCode", code).get().await()
                if (query.isEmpty) {
                    withContext(Dispatchers.Main) { onError("Mã mời không chính xác.") }
                    return@launch
                }

                val doc = query.documents[0]
                db.collection("gardens").document(doc.id).update("members", FieldValue.arrayUnion(uid)).await()

                withContext(Dispatchers.Main) {
                    val newGarden = doc.toObject(Garden::class.java)?.apply { id = doc.id }
                    setGardenMode(newGarden)
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Lỗi không xác định") }
            }
        }
    }

    fun leaveCurrentGarden(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val garden = _currentGarden.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("gardens").document(garden.id).update("members", FieldValue.arrayRemove(uid)).await()
                // [SỬA ĐỔI]: Chỉ thực hiện hủy đăng ký khi thực sự rời vườn
                unsubscribeFromGardenTopic(garden.id)
                withContext(Dispatchers.Main) {
                    setGardenMode(null)
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Lỗi khi rời vườn") }
            }
        }
    }

    // =========================================================================
    // PLANT OPERATIONS & UTILS (Giữ nguyên)
    // =========================================================================
    fun insert(plant: UserPlant) = viewModelScope.launch {
        val currentGid = _currentGarden.value?.id
        val plantToSave = plant.copy(gardenId = currentGid)
        repository.insertUserPlant(plantToSave)
        saveCreationDate(plantToSave.id, System.currentTimeMillis())
        loadCombinedPlants()
    }

    fun delete(plant: UserPlant) = viewModelScope.launch {
        repository.deleteUserPlant(plant)
        loadCombinedPlants()
    }

    fun updatePlantName(plant: UserPlant, newName: String) = viewModelScope.launch {
        repository.updateUserPlant(plant.copy(nickname = newName))
        loadCombinedPlants()
    }

    fun markPlantsAsInfected(plants: List<UserPlant>, diseaseName: String) = viewModelScope.launch {
        for (plant in plants) {
            val log = DiseaseLog(plantId = plant.id, diseaseName = diseaseName, timestamp = System.currentTimeMillis())
            repository.insertDiseaseLog(log, plant.gardenId)
        }
    }

    // --- TIMELINE LOGIC ---
    fun getPlantTimeline(userPlantId: String): LiveData<List<TimelineItem>> {
        return masterPlantList.switchMap { plants ->
            val plant = plants.find { it.id == userPlantId }
            val diseaseFlow = if (plant != null) {
                repository.listenToDiseaseLogs(userPlantId, plant.gardenId)
            } else flowOf(emptyList())

            diseaseFlow.map { diseaseLogs ->
                val rawEvents = ArrayList<TimelineItem>()
                if (plant != null) {
                    rawEvents.add(TimelineItem.PlantAdded(
                        getOrInitCreationDate(userPlantId, plant.dateAdded),
                        plant.nickname, plant.imagePath
                    ))
                }
                for (log in diseaseLogs) {
                    rawEvents.add(TimelineItem.DiseaseEvent(log.timestamp, log.diseaseName))
                }
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

    // --- TASK GROUPING FOR UI ---
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

    // --- HELPER UTILS ---
    private fun getStartOfDay(time: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return c.timeInMillis
    }
    fun getSelectedDayStart() = getStartOfDay(_selectedDate.value ?: System.currentTimeMillis())
    fun setSelectedDate(date: Date) { _selectedDate.value = date.time }
    private fun saveCreationDate(id: String, date: Long) = getApplication<Application>().getSharedPreferences("plant_birthdays", 0).edit().putLong("dob_$id", date).apply()
    private fun getOrInitCreationDate(id: String, fb: Long) = getApplication<Application>().getSharedPreferences("plant_birthdays", 0).getLong("dob_$id", 0L).let { if (it != 0L) it else fb }
    suspend fun getPlantCount(sid: Int) = (allUserPlants.value ?: emptyList()).count { it.plantId == sid }
}