package com.example.leafyapp.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class GardenRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId get() = auth.currentUser?.uid

    // LiveData để ViewModel quan sát
    private val _userPlants = MutableLiveData<List<UserPlant>>()
    val userPlants: LiveData<List<UserPlant>> = _userPlants

    private val _allTasks = MutableLiveData<List<CareTask>>()

    // LiveData đặc biệt: Tự động ghép Plant vào Task để ra TaskWithPlant
    val allTasksWithPlant = MutableLiveData<List<TaskWithPlant>>()

    private val _taskHistory = MutableLiveData<List<TaskHistory>>()
    val taskHistory: LiveData<List<TaskHistory>> = _taskHistory

    // Biến lưu listener để hủy khi không cần thiết (tránh rò rỉ bộ nhớ)
    private var plantsListener: ListenerRegistration? = null
    private var tasksListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    // --- 1. KHỞI TẠO LẮNG NGHE DỮ LIỆU (REALTIME) ---
    init {
        startListening()
    }

    fun startListening() {
        val uid = currentUserId ?: return

        // A. Lắng nghe Plants
        plantsListener = db.collection("users").document(uid).collection("user_plants")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(UserPlant::class.java) ?: emptyList()
                // Gán ID từ document key vào object (quan trọng để sửa/xóa)
                list.forEachIndexed { i, item -> item.id = snap!!.documents[i].id }
                _userPlants.value = list
                combineTasksAndPlants() // Dữ liệu cây thay đổi -> Ghép lại task
            }

        // B. Lắng nghe Tasks
        tasksListener = db.collection("users").document(uid).collection("care_tasks")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(CareTask::class.java) ?: emptyList()
                list.forEachIndexed { i, item -> item.id = snap!!.documents[i].id }
                _allTasks.value = list
                combineTasksAndPlants() // Dữ liệu task thay đổi -> Ghép lại
            }

        // C. Lắng nghe History
        historyListener = db.collection("users").document(uid).collection("task_history")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(TaskHistory::class.java) ?: emptyList()
                _taskHistory.value = list
            }
    }

    // --- HÀM GHÉP (JOIN) THỦ CÔNG ---
    // Vì Firebase không join được, ta phải lấy List Plant và List Task rồi tự ghép code
    private fun combineTasksAndPlants() {
        val plants = _userPlants.value ?: return
        val tasks = _allTasks.value ?: return

        val combinedList = ArrayList<TaskWithPlant>()
        for (task in tasks) {
            // Tìm cây tương ứng với task này
            val plant = plants.find { it.id == task.userPlantId }
            if (plant != null) {
                combinedList.add(TaskWithPlant(task, plant))
            }
        }
        allTasksWithPlant.value = combinedList
    }

    // --- 2. CÁC HÀM THÊM / SỬA / XÓA (CRUD) ---

    // Plant
    fun insertUserPlant(plant: UserPlant) {
        val uid = currentUserId ?: return
        val ref = db.collection("users").document(uid).collection("user_plants").document()
        plant.id = ref.id
        plant.userId = uid
        ref.set(plant)
    }

    fun deleteUserPlant(plant: UserPlant) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("user_plants").document(plant.id).delete()
        // Lưu ý: Cần viết thêm Cloud Function hoặc logic xóa cascade các Task con của cây này
    }

    fun updateUserPlant(plant: UserPlant) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("user_plants").document(plant.id).set(plant)
    }

    // Task
    fun insertTask(task: CareTask) {
        val uid = currentUserId ?: return
        val ref = db.collection("users").document(uid).collection("care_tasks").document()
        task.id = ref.id
        ref.set(task)
    }

    fun updateTask(task: CareTask) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("care_tasks").document(task.id).set(task)
    }

    fun deleteTask(task: CareTask) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("care_tasks").document(task.id).delete()
    }

    // History
    fun insertHistory(history: TaskHistory) {
        val uid = currentUserId ?: return
        val ref = db.collection("users").document(uid).collection("task_history").document()
        // history.id = ref.id // Nếu model TaskHistory có trường String id
        ref.set(history)
    }

    // Hàm thay thế cho gardenDao.getHistoryForTask
    // Thay vì query database liên tục, ta lọc từ list đã tải về (nhanh hơn)
    fun getHistoryDatesForTask(taskId: String): List<Long> {
        val allHistory = _taskHistory.value ?: emptyList()
        return allHistory.filter { it.taskId == taskId }.map { it.completedDate }
    }
}