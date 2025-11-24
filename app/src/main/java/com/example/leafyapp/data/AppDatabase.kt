package com.example.leafyapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.leafyapp.data.dao.GardenDao
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.UserPlant

// Khai báo Database này chứa 2 bảng: UserPlant và CareTask
// version = 1: Phiên bản đầu tiên của DB
@Database(entities = [UserPlant::class, CareTask::class], version = 2, exportSchema = false) // Tăng lên 2
abstract class AppDatabase : RoomDatabase() {

    // Khai báo cho phép GardenDao hoạt động
    abstract fun gardenDao(): GardenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Nếu instance đã có thì trả về, nếu chưa thì tạo mới (Singleton Pattern)
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leafy_user_database" // Tên file database sẽ lưu trong máy điện thoại
                )
                    // .createFromAsset("database/my_plants.db") <--- Nếu bạn muốn dùng lại DB 29 cây cũ thì dùng lệnh này (tạm thời chưa cần)
                    .fallbackToDestructiveMigration() // Nếu sửa cấu trúc bảng thì xóa cũ xây lại để tránh lỗi crash
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}