package com.example.leafyapp.ui.garden

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.TaskType
import com.example.leafyapp.data.model.TaskWithPlant
import com.example.leafyapp.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskAdapter(
    private var selectedDateMillis: Long,
    private val onTaskChecked: (TaskWithPlant) -> Unit,
    private val onItemClick: (TaskWithPlant) -> Unit
) : ListAdapter<TaskWithPlant, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    fun updateSelectedDate(newDate: Long) {
        selectedDateMillis = newDate
        notifyDataSetChanged()
    }

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
            val task = item.task

            // 1. Hiển thị thông tin cơ bản
            binding.tvTaskType.text = task.type.displayName
            binding.tvPlantName.text = item.plant.nickname

            val iconRes = when (task.type) {
                TaskType.WATER -> R.drawable.ic_water_drop
                TaskType.MIST -> R.drawable.ic_mist
                TaskType.FERTILIZER -> R.drawable.ic_fertilizer
                TaskType.ROTATE -> R.drawable.ic_rotate
                TaskType.CUT -> R.drawable.ic_cut
            }
            binding.imgTaskIcon.setImageResource(iconRes)

            // Xóa listener cũ để tránh lỗi khi scroll
            binding.cbTaskDone.setOnCheckedChangeListener(null)

            // 2. Kiểm tra trạng thái Hoàn thành
            val isCompletedOnSelectedDate = isSameDay(task.lastCompletedDate ?: 0, selectedDateMillis)
            val isFuture = selectedDateMillis > System.currentTimeMillis() && !isSameDay(selectedDateMillis, System.currentTimeMillis())

            if (isCompletedOnSelectedDate) {
                // --- TRƯỜNG HỢP 1: ĐÃ HOÀN THÀNH ---
                // Hiển thị dấu tick, không cho bỏ tick
                binding.cbTaskDone.visibility = View.VISIBLE
                binding.cbTaskDone.isChecked = true
                binding.cbTaskDone.isEnabled = false

                // Hiển thị thông tin "Today Completed" và "Next Task"
                binding.layoutCompletedInfo.visibility = View.VISIBLE

                val nextDate = Calendar.getInstance()
                nextDate.timeInMillis = task.nextDueDate
                val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
                binding.tvNextDueDate.text = "Next task: ${dateFormat.format(nextDate.time)}"

                // Gạch ngang chữ
                binding.tvTaskType.paintFlags = binding.tvTaskType.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            } else {
                // --- TRƯỜNG HỢP 2: CHƯA HOÀN THÀNH ---
                binding.cbTaskDone.isChecked = false
                binding.layoutCompletedInfo.visibility = View.GONE
                binding.tvTaskType.paintFlags = binding.tvTaskType.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

                if (isFuture) {
                    // Nếu xem ngày tương lai -> Ẩn checkbox
                    binding.cbTaskDone.visibility = View.INVISIBLE
                    binding.cbTaskDone.isEnabled = false
                } else {
                    // Nếu xem hôm nay (hoặc quá khứ sót lại) -> Hiện checkbox để tick
                    binding.cbTaskDone.visibility = View.VISIBLE
                    binding.cbTaskDone.isEnabled = true
                }
            }

            // 3. Gán sự kiện Click
            binding.cbTaskDone.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    onTaskChecked(item)
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private fun isSameDay(date1: Long, date2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
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