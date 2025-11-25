package com.example.leafyapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_history",
    foreignKeys = [ForeignKey(
        entity = CareTask::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TaskHistory(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val taskId: Long,
    val completedDate: Long // Ngày đã hoàn thành (Millis)
)