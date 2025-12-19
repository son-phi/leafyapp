package com.example.leafyapp.ui.information

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.UserPlant
import com.google.android.material.card.MaterialCardView

class DiseasePlantSelectionAdapter(
    private var plants: List<UserPlant>
) : RecyclerView.Adapter<DiseasePlantSelectionAdapter.PlantViewHolder>() {

    val selectedPlants = HashSet<UserPlant>()

    fun updateData(newPlants: List<UserPlant>) {
        this.plants = newPlants
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        // Layout này phải là bản dùng MaterialCardView có dấu tích góc trên
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dialog_plant_selector, parent, false)
        return PlantViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
        holder.bind(plants[position])
    }

    override fun getItemCount() = plants.size

    inner class PlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ánh xạ View từ CardView
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardPlantItem)
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivPlantImage)
        private val tvName: TextView = itemView.findViewById(R.id.tvPlantNickname)
        private val tvSpecies: TextView = itemView.findViewById(R.id.tvPlantSpecies)
        private val ivCheckMark: ImageView = itemView.findViewById(R.id.ivCheckMark)

        fun bind(plant: UserPlant) {
            tvName.text = plant.nickname
            tvSpecies.text = "Cây của bạn"

            // Load ảnh sử dụng hàm bên dưới
            loadImage(plant.imagePath, ivThumb)

            // Cập nhật trạng thái UI dựa trên danh sách đã chọn
            val isSelected = selectedPlants.contains(plant)
            updateSelectionUi(isSelected)

            // Sự kiện click vào cả khối Card
            itemView.setOnClickListener {
                if (selectedPlants.contains(plant)) {
                    selectedPlants.remove(plant)
                    updateSelectionUi(false)
                } else {
                    selectedPlants.add(plant)
                    updateSelectionUi(true)
                }
            }
        }

        // Hàm đổi màu viền và hiện dấu tích (Không dùng Checkbox)
        private fun updateSelectionUi(isSelected: Boolean) {
            if (isSelected) {
                // Viền xanh lá chuẩn của app
                cardView.strokeColor = Color.parseColor("#4CAF50")
                cardView.strokeWidth = 4 // Tăng độ dày viền khi chọn cho rõ
                ivCheckMark.visibility = View.VISIBLE
            } else {
                // Viền xám nhạt mặc định
                cardView.strokeColor = Color.parseColor("#E0E0E0")
                cardView.strokeWidth = 2
                ivCheckMark.visibility = View.GONE
            }
        }
    }

    // --- [HÀM XỬ LÝ ẢNH CHUẨN] ---
    private fun loadImage(path: String?, imageView: ImageView) {
        if (path.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_launcher_background)
            return
        }
        val context = imageView.context
        val resId = context.resources.getIdentifier(path, "drawable", context.packageName)

        if (resId != 0) {
            Glide.with(context).load(resId).centerCrop().into(imageView)
        } else {
            val finalUrl = if (path.contains("drive.google.com")) {
                try {
                    val id = path.substringAfter("d/").substringBefore("/")
                    "https://drive.google.com/uc?export=view&id=$id"
                } catch (e: Exception) { path }
            } else path

            Glide.with(context)
                .load(finalUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .into(imageView)
        }
    }
}