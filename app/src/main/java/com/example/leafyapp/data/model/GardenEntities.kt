package com.example.leafyapp.data.model

import java.io.Serializable
import androidx.annotation.DrawableRes
import com.example.leafyapp.R


data class UserPlant(
    var id: String = "",
    var userId: String = "",

    val plantId: Int = 0,
    var nickname: String = "", // var để sửa tên (Family)
    val imagePath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    var gardenId: String? = null
) : Serializable {
    constructor() : this("", "", 0, "", null)
}

data class CareTask(
    var id: String = "",
    var userPlantId: String = "",

    // [QUAN TRỌNG 1] Thêm tên cây để hiển thị trên thông báo cho đẹp
    val plantName: String? = null,

    val type: TaskType = TaskType.WATER,
    val frequencyDays: Int = 1,
    val timeHour: Int = 8,
    val timeMinute: Int = 0,

    val startDate: Long = System.currentTimeMillis(),
    val nextDueDate: Long = System.currentTimeMillis(),

    val isAutoReminder: Boolean = true,
    val lastCompletedDate: Long? = null,

    // [QUAN TRỌNG 2] ID Vườn (null = Cá nhân, có giá trị = Family)
    val gardenId: String? = null,

    // [QUAN TRỌNG 3] ID Người tạo (Để xử lý logic báo thức so le 30p)
    val ownerId: String = ""

) : Serializable {
    // Constructor rỗng cho Firebase (cập nhật đủ trường)
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