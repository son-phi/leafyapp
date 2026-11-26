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
    private val onTaskClick: (CareTask) -> Unit,
    /** Ảnh cây giống thẻ ở tab Cây trồng (cây mới không lưu imagePath, dùng ảnh trên máy/ảnh loài). */
    private val imageFor: (String) -> String? = { null }
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
            val doneToday = group.tasks.count { isSameDay(it.lastCompletedDate ?: 0, selectedDateMillis) }
            binding.tvGroupProgress.text = "$doneToday/${group.tasks.size}"
            val isV2 = group.plant.speciesId != null

            // 2. Load ảnh cây
            val context = itemView.context
            val avatar = group.plant.imagePath?.takeIf { it.isNotBlank() } ?: imageFor(group.plant.id)
            if (!avatar.isNullOrEmpty()) {
                val resId = context.resources.getIdentifier(avatar, "drawable", context.packageName)
                if (resId != 0) {
                    Glide.with(context).load(resId).centerCrop().into(binding.imgPlantAvatar)
                } else {
                    val finalUrl = com.example.leafyapp.util.ImageUtils.directImageUrl(avatar) ?: avatar
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
                bindTaskRow(taskView, task, context, isV2)

                // Ẩn đường kẻ của dòng cuối cùng
                if (index == group.tasks.size - 1) {
                    val divider = taskView.findViewById<View>(R.id.divider)
                    if (divider != null) divider.visibility = View.GONE
                }

                binding.layoutTasksContainer.addView(taskView)
            }
        }

        private fun bindTaskRow(view: View, task: CareTask, context: Context, isV2: Boolean) {
            val tvName = view.findViewById<TextView>(R.id.tv_task_name)
            val imgIcon = view.findViewById<ImageView>(R.id.img_task_icon)
            val cbDone = view.findViewById<CheckBox>(R.id.cb_task_done)
            val imgDone = view.findViewById<ImageView>(R.id.img_done_check)
            val layoutCompleted = view.findViewById<View>(R.id.layout_completed_info)
            val tvNextDue = view.findViewById<TextView>(R.id.tv_next_due_date)

            // Lịch mới: CareTask.plantName mang tên công việc (vd. "Lau lá"); lịch cũ: dịch tên loại việc
            tvName.text = if (isV2 && !task.plantName.isNullOrBlank()) task.plantName else vietnameseName(task.type)
            val tvSub = view.findViewById<TextView>(R.id.tvTaskSub)
            val timeText = String.format(Locale.US, "%02d:%02d", task.timeHour, task.timeMinute)
            val overdueDays = ((startOfDay(selectedDateMillis) - startOfDay(task.nextDueDate)) / 86_400_000L).toInt()
            tvSub.text = when {
                isSameDay(task.lastCompletedDate ?: 0, selectedDateMillis) -> context.getString(R.string.tasks_done_at)
                overdueDays > 0 && isSameDay(selectedDateMillis, System.currentTimeMillis()) -> "⏰ $timeText · quá hạn $overdueDays ngày"
                else -> "⏰ $timeText · mỗi ${task.frequencyDays} ngày"
            }
            tvSub.setTextColor(androidx.core.content.ContextCompat.getColor(context,
                if (overdueDays > 0 && isSameDay(selectedDateMillis, System.currentTimeMillis())) R.color.leafy_danger else R.color.leafy_text_secondary))

            // [SỬA LỖI Ở ĐÂY] Thêm nhánh OTHER và else
            val iconRes = when (task.type) {
                TaskType.WATER -> R.drawable.ic_water_drop
                TaskType.MIST -> R.drawable.ic_mist
                TaskType.FERTILIZER -> R.drawable.ic_fertilizer
                TaskType.ROTATE -> R.drawable.ic_rotate
                TaskType.CUT -> R.drawable.ic_cut
                TaskType.OTHER -> R.drawable.ic_water_drop // Fallback icon
                else -> R.drawable.ic_water_drop // Fallback an toàn
            }
            imgIcon.setImageResource(iconRes)

            // --- TÍNH TRẠNG THÁI HIỆN TẠI ---
            val isCompletedOnSelectedDate = isSameDay(task.lastCompletedDate ?: 0, selectedDateMillis)
            val isFuture = selectedDateMillis > System.currentTimeMillis() && !isSameDay(selectedDateMillis, System.currentTimeMillis())

            cbDone.setOnCheckedChangeListener(null)

            if (isCompletedOnSelectedDate) {
                cbDone.visibility = View.VISIBLE
                cbDone.isChecked = true

                // KHÔNG disable để khỏi bị xám, chỉ khoá click
                cbDone.isEnabled = true
                cbDone.isClickable = false

                imgDone.visibility = View.GONE
                layoutCompleted.visibility = View.VISIBLE

                val oneDayMillis = 24L * 60 * 60 * 1000
                val nextDueTime = selectedDateMillis + (task.frequencyDays * oneDayMillis)
                val nextDate = Calendar.getInstance().apply { timeInMillis = nextDueTime }
                tvNextDue.text = context.getString(R.string.tasks_next, SimpleDateFormat("dd/MM", Locale("vi")).format(nextDate.time))
            }
            else {
                // CHƯA HOÀN THÀNH
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
                    onTaskChecked(task)

                    // Khóa không cho bấm lại, nhưng vẫn giữ màu xanh
                    cbDone.isClickable = false
                    cbDone.isEnabled = true

                    layoutCompleted.visibility = View.VISIBLE

                    val oneDayMillis = 24L * 60 * 60 * 1000
                    val baseTime = selectedDateMillis
                    val nextDueTime = baseTime + (task.frequencyDays * oneDayMillis)
                    val nextDate = Calendar.getInstance().apply { timeInMillis = nextDueTime }
                    tvNextDue.text = context.getString(R.string.tasks_next, SimpleDateFormat("dd/MM", Locale("vi")).format(nextDate.time))
                }
            }

            view.setOnClickListener { onTaskClick(task) }
        }
    }

    private fun vietnameseName(t: TaskType) = when (t) {
        TaskType.WATER -> "Tưới nước"
        TaskType.FERTILIZER -> "Bón phân"
        TaskType.ROTATE -> "Xoay chậu"
        TaskType.MIST -> "Phun sương"
        TaskType.CUT -> "Cắt tỉa"
        else -> "Công việc khác"
    }

    private fun startOfDay(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun convertDrive(url: String): String =
        com.example.leafyapp.util.ImageUtils.directImageUrl(url) ?: url

    private fun isSameDay(date1: Long, date2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
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