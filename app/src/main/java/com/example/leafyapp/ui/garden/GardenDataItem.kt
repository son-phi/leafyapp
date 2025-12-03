package com.example.leafyapp.ui.garden

import com.example.leafyapp.data.model.UserPlant

// Sealed class để định nghĩa các loại item trong RecyclerView
sealed class GardenDataItem {
    // Header của nhóm (VD: "Cây Hoa Hồng - 3 cây")
    data class GroupHeader(
        val plantId: Int,
        val name: String,
        val image: String?,
        val count: Int,
        var isExpanded: Boolean = false
    ) : GardenDataItem()

    // Item cây con (VD: "Cây Hoa Hồng #1")
    data class PlantItem(val userPlant: UserPlant) : GardenDataItem()
}