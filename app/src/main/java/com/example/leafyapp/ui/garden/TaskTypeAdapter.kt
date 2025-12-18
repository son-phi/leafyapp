import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.TaskType

class TaskTypeAdapter(
    private val types: Array<TaskType>,
    private val currentSelection: TaskType?,
    private val onTypeSelected: (TaskType) -> Unit
) : RecyclerView.Adapter<TaskTypeAdapter.ViewHolder>() {

    // Biến lưu item đang được chọn
    private var selectedItem: TaskType? = currentSelection

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card_root)
        val tvName: TextView = itemView.findViewById(R.id.tv_task_name)
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_task_icon)
        val ivCheck: ImageView = itemView.findViewById(R.id.iv_check)

        fun bind(type: TaskType) {
            tvName.text = type.displayName
            ivIcon.setImageResource(type.iconResId)

            // --- LOGIC TÔ MÀU ---
            if (type == selectedItem) {
                // TRẠNG THÁI: ĐANG CHỌN (Xanh lá - Leafy Style)
                val colorGreen = Color.parseColor("#4CAF50")
                val bgGreenLight = Color.parseColor("#F1F8E9") // Xanh cực nhạt

                // Card: Viền xanh, Nền xanh nhạt
                card.strokeColor = colorGreen
                card.strokeWidth = 3 // Viền dày hơn
                card.setCardBackgroundColor(bgGreenLight)

                // Text: Chữ đậm, Màu xanh
                tvName.setTextColor(colorGreen)
                tvName.setTypeface(null, Typeface.BOLD)

                // Icon: Tô màu xanh
                ivIcon.setColorFilter(colorGreen)

                // Hiện dấu check
                ivCheck.visibility = View.VISIBLE

            } else {
                // TRẠNG THÁI: BÌNH THƯỜNG (Trắng/Xám)
                val colorGrey = Color.parseColor("#757575")
                val colorTextNormal = Color.parseColor("#1A1C1E")
                val colorBorder = Color.parseColor("#E0E0E0")
                val bgWhite = Color.parseColor("#FFFFFF")

                // Card: Viền xám mỏng, Nền trắng
                card.strokeColor = colorBorder
                card.strokeWidth = 1
                card.setCardBackgroundColor(bgWhite)

                // Text: Chữ thường, Màu đen
                tvName.setTextColor(colorTextNormal)
                tvName.setTypeface(null, Typeface.NORMAL)

                // Icon: Tô màu xám
                ivIcon.setColorFilter(colorGrey)

                // Ẩn dấu check
                ivCheck.visibility = View.GONE
            }

            itemView.setOnClickListener {
                selectedItem = type
                notifyDataSetChanged() // Reload lại để cập nhật màu sắc
                onTypeSelected(type)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_type, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(types[position])
    }

    override fun getItemCount() = types.size
}