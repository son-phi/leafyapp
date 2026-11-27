package com.example.leafyapp.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.leafyapp.data.model.CareLog
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.PlantProfile
import com.example.leafyapp.data.model.TaskType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.example.leafyapp.data.model.DiseaseLog
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
// [QUAN TRỌNG] Các import cho Coroutines & Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GardenRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId get() = auth.currentUser?.uid

    // Biến công tắc Vườn (null = Riêng, String = Chung)
    private var currentGardenId: String? = null

    // --- LIVE DATA ---
    private val _userPlants = MutableLiveData<List<UserPlant>>()
    val userPlants: LiveData<List<UserPlant>> = _userPlants

    private val _allTasks = MutableLiveData<List<CareTask>>()
    val allTasksWithPlant = MutableLiveData<List<TaskWithPlant>>()

    private val _taskHistory = MutableLiveData<List<TaskHistory>>()
    val taskHistory: LiveData<List<TaskHistory>> = _taskHistory

    private val _diseaseLogs = MutableLiveData<List<DiseaseLog>>()
    val diseaseLogs: LiveData<List<DiseaseLog>> = _diseaseLogs

    // --- LISTENERS ---
    private var plantsListener: ListenerRegistration? = null
    private var tasksListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null
    private var diseaseListener: ListenerRegistration? = null

    // --- CẤU TRÚC MỚI: users/{uid}/plants/{plantId}/(careSchedules|careLogs) ---
    private val _v2Plants = MutableLiveData<List<PlantProfile>>(emptyList())
    val v2Plants: LiveData<List<PlantProfile>> = _v2Plants
    private var v2PlantsListener: ListenerRegistration? = null
    private val v2ScheduleListeners = mutableMapOf<String, ListenerRegistration>()
    private val v2LogListeners = mutableMapOf<String, ListenerRegistration>()
    private val v2Schedules = mutableMapOf<String, List<CareSchedule>>()
    private val v2Logs = mutableMapOf<String, List<CareLog>>()
    private var v2PlantsLoaded = false

    /** Gọi khi lịch chăm sóc (cấu trúc mới) thay đổi và đã tải đủ: (lịch, tên cây theo plantId). */
    var onV2SchedulesChanged: ((List<CareSchedule>, Map<String, String>) -> Unit)? = null

    init {
        startListening()
    }

    // --- CHUYỂN ĐỔI CHẾ ĐỘ ---
    fun switchGardenMode(gardenId: String?) {
        if (currentGardenId == gardenId) return
        currentGardenId = gardenId
        stopListening()
        // _userPlants.value = emptyList() // Có thể bỏ comment nếu muốn xóa trắng khi chuyển
        startListening()
    }

    // --- HÀM LẤY ĐƯỜNG DẪN (PATH) ---
    // Vườn cá nhân dùng cấu trúc mới users/{uid}/plants/{plantId}/(careSchedules|careLogs|diagnoses).
    // Các collection cũ user_plants / care_tasks / task_history / disease_logs đã xóa, chỉ còn ở vườn gia đình (gardens).
    private fun familyGardenId(): String =
        currentGardenId ?: throw IllegalStateException("Vườn cá nhân dùng users/{uid}/plants (cấu trúc mới)")

    private fun getPlantsRef(): CollectionReference =
        db.collection("gardens").document(familyGardenId()).collection("plants")

    private fun getTasksRef(): CollectionReference =
        db.collection("gardens").document(familyGardenId()).collection("tasks")

    private fun getDiseaseLogsRef(): CollectionReference =
        db.collection("gardens").document(familyGardenId()).collection("disease_logs")

    // --- START LISTENING ---
    private fun startListening() {
        val uid = currentUserId ?: return

        if (currentGardenId == null) {
            // Vườn cá nhân: không còn dữ liệu cũ
            _userPlants.value = emptyList()
            _allTasks.value = emptyList()
            _taskHistory.value = emptyList()
            _diseaseLogs.value = emptyList()
        } else {
        // 1. Plants
        plantsListener = getPlantsRef().addSnapshotListener { snap, _ ->
            val list = snap?.toObjects(UserPlant::class.java) ?: emptyList()
            list.forEachIndexed { i, item -> item.id = snap!!.documents[i].id }
            _userPlants.value = list
            combineTasksAndPlants()
        }

        // 2. Tasks
        tasksListener = getTasksRef().addSnapshotListener { snap, _ ->
            val list = snap?.toObjects(CareTask::class.java) ?: emptyList()
            list.forEachIndexed { i, item -> item.id = snap!!.documents[i].id }
            _allTasks.value = list
            combineTasksAndPlants()
        }

        // 3. History (Luôn của User)
        historyListener = db.collection("users").document(uid).collection("task_history")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(TaskHistory::class.java) ?: emptyList()
                _taskHistory.value = list
            }

        // 4. Disease Logs (Tổng hợp để hiển thị realtime nếu cần)
        diseaseListener = getDiseaseLogsRef().addSnapshotListener { snap, _ ->
            val list = snap?.toObjects(DiseaseLog::class.java) ?: emptyList()
            list.forEachIndexed { i, item -> item.id = snap!!.documents[i].id }
            _diseaseLogs.value = list
        }
        }

        // 5. Cây theo cấu trúc mới (chỉ ở vườn cá nhân)
        if (currentGardenId == null) {
            v2PlantsListener = v2PlantsRef(uid).addSnapshotListener { snap, _ ->
                val plants = snap?.toObjects(PlantProfile::class.java) ?: emptyList()
                _v2Plants.value = plants
                v2PlantsLoaded = true
                syncV2ChildListeners(uid, plants.map { it.id }.toSet())
                combineTasksAndPlants()
            }
        }
    }

    private fun v2PlantsRef(uid: String) = db.collection("users").document(uid).collection("plants")

    /** Gắn/gỡ listener careSchedules, careLogs theo từng cây mới. */
    private fun syncV2ChildListeners(uid: String, plantIds: Set<String>) {
        (v2ScheduleListeners.keys - plantIds).forEach { id ->
            v2ScheduleListeners.remove(id)?.remove()
            v2LogListeners.remove(id)?.remove()
            v2Schedules.remove(id)
            v2Logs.remove(id)
        }
        (plantIds - v2ScheduleListeners.keys).forEach { id ->
            val plantRef = v2PlantsRef(uid).document(id)
            v2ScheduleListeners[id] = plantRef.collection("careSchedules").addSnapshotListener { snap, _ ->
                v2Schedules[id] = snap?.toObjects(CareSchedule::class.java) ?: emptyList()
                fillFromLogs(id)
                combineTasksAndPlants()
            }
            v2LogListeners[id] = plantRef.collection("careLogs").addSnapshotListener { snap, _ ->
                v2Logs[id] = snap?.toObjects(CareLog::class.java) ?: emptyList()
                fillFromLogs(id)
                combineTasksAndPlants()
            }
        }
    }

    /** Chỉ báo khi đã có dữ liệu thật của mọi cây, tránh hủy nhầm báo thức lúc đang tải. */
    private fun notifyV2Schedules() {
        val plants = _v2Plants.value.orEmpty()
        if (!v2PlantsLoaded || !v2Schedules.keys.containsAll(plants.map { it.id })) return
        onV2SchedulesChanged?.invoke(v2Schedules.values.flatten(), plants.associate { it.id to it.name })
    }

    /** completedCount / lastDoneAt của lịch suy ra từ careLogs (không lưu trong careSchedules). */
    private fun fillFromLogs(plantId: String) {
        val logs = v2Logs[plantId].orEmpty()
        v2Schedules[plantId].orEmpty().forEach { s ->
            val mine = logs.filter { it.careScheduleId == s.id }
            s.completedCount = mine.size
            s.lastDoneAt = mine.mapNotNull { it.performedAt }.maxByOrNull { it.toDate().time }
        }
    }

    fun isV2Plant(plantId: String): Boolean = _v2Plants.value.orEmpty().any { it.id == plantId }
    fun isV2Task(taskId: String): Boolean = v2Schedules.values.any { list -> list.any { it.id == taskId } }
    private fun findV2Schedule(taskId: String): CareSchedule? =
        v2Schedules.values.asSequence().flatten().firstOrNull { it.id == taskId }

    companion object {
        /** Hồ sơ cây mới → UserPlant để màn vườn cũ hiển thị được. */
        fun toUserPlant(p: PlantProfile, legacyCatalogId: Int = 0, fallbackImage: String? = null,
                        speciesName: String? = null, scientificName: String? = null): UserPlant =
            UserPlant(
                id = p.id,
                userId = p.userId,
                ownerId = p.userId,
                plantId = legacyCatalogId,
                nickname = p.name,
                imagePath = p.imageUrl ?: fallbackImage,
                dateAdded = (p.plantedDate ?: p.createdAt)?.toDate()?.time ?: System.currentTimeMillis(),
                gardenId = null
            ).apply {
                speciesId = p.speciesId
                this.speciesName = speciesName
                this.scientificName = scientificName
                healthStatus = p.healthStatus
            }

        fun toLegacyType(type: CareTaskType, title: String): TaskType = when (type) {
            CareTaskType.WATERING -> TaskType.WATER
            CareTaskType.FERTILIZING -> TaskType.FERTILIZER
            CareTaskType.PRUNING -> TaskType.CUT
            CareTaskType.SPRAYING -> TaskType.MIST
            CareTaskType.HEALTH_CHECK -> TaskType.OTHER
            CareTaskType.OTHER -> if (title.contains("Xoay", ignoreCase = true)) TaskType.ROTATE else TaskType.OTHER
        }

        fun fromLegacyType(type: TaskType): CareTaskType = CareTaskType.parse(type.name)

        private fun parseTime(timeOfDay: String): Pair<Int, Int> {
            val parts = timeOfDay.split(":")
            return (parts.getOrNull(0)?.toIntOrNull() ?: 8) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        /** Lịch chăm sóc mới → CareTask để màn Công việc cũ hiển thị và thao tác. */
        fun toCareTask(s: CareSchedule): CareTask {
            val (h, m) = parseTime(s.timeOfDay)
            return CareTask(
                id = s.id,
                userPlantId = s.plantId,
                plantName = s.title,
                type = toLegacyType(CareTaskType.parse(s.taskType), s.title),
                frequencyDays = s.repeatIntervalDays.coerceAtLeast(1),
                timeHour = h,
                timeMinute = m,
                startDate = s.startAt?.toDate()?.time ?: System.currentTimeMillis(),
                nextDueDate = (s.nextDueAt ?: s.startAt)?.toDate()?.time ?: System.currentTimeMillis(),
                isAutoReminder = s.active,
                lastCompletedDate = s.lastDoneAt?.toDate()?.time,
                gardenId = null,
                ownerId = ""
            )
        }

        fun nextDueAfter(completedAt: Long, intervalDays: Int, timeOfDay: String): java.util.Date {
            val (h, m) = parseTime(timeOfDay)
            return java.util.Calendar.getInstance().apply {
                timeInMillis = completedAt
                add(java.util.Calendar.DAY_OF_YEAR, intervalDays.coerceAtLeast(1))
                set(java.util.Calendar.HOUR_OF_DAY, h)
                set(java.util.Calendar.MINUTE, m)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time
        }
    }

    private fun stopListening() {
        plantsListener?.remove()
        tasksListener?.remove()
        historyListener?.remove()
        diseaseListener?.remove()
        v2PlantsListener?.remove()
        v2PlantsListener = null
        v2ScheduleListeners.values.forEach { it.remove() }
        v2ScheduleListeners.clear()
        v2LogListeners.values.forEach { it.remove() }
        v2LogListeners.clear()
        v2Schedules.clear()
        v2Logs.clear()
        v2PlantsLoaded = false
        _v2Plants.value = emptyList()
    }

    private fun combineTasksAndPlants() {
        val v2PlantList = _v2Plants.value.orEmpty().map { toUserPlant(it) }
        val v2Ids = v2PlantList.map { it.id }.toSet()
        val plants = (_userPlants.value ?: emptyList()).filter { it.id !in v2Ids } + v2PlantList
        val v2Tasks = v2Schedules.values.flatten().filter { it.active }.map { toCareTask(it) }
        val v2TaskIds = v2Tasks.map { it.id }.toSet()
        // care_tasks cũ đã chuyển sang careSchedules (cùng id) thì bỏ bản cũ
        val tasks = (_allTasks.value ?: emptyList()).filter { it.id !in v2TaskIds } + v2Tasks
        notifyV2Schedules()
        val combinedList = ArrayList<TaskWithPlant>()
        for (task in tasks) {
            val plant = plants.find { it.id == task.userPlantId }
            if (plant != null) combinedList.add(TaskWithPlant(task, plant))
        }
        allTasksWithPlant.value = combinedList
    }

    // --- CRUD FUNCTIONS ---

    fun insertUserPlant(plant: UserPlant) {
        val ref = getPlantsRef().document()
        plant.id = ref.id
        plant.userId = currentUserId ?: ""
        ref.set(plant)
    }
    // Ghi lên Firestore và CHỜ kết quả (ném lỗi nếu bị từ chối, vd: Security Rules)
    suspend fun insertUserPlantAwait(plant: UserPlant) {
        val ref = getPlantsRef().document()
        plant.id = ref.id
        plant.userId = currentUserId ?: ""
        ref.set(plant).await()
    }
    fun updateUserPlant(plant: UserPlant) { getPlantsRef().document(plant.id).set(plant) }
    fun deleteUserPlant(plant: UserPlant) { getPlantsRef().document(plant.id).delete() }

    fun insertTask(task: CareTask) {
        val ref = getTasksRef().document()
        task.id = ref.id
        ref.set(task)
    }
    fun updateTask(task: CareTask) { getTasksRef().document(task.id).set(task) }
    fun deleteTask(task: CareTask) { getTasksRef().document(task.id).delete() }

    fun insertHistory(history: TaskHistory) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("task_history").document().set(history)
    }
    fun getHistoryDatesForTask(taskId: String): List<Long> {
        val allHistory = _taskHistory.value ?: emptyList()
        val legacy = allHistory.filter { it.taskId == taskId }.map { it.completedDate }
        val v2 = v2Logs.values.flatten().filter { it.careScheduleId == taskId }.mapNotNull { it.performedAt?.toDate()?.time }
        return legacy + v2
    }

    // --- THAO TÁC TRÊN CẤU TRÚC MỚI ---

    /** Đánh dấu hoàn thành: dùng chung logic của Hồ sơ cây trồng (careLogs + hạn kế tiếp). */
    suspend fun completeV2Task(taskId: String, completedAt: Long) {
        val s = findV2Schedule(taskId) ?: return
        PlantDetailRepository.complete(s.plantId, s, completedAt)
    }

    suspend fun updateV2Task(task: CareTask) {
        val uid = currentUserId ?: return
        val s = findV2Schedule(task.id) ?: return
        val start = java.util.Calendar.getInstance().apply {
            timeInMillis = task.startDate; set(java.util.Calendar.HOUR_OF_DAY, task.timeHour); set(java.util.Calendar.MINUTE, task.timeMinute)
        }.time
        v2PlantsRef(uid).document(s.plantId).collection("careSchedules").document(s.id).update(mapOf(
            "taskType" to fromLegacyType(task.type).name,
            "repeatIntervalDays" to task.frequencyDays.coerceAtLeast(1),
            "startAt" to Timestamp(start),
            "nextDueAt" to Timestamp(java.util.Date(task.nextDueDate)),
            "active" to task.isAutoReminder
        )).await()
    }

    suspend fun deleteV2Task(taskId: String) {
        val uid = currentUserId ?: return
        val s = findV2Schedule(taskId) ?: return
        v2PlantsRef(uid).document(s.plantId).collection("careSchedules").document(s.id).delete().await()
    }

    /** Tạo lịch mới cho cây thuộc cấu trúc mới (mở từ form tạo công việc cũ). */
    suspend fun insertV2Task(task: CareTask) {
        val start = java.util.Calendar.getInstance().apply {
            timeInMillis = task.startDate; set(java.util.Calendar.HOUR_OF_DAY, task.timeHour); set(java.util.Calendar.MINUTE, task.timeMinute)
        }.time
        PlantDetailRepository.saveSchedule(task.userPlantId, null, fromLegacyType(task.type), vietnameseTitle(task.type),
            task.frequencyDays.coerceAtLeast(1), "%02d:%02d".format(task.timeHour, task.timeMinute), start, "")
    }

    private fun vietnameseTitle(t: TaskType) = when (t) {
        TaskType.WATER -> "Tưới nước"; TaskType.FERTILIZER -> "Bón phân"; TaskType.ROTATE -> "Xoay chậu"
        TaskType.MIST -> "Phun sương"; TaskType.CUT -> "Cắt tỉa"; else -> "Công việc khác"
    }

    suspend fun renameV2Plant(plantId: String, name: String) {
        val uid = currentUserId ?: return
        v2PlantsRef(uid).document(plantId).update("name", name).await()
    }

    /** Xóa cây mới: Firestore không tự xóa collection con nên xóa từng tài liệu bằng batch (8.6). */
    suspend fun deleteV2Plant(plantId: String) {
        val uid = currentUserId ?: return
        val plantRef = v2PlantsRef(uid).document(plantId)
        for (sub in listOf("careSchedules", "careLogs", "diagnoses")) {
            val docs = plantRef.collection(sub).get().await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        plantRef.delete().await()
        try {
            com.google.firebase.storage.FirebaseStorage.getInstance().reference
                .child("users/$uid/plants/$plantId/cover.jpg").delete().await()
        } catch (_: Exception) {
            // Không có ảnh hoặc chưa bật Storage: bỏ qua
        }
    }

    // --- [DISEASE LOGIC] ---

    // 1. Insert Log (Lưu đúng Vườn)
    fun insertDiseaseLog(log: DiseaseLog, gardenId: String?) {
        // Vườn cá nhân: chẩn đoán lưu ở users/{uid}/plants/{plantId}/diagnoses (DiagnosisRepository)
        if (gardenId == null) return
        val ref = db.collection("gardens").document(gardenId).collection("disease_logs")

        val docRef = ref.document()
        log.id = docRef.id
        docRef.set(log)
    }

    // 2. Get Log One-Shot (Lấy 1 lần - Dùng cho các tác vụ không cần realtime)
    suspend fun getDiseaseLogsForPlant(plantId: String, gardenId: String?): List<DiseaseLog> {
        if (gardenId == null) return emptyList()   // cá nhân: xem diagnoses trong Hồ sơ cây trồng
        return try {
            val ref = db.collection("gardens").document(gardenId).collection("disease_logs")
            val snapshot = ref.whereEqualTo("plantId", plantId).get().await()
            snapshot.toObjects(DiseaseLog::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 3. Listen Log Real-time (Dùng cho Timeline để tự cập nhật)
    fun listenToDiseaseLogs(plantId: String, gardenId: String?): Flow<List<DiseaseLog>> = callbackFlow {
        if (gardenId == null) {   // cá nhân: xem diagnoses trong Hồ sơ cây trồng
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.collection("gardens").document(gardenId).collection("disease_logs")

        // Lắng nghe thay đổi
        val subscription = ref.whereEqualTo("plantId", plantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val logs = snapshot.toObjects(DiseaseLog::class.java)
                    trySend(logs)
                }
            }

        awaitClose { subscription.remove() }
    }


}