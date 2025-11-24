package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.ItemUserPlantBinding

class UserPlantAdapter(
    private val onDeleteClick: (UserPlant) -> Unit,
    private val onItemClick: (UserPlant) -> Unit
) : ListAdapter<UserPlant, UserPlantAdapter.UserPlantViewHolder>(UserPlantDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserPlantViewHolder {
        val binding = ItemUserPlantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserPlantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserPlantViewHolder, position: Int) {
        val plant = getItem(position)
        holder.bind(plant)
    }

    inner class UserPlantViewHolder(private val binding: ItemUserPlantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(plant: UserPlant) {
            binding.tvNickname.text = plant.nickname
            binding.tvPlantType.text = "Plant ID: ${plant.plantId}"

            val context = itemView.context

            // LOGIC LOAD ẢNH MỚI: Kiểm tra resource hoặc file path
            if (!plant.imagePath.isNullOrEmpty()) {
                // 1. Thử tìm xem imagePath có phải là tên resource trong drawable không (VD: "rose")
                val resId = context.resources.getIdentifier(plant.imagePath, "drawable", context.packageName)

                if (resId != 0) {
                    // Nếu đúng là resource ID -> Load bằng ID
                    Glide.with(context)
                        .load(resId)
                        .centerCrop()
                        .into(binding.imgPlant)
                } else {
                    // 2. Nếu không phải resource -> Load như đường dẫn file/URL bình thường (Fallback)
                    Glide.with(context)
                        .load(plant.imagePath)
                        .centerCrop()
                        .into(binding.imgPlant)
                }
            } else {
                binding.imgPlant.setImageResource(R.drawable.ic_launcher_background)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(plant)
            }

            binding.root.setOnClickListener {
                onItemClick(plant)
            }
        }
    }

    class UserPlantDiffCallback : DiffUtil.ItemCallback<UserPlant>() {
        override fun areItemsTheSame(oldItem: UserPlant, newItem: UserPlant): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserPlant, newItem: UserPlant): Boolean {
            return oldItem == newItem
        }
    }
}