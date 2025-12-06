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
import java.util.Date
import java.util.Locale

class TimelineAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 1. Thay đổi cấu trúc list:
    // fullList: Chứa toàn bộ dữ liệu gốc
    private val fullList = ArrayList<TimelineItem>()
    // visibleList: Chỉ chứa những item ĐANG ĐƯỢC HIỆN (đã trừ đi những tháng bị đóng)
    private val visibleList = ArrayList<TimelineItem>()

    // 2. Biến lưu trạng thái: Những tháng nào đang bị đóng (Collapse)
    private val collapsedMonths = HashSet<String>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    // 3. Hàm nạp dữ liệu mới
    fun submitList(newItems: List<TimelineItem>) {
        fullList.clear()
        fullList.addAll(newItems)
        // Sau khi nạp dữ liệu gốc, tính toán xem cần hiện cái gì
        recalculateVisibleList()
    }

    // 4. LOGIC QUAN TRỌNG: Tính toán hiển thị (Expand/Collapse)
    private fun recalculateVisibleList() {
        visibleList.clear()
        var currentHeaderKey: String? = null
        var isCurrentGroupCollapsed = false

        for (item in fullList) {
            if (item is TimelineItem.Header) {
                // Header thì luôn luôn hiện
                visibleList.add(item)
                currentHeaderKey = item.key // Lưu lại key của tháng này (VD: "12-2025")

                // Kiểm tra xem tháng này có đang nằm trong danh sách "Bị đóng" không
                isCurrentGroupCollapsed = collapsedMonths.contains(currentHeaderKey)
            } else {
                // Nếu là item con: Chỉ hiện nếu nhóm KHÔNG bị đóng
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

    // Lưu ý: Dùng visibleList thay vì items
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = visibleList[position]
        if (holder is HeaderViewHolder && item is TimelineItem.Header) {
            holder.bind(item)
        } else if (holder is TimelineViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = visibleList.size

    // --- ViewHolder Header (Xử lý bấm mở/đóng) ---
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tv_month_header)
        private val imgArrow: ImageView = itemView.findViewById(R.id.img_arrow) // Nhớ thêm ID này vào layout header

        fun bind(item: TimelineItem.Header) {
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale("vi", "VN"))
            tvHeader.text = dateFormat.format(item.dateMillis).capitalize()

            // Xử lý mũi tên: Nếu đóng thì xoay 180 độ (xuống), mở thì để thường (lên)
            val isCollapsed = collapsedMonths.contains(item.key)
            imgArrow.rotation = if (isCollapsed) 180f else 0f

            // Xử lý sự kiện CLICK vào Header
            itemView.setOnClickListener {
                if (isCollapsed) {
                    collapsedMonths.remove(item.key) // Đang đóng -> Mở ra
                } else {
                    collapsedMonths.add(item.key)    // Đang mở -> Đóng lại
                }
                // Tính toán lại danh sách hiển thị
                recalculateVisibleList()
            }
        }

        private fun String.capitalize(): String {
            return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    // --- ViewHolder Item (Xử lý định dạng ngày Fancy) ---
    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_timeline_date)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_timeline_title)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_timeline_desc)
        private val imgIcon: ImageView = itemView.findViewById(R.id.img_timeline_icon)

        fun bind(item: TimelineItem) {

            // --- 5. LOGIC FORMAT NGÀY: "Saturday 06th, 14:14" ---
            tvDate.text = getFancyDate(item.dateMillis)

            // Reset UI
            tvTitle.setTextColor(android.graphics.Color.BLACK)
            imgIcon.clearColorFilter()

            when (item) {
                is TimelineItem.PlantAdded -> {
                    tvTitle.text = "Cây được thêm vào vườn"
                    tvDesc.text = "${item.plantName} đã được thêm vào vườn"
                    imgIcon.setImageResource(R.drawable.baseline_emoji_nature_24)
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

        // Hàm helper format ngày đặc biệt
        private fun getFancyDate(millis: Long): String {
            val date = Date(millis)
            val dayNameFormat = SimpleDateFormat("EEEE", Locale.US) // Saturday
            val dayNumFormat = SimpleDateFormat("d", Locale.US)    // 6
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)  // 14:14

            val dayName = dayNameFormat.format(date)
            val dayNum = dayNumFormat.format(date).toInt()
            val time = timeFormat.format(date)

            val suffix = getDaySuffix(dayNum) // Lấy đuôi th, st, nd

            // Kết quả: "Saturday 06th, 14:14"
            return String.format("%s %02d%s, %s", dayName, dayNum, suffix, time)
        }

        // Hàm tính đuôi ngày
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