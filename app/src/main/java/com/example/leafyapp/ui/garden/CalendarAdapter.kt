package com.example.leafyapp.ui.garden

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.databinding.ItemCalendarDayBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarAdapter(
    private val onDateClick: (Date) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    private val days = ArrayList<Date>()
    private var selectedPosition = -1

    init {
        val calendar = Calendar.getInstance()

        // Reset giờ phút giây để so sánh ngày chính xác
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        // Bắt đầu từ 30 ngày trước (để xem lịch sử gần đây)
        calendar.add(Calendar.DAY_OF_YEAR, -30)

        // Tạo lịch cho 365 ngày tiếp theo (Tổng cộng khoảng 1 năm hơn)
        for (i in 0 until 400) {
            val date = calendar.time
            days.add(date)

            // Kiểm tra nếu là ngày hôm nay thì lưu vị trí lại
            if (isSameDay(calendar, today)) {
                selectedPosition = i
            }

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    // Hàm so sánh 2 ngày có trùng nhau không
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // Hàm lấy vị trí ngày được chọn (để Fragment cuộn tới đó)
    fun getSelectedPositionInt(): Int {
        return selectedPosition
    }

    inner class DayViewHolder(private val binding: ItemCalendarDayBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(date: Date, position: Int) {
            val cal = Calendar.getInstance().apply { time = date }
            binding.tvDayOfMonth.text = cal.get(Calendar.DAY_OF_MONTH).toString()
            binding.tvDayOfWeek.text = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "T2"; Calendar.TUESDAY -> "T3"; Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"; Calendar.FRIDAY -> "T6"; Calendar.SATURDAY -> "T7"; else -> "CN"
            }

            val ctx = binding.root.context
            val isSelected = position == selectedPosition
            val isToday = isSameDay(cal, Calendar.getInstance())
            binding.tvDayOfMonth.isSelected = isSelected
            val primary = androidx.core.content.ContextCompat.getColor(ctx, com.example.leafyapp.R.color.leafy_primary)
            val text = androidx.core.content.ContextCompat.getColor(ctx, com.example.leafyapp.R.color.leafy_text)
            val secondary = androidx.core.content.ContextCompat.getColor(ctx, com.example.leafyapp.R.color.leafy_text_secondary)
            binding.tvDayOfMonth.setTextColor(when { isSelected -> Color.WHITE; isToday -> primary; else -> text })
            binding.tvDayOfWeek.setTextColor(if (isSelected || isToday) primary else secondary)

            binding.root.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                onDateClick(date)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(days[position], position)
    }

    override fun getItemCount(): Int = days.size

    /** Chọn lại hôm nay; trả về vị trí để cuộn tới. */
    fun selectToday(): Int {
        val today = Calendar.getInstance()
        val pos = days.indexOfFirst { isSameDay(Calendar.getInstance().apply { time = it }, today) }
        if (pos >= 0) {
            val prev = selectedPosition
            selectedPosition = pos
            notifyItemChanged(prev); notifyItemChanged(pos)
            onDateClick(days[pos])
        }
        return pos
    }

    fun getSelectedDate(): Date = if (selectedPosition != -1) days[selectedPosition] else Date()
}