package com.example.leafyapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.data.model.UserPlant

@Dao
interface GardenDao {
    // --- PHẦN CÂY (CŨ) ---
    @Query("SELECT * FROM user_plants ORDER BY id DESC")
    fun getAllUserPlants(): LiveData<List<UserPlant>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPlant(plant: UserPlant)

    @Delete
    suspend fun deleteUserPlant(plant: UserPlant)

    // --- PHẦN TASK (MỚI) ---

    // Thêm Task mới
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: CareTask)

    // Lấy danh sách Task trong một khoảng thời gian (ví dụ: từ sáng đến tối của 1 ngày)
    // Dùng @Transaction vì truy vấn nhiều bảng
    @Transaction
    @Query("SELECT * FROM care_tasks WHERE nextDueDate >= :startTime AND nextDueDate <= :endTime ORDER BY nextDueDate ASC")
    fun getTasksForDate(startTime: Long, endTime: Long): LiveData<List<TaskWithPlant>>
}