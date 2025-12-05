package com.example.leafyapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.leafyapp.data.dao.GardenDao
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskHistory // Import mới
import com.example.leafyapp.data.model.UserPlant

// Tăng version lên 3
@Database(entities = [UserPlant::class, CareTask::class, TaskHistory::class], version = 10   , exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gardenDao(): GardenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leafy_user_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}