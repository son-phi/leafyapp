package com.example.leafyapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskHistory
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant

@Dao
interface GardenDao {
    // --- UserPlant ---
    @Query("SELECT * FROM user_plants ORDER BY id DESC")
    fun getAllUserPlants(): LiveData<List<UserPlant>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPlant(plant: UserPlant)

    @Delete
    suspend fun deleteUserPlant(plant: UserPlant)

    // --- CareTask ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: CareTask): Long

    @Delete
    suspend fun deleteTask(task: CareTask)

    // Lấy TOÀN BỘ Task (kèm thông tin cây) để ViewModel tự lọc
    @Transaction
    @Query("SELECT * FROM care_tasks")
    fun getAllTasks(): LiveData<List<TaskWithPlant>>

    // --- TaskHistory ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TaskHistory)

    // Lấy lịch sử hoàn thành của 1 task cụ thể
    @Query("SELECT completedDate FROM task_history WHERE taskId = :taskId")
    suspend fun getHistoryForTask(taskId: Long): List<Long>

    // HÀM MỚI: Kiểm tra xem plantId đã có trong user_plants chưa
    @Query("SELECT EXISTS(SELECT 1 FROM user_plants WHERE plantId = :plantId LIMIT 1)")
    suspend fun isPlantInGarden(plantId: Int): Boolean
}