package com.example.leafyapp.ui.garden

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskType
import com.example.leafyapp.databinding.ItemPlantTaskGroupBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskGroupAdapter(
    private var selectedDateMillis: Long,
    private val onTaskChecked: (CareTask) -> Unit,
    private val onTaskClick: (CareTask) -> Unit
) : ListAdapter<PlantTasksGroup, TaskGroupAdapter.GroupViewHolder>(GroupDiffCallback()) {

    fun updateSelectedDate(newDate: Long) {
        selectedDateMillis = newDate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemPlantTaskGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(private val binding: ItemPlantTaskGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: PlantTasksGroup) {
            // 1. Set tên cây
            binding.tvPlantNameHeader.text = group.plant.nickname

            // 2. Load ảnh cây
            val context = itemView.context
            if (!group.plant.imagePath.isNullOrEmpty()) {
                val resId = context.resources.getIdentifier(group.plant.imagePath, "drawable", context.packageName)
                if (resId != 0) {
                    Glide.with(context).load(resId).centerCrop().into(binding.imgPlantAvatar)
                } else {
                    val finalUrl = convertDrive(group.plant.imagePath!!)
                    Glide.with(context)
                        .load(finalUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(binding.imgPlantAvatar)
                }
            } else {
                binding.imgPlantAvatar.setImageResource(R.drawable.ic_launcher_background)
            }

            // 3. Add các task con vào
            binding.layoutTasksContainer.removeAllViews()
            val inflater = LayoutInflater.from(context)

            group.tasks.forEachIndexed { index, task ->
                // Inflate layout dòng task
                val taskView = inflater.inflate(R.layout.item_task_row, binding.layoutTasksContainer, false)

                // Bind dữ liệu cho dòng task
                bindTaskRow(taskView, task, context)

                // Ẩn đường kẻ của dòng cuối cùng
                if (index == group.tasks.size - 1) {
                    val divider = taskView.findViewById<View>(R.id.divider)
                    if (divider != null) divider.visibility = View.GONE
                }

                binding.layoutTasksContainer.addView(taskView)
            }
        }

        private fun bindTaskRow(view: View, task: CareTask, context: Context) {
            val tvName = view.findViewById<TextView>(R.id.tv_task_name)
            val imgIcon = view.findViewById<ImageView>(R.id.img_task_icon)
            val cbDone = view.findViewById<CheckBox>(R.id.cb_task_done)
            val imgDone = view.findViewById<ImageView>(R.id.img_done_check)
            val layoutCompleted = view.findViewById<View>(R.id.layout_completed_info)
            val tvNextDue = view.findViewById<TextView>(R.id.tv_next_due_date)

            tvName.text = task.type.displayName

            val iconRes = when (task.type) {
                TaskType.WATER -> R.drawable.ic_water_drop
                TaskType.MIST -> R.drawable.ic_mist
                TaskType.FERTILIZER -> R.drawable.ic_fertilizer
                TaskType.ROTATE -> R.drawable.ic_rotate
                TaskType.CUT -> R.drawable.ic_cut
            }
            imgIcon.setImageResource(iconRes)

            // --- TÍNH TRẠNG HIỆN TẠI ---
            val isCompletedOnSelectedDate = isSameDay(task.lastCompletedDate ?: 0, selectedDateMillis)
            val isFuture = selectedDateMillis > System.currentTimeMillis() &&
                    !isSameDay(selectedDateMillis, System.currentTimeMillis())

            // Xóa listener cũ tránh loop
            cbDone.setOnCheckedChangeListener(null)

            if (isCompletedOnSelectedDate) {
                // ĐÃ hoàn thành trong ngày đang xem → set UI completed
                applyCompletedUi(cbDone, imgDone, layoutCompleted, tvNextDue, task)
            } else {
                imgDone.visibility = View.GONE
                layoutCompleted.visibility = View.GONE

                if (isFuture) {
                    cbDone.visibility = View.INVISIBLE
                    cbDone.isEnabled = false
                } else {
                    cbDone.visibility = View.VISIBLE
                    cbDone.isChecked = false
                    cbDone.isEnabled = true
                }
            }


            // --- LISTENER: CẬP NHẬT UI NGAY KHI TICK ---
            cbDone.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // 1. Cập nhật UI ngay
                    applyCompletedUi(cbDone, imgDone, layoutCompleted, tvNextDue, task)

                    // 2. Báo ViewModel cập nhật DB
                    onTaskChecked(task)
                }
            }


            view.setOnClickListener { onTaskClick(task) }
        }
    }

    private fun convertDrive(url: String): String {
        return if (url.contains("drive.google.com")) {
            try {
                val id = url.substringAfter("d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) { url }
        } else url
    }

    private fun isSameDay(date1: Long, date2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }
    private fun applyCompletedUi(
        cbDone: CheckBox,
        imgDone: ImageView,
        layoutCompleted: View,
        tvNextDue: TextView,
        task: CareTask
    ) {
        // Ẩn checkbox, hiện icon tick + block "Today Completed / Next task"
        cbDone.visibility = View.GONE
        imgDone.visibility = View.VISIBLE
        layoutCompleted.visibility = View.VISIBLE

        // Tính ngày Next task dựa theo selectedDateMillis + frequencyDays
        val oneDayMillis = 24L * 60 * 60 * 1000
        val baseCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val nextTime = baseCal.timeInMillis + task.frequencyDays * oneDayMillis

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        tvNextDue.text = "Next task: ${dateFormat.format(nextCal.time)}"
    }


    class GroupDiffCallback : DiffUtil.ItemCallback<PlantTasksGroup>() {
        override fun areItemsTheSame(oldItem: PlantTasksGroup, newItem: PlantTasksGroup): Boolean {
            return oldItem.plant.id == newItem.plant.id
        }
        override fun areContentsTheSame(oldItem: PlantTasksGroup, newItem: PlantTasksGroup): Boolean {
            return oldItem == newItem
        }
    }
}