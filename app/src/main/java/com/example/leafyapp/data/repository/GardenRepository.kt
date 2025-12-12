package com.example.leafyapp.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.leafyapp.data.model.CareTask
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
    private fun getPlantsRef(): CollectionReference {
        val uid = currentUserId ?: throw Exception("No User")
        return if (currentGardenId == null) {
            db.collection("users").document(uid).collection("user_plants")
        } else {
            db.collection("gardens").document(currentGardenId!!).collection("plants")
        }
    }

    private fun getTasksRef(): CollectionReference {
        val uid = currentUserId ?: throw Exception("No User")
        return if (currentGardenId == null) {
            db.collection("users").document(uid).collection("care_tasks")
        } else {
            db.collection("gardens").document(currentGardenId!!).collection("tasks")
        }
    }

    private fun getDiseaseLogsRef(): CollectionReference {
        val uid = currentUserId ?: throw Exception("No User")
        return if (currentGardenId == null) {
            db.collection("users").document(uid).collection("disease_logs")
        } else {
            db.collection("gardens").document(currentGardenId!!).collection("disease_logs")
        }
    }

    // --- START LISTENING ---
    private fun startListening() {
        val uid = currentUserId ?: return

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

    private fun stopListening() {
        plantsListener?.remove()
        tasksListener?.remove()
        historyListener?.remove()
        diseaseListener?.remove()
    }

    private fun combineTasksAndPlants() {
        val plants = _userPlants.value ?: return
        val tasks = _allTasks.value ?: return
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
        return allHistory.filter { it.taskId == taskId }.map { it.completedDate }
    }

    // --- [DISEASE LOGIC] ---

    // 1. Insert Log (Lưu đúng Vườn)
    fun insertDiseaseLog(log: DiseaseLog, gardenId: String?) {
        val uid = currentUserId ?: return
        val ref = if (gardenId != null) {
            db.collection("gardens").document(gardenId).collection("disease_logs")
        } else {
            db.collection("users").document(uid).collection("disease_logs")
        }

        val docRef = ref.document()
        log.id = docRef.id
        docRef.set(log)
    }

    // 2. Get Log One-Shot (Lấy 1 lần - Dùng cho các tác vụ không cần realtime)
    suspend fun getDiseaseLogsForPlant(plantId: String, gardenId: String?): List<DiseaseLog> {
        val uid = currentUserId ?: return emptyList()
        return try {
            val ref = if (gardenId != null) {
                db.collection("gardens").document(gardenId).collection("disease_logs")
            } else {
                db.collection("users").document(uid).collection("disease_logs")
            }
            val snapshot = ref.whereEqualTo("plantId", plantId).get().await()
            snapshot.toObjects(DiseaseLog::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 3. Listen Log Real-time (Dùng cho Timeline để tự cập nhật)
    fun listenToDiseaseLogs(plantId: String, gardenId: String?): Flow<List<DiseaseLog>> = callbackFlow {
        val uid = currentUserId ?: run {
            trySend(emptyList())
            return@callbackFlow
        }

        // Chọn đúng đường dẫn
        val ref = if (gardenId != null) {
            db.collection("gardens").document(gardenId).collection("disease_logs")
        } else {
            db.collection("users").document(uid).collection("disease_logs")
        }

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