package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.DatabaseHelper
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

            val context = itemView.context
            val dbHelper = DatabaseHelper(context)
            val originalPlant = dbHelper.getPlantById(plant.plantId)

            if (originalPlant != null) {
                binding.tvPlantType.text = originalPlant.scientificName
            } else {
                binding.tvPlantType.text = "Unknown Plant"
            }

            // --- LOGIC LOAD ẢNH (ĐÃ SỬA) ---
            var imageToLoad = if (!plant.imagePath.isNullOrEmpty()) {
                plant.imagePath
            } else {
                originalPlant?.image
            }

            if (!imageToLoad.isNullOrEmpty()) {
                val resId = context.resources.getIdentifier(imageToLoad, "drawable", context.packageName)

                if (resId != 0) {
                    Glide.with(context).load(resId).centerCrop().into(binding.imgPlant)
                } else {
                    // Convert link Drive trước khi load
                    val finalUrl = convertDrive(imageToLoad!!)
                    Glide.with(context)
                        .load(finalUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
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

    // Hàm convert link Google Drive
    private fun convertDrive(url: String): String {
        return if (url.contains("drive.google.com")) {
            try {
                val id = url.substringAfter("d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) { url }
        } else url
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