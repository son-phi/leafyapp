package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.databinding.ItemTaskBinding

class TaskAdapter : ListAdapter<TaskWithPlant, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TaskWithPlant) {
            // Hiển thị thông tin
            binding.tvTaskType.text = item.task.type.name // WATER, FERTILIZER...
            binding.tvPlantName.text = item.plant.nickname

            // Đặt icon tùy theo loại task (Bạn có thể thêm logic switch-case ở đây để đổi icon)
            binding.imgTaskIcon.setImageResource(R.drawable.ic_launcher_foreground) // Icon tạm

            // Reset checkbox (để tránh lỗi tái sử dụng view)
            binding.cbTaskDone.setOnCheckedChangeListener(null)
            binding.cbTaskDone.isChecked = false // Mặc định chưa xong

            // Sự kiện check xong task (Xử lý sau)
            binding.cbTaskDone.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Logic hoàn thành task (Ẩn đi hoặc gạch ngang)
                }
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TaskWithPlant>() {
        override fun areItemsTheSame(oldItem: TaskWithPlant, newItem: TaskWithPlant): Boolean {
            return oldItem.task.id == newItem.task.id
        }

        override fun areContentsTheSame(oldItem: TaskWithPlant, newItem: TaskWithPlant): Boolean {
            return oldItem == newItem
        }
    }
}