package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.data.model.UserPlant

// Bạn có thể để class này ngay trong file Fragment hoặc file riêng
class SimplePlantAdapter(
    private val plants: List<UserPlant>,
    private val initialSelection: List<UserPlant>,
    private val isMultiSelect: Boolean,
    private val onSelectionChanged: (List<UserPlant>) -> Unit
) : RecyclerView.Adapter<SimplePlantAdapter.ViewHolder>() {

    private val selectedItems = ArrayList<UserPlant>(initialSelection)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(com.example.leafyapp.R.id.tv_name)
        val ivCheck: ImageView = itemView.findViewById(com.example.leafyapp.R.id.iv_check)

        fun bind(plant: UserPlant) {
            tvName.text = plant.nickname

            // Kiểm tra trạng thái chọn
            val isSelected = selectedItems.any { it.id == plant.id }

            if (isSelected) {
                // ĐƯỢC CHỌN: Hiện dấu check, chữ đậm, màu xanh
                ivCheck.visibility = View.VISIBLE
                tvName.setTypeface(null, android.graphics.Typeface.BOLD)
                tvName.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                // KHÔNG CHỌN: Ẩn dấu check, chữ thường, màu đen
                ivCheck.visibility = View.GONE
                tvName.setTypeface(null, android.graphics.Typeface.NORMAL)
                tvName.setTextColor(android.graphics.Color.parseColor("#1A1C1E"))
            }

            itemView.setOnClickListener {
                if (isMultiSelect) {
                    // Chế độ nhiều: Toggle
                    if (isSelected) selectedItems.removeAll { it.id == plant.id }
                    else selectedItems.add(plant)
                } else {
                    // Chế độ 1: Xóa hết chọn cái mới
                    selectedItems.clear()
                    selectedItems.add(plant)
                }
                notifyDataSetChanged() // Cập nhật lại giao diện
                onSelectionChanged(selectedItems)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(com.example.leafyapp.R.layout.item_plant_simple, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(plants[position])
    override fun getItemCount() = plants.size
}