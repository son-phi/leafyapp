package com.example.leafyapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "user_plants")
data class UserPlant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Int,
    val nickname: String,
    val imagePath: String?,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "care_tasks",
    foreignKeys = [ForeignKey(
        entity = UserPlant::class,
        parentColumns = ["id"],
        childColumns = ["userPlantId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CareTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userPlantId: Long,
    val type: TaskType,
    val frequencyDays: Int,  // 1 = mỗi ngày, 2 = 2 ngày/lần...
    val timeHour: Int,
    val timeMinute: Int,
    val nextDueDate: Long,
    val isAutoReminder: Boolean = true // MỚI: Bật tắt thông báo
)

enum class TaskType(val displayName: String) {
    WATER("Watering"),
    MIST("Misting"),
    FERTILIZER("Fertilizing"),
    ROTATE("Rotating"),
    CUT("Cutting")
}