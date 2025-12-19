package com.example.leafyapp.data.model

import java.io.Serializable
import androidx.annotation.DrawableRes
import com.example.leafyapp.R


data class UserPlant(
    var id: String = "",
    var userId: String = "", // Giữ lại nếu bạn đang dùng để query
    var ownerId: String = "", // [BỔ SUNG] Để Server lấy tên hiển thị Quý Phạm

    val plantId: Int = 0,
    var nickname: String = "",
    val imagePath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    var gardenId: String? = null
) : Serializable {
    constructor() : this("", "", "", 0, "", null)
}

data class CareTask(
    var id: String = "",
    var userPlantId: String = "",
    val plantName: String? = null, // Giúp thông báo hiện tên cây cụ thể

    val type: TaskType = TaskType.WATER,
    val frequencyDays: Int = 1,
    val timeHour: Int = 8,
    val timeMinute: Int = 0,

    val startDate: Long = System.currentTimeMillis(),
    val nextDueDate: Long = System.currentTimeMillis(),

    val isAutoReminder: Boolean = true,
    var lastCompletedDate: Long? = null,

    val gardenId: String? = null, // null = Cá nhân, ID = Family
    val ownerId: String = ""      // Dùng để lọc thông báo và báo trễ

) : Serializable {
    constructor() : this(
        "", "", null, TaskType.WATER, 1, 8, 0,
        System.currentTimeMillis(), System.currentTimeMillis(),
        true, null, null, ""
    )
}
enum class TaskType(
    val displayName: String,
    @DrawableRes val iconResId: Int // Chỉ cần Icon, không cần Color nữa
) {
    // Thay thế bằng tên file icon thực tế của bạn
    WATER("Water", R.drawable.ic_water_drop), // Ví dụ icon giọt nước
    FERTILIZER("Fertilizer", R.drawable.ic_fertilizer),
    ROTATE("Rotate", R.drawable.ic_rotate),
    MIST("Misting", R.drawable.ic_mist),
    CUT("Cutting", R.drawable.ic_cut),


    OTHER("Other", R.drawable.leaf_solid_full);

}