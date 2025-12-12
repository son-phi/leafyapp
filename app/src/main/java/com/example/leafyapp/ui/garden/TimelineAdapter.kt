package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
// [ĐÃ XÓA IMPORT TaskType VÌ KHÔNG DÙNG NỮA]
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val fullList = ArrayList<TimelineItem>()
    private val visibleList = ArrayList<TimelineItem>()
    private val collapsedMonths = HashSet<String>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    fun submitList(newItems: List<TimelineItem>) {
        fullList.clear()
        fullList.addAll(newItems)
        recalculateVisibleList()
    }

    private fun recalculateVisibleList() {
        visibleList.clear()
        var currentHeaderKey: String? = null
        var isCurrentGroupCollapsed = false

        for (item in fullList) {
            if (item is TimelineItem.Header) {
                visibleList.add(item)
                currentHeaderKey = item.key
                isCurrentGroupCollapsed = collapsedMonths.contains(currentHeaderKey)
            } else {
                if (!isCurrentGroupCollapsed) {
                    visibleList.add(item)
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (visibleList[position]) {
            is TimelineItem.Header -> TYPE_HEADER
            else -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
            TimelineViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = visibleList[position]
        if (holder is HeaderViewHolder && item is TimelineItem.Header) {
            holder.bind(item)
        } else if (holder is TimelineViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = visibleList.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tv_month_header)
        private val imgArrow: ImageView = itemView.findViewById(R.id.img_arrow)

        fun bind(item: TimelineItem.Header) {
            // Hiển thị dạng: "Tháng 12-2025" (Đơn giản hóa format để tránh lỗi font)
            tvHeader.text = "Tháng ${item.key}"

            val isCollapsed = collapsedMonths.contains(item.key)
            imgArrow.rotation = if (isCollapsed) 180f else 0f

            itemView.setOnClickListener {
                if (isCollapsed) collapsedMonths.remove(item.key)
                else collapsedMonths.add(item.key)
                recalculateVisibleList()
            }
        }
    }

    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_timeline_date)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_timeline_title)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_timeline_desc)
        private val imgIcon: ImageView = itemView.findViewById(R.id.img_timeline_icon)

        fun bind(item: TimelineItem) {
            tvDate.text = getFancyDate(item.dateMillis)

            tvTitle.setTextColor(android.graphics.Color.BLACK)
            imgIcon.clearColorFilter()

            when (item) {
                is TimelineItem.PlantAdded -> {
                    tvTitle.text = "Cây được thêm vào vườn"
                    tvDesc.text = "${item.plantName} đã được thêm vào vườn"
                    imgIcon.setImageResource(R.drawable.baseline_emoji_nature_24)
                }
                is TimelineItem.CareEvent -> {
                    // [SỬA LỖI]: taskType bây giờ là String, hiển thị trực tiếp
                    tvTitle.text = item.taskType
                    tvDesc.text = "Đã hoàn thành chăm sóc."

                    // [SỬA LỖI]: So sánh String để chọn icon
                    val iconRes = when (item.taskType) {
                        "Tưới nước" -> R.drawable.ic_water_drop
                        "Phun sương" -> R.drawable.ic_mist
                        "Bón phân" -> R.drawable.ic_fertilizer
                        "Xoay cây" -> R.drawable.ic_rotate
                        "Cắt tỉa" -> R.drawable.ic_cut
                        else -> R.drawable.ic_water_drop // Default icon
                    }
                    imgIcon.setImageResource(iconRes)
                }
                is TimelineItem.DiseaseEvent -> {
                    tvTitle.text = "Phát hiện bệnh: ${item.diseaseName}"
                    tvDesc.text = "Cần theo dõi và chữa trị ngay."
                    tvTitle.setTextColor(android.graphics.Color.parseColor("#F5C857"))
                    imgIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                    imgIcon.setColorFilter(android.graphics.Color.parseColor("#F5C857"))
                }
                else -> {}
            }
        }

        private fun getFancyDate(millis: Long): String {
            val date = Date(millis)
            val dayNameFormat = SimpleDateFormat("EEEE", Locale.US)
            val dayNumFormat = SimpleDateFormat("d", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

            val dayName = dayNameFormat.format(date)
            val dayNum = dayNumFormat.format(date).toInt()
            val time = timeFormat.format(date)
            val suffix = getDaySuffix(dayNum)

            return String.format("%s %02d%s, %s", dayName, dayNum, suffix, time)
        }

        private fun getDaySuffix(n: Int): String {
            if (n in 11..13) return "th"
            return when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
    }
}