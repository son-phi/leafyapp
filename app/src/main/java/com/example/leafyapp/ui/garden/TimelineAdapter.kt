package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.TaskType
import java.text.SimpleDateFormat
import java.util.Locale

class TimelineAdapter : RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder>() {

    private val items = ArrayList<TimelineItem>()

    fun submitList(newItems: List<TimelineItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        // Bạn cần tạo layout item_timeline.xml (xem hướng dẫn bên dưới)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_timeline_date)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_timeline_title)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_timeline_desc)
        private val imgIcon: ImageView = itemView.findViewById(R.id.img_timeline_icon)

        fun bind(item: TimelineItem) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            tvDate.text = dateFormat.format(item.dateMillis)

            when (item) {
                is TimelineItem.PlantAdded -> {
                    tvTitle.text = "Chào mừng thành viên mới!"
                    tvDesc.text = "${item.plantName} đã được thêm vào vườn."
                    imgIcon.setImageResource(R.drawable.ic_launcher_foreground) // Thay bằng icon cây
                }
                is TimelineItem.CareEvent -> {
                    tvTitle.text = item.taskType.displayName
                    tvDesc.text = "Đã hoàn thành chăm sóc."
                    val iconRes = when (item.taskType) {
                        TaskType.WATER -> R.drawable.ic_water_drop
                        TaskType.MIST -> R.drawable.ic_mist
                        TaskType.FERTILIZER -> R.drawable.ic_fertilizer
                        TaskType.ROTATE -> R.drawable.ic_rotate
                        TaskType.CUT -> R.drawable.ic_cut
                    }
                    imgIcon.setImageResource(iconRes)
                }
            }
        }
    }
}