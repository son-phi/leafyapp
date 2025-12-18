import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.UserPlant

class SimplePlantAdapter(
    private val plants: List<UserPlant>,
    private val initialSelection: List<UserPlant>,
    private val isMultiSelect: Boolean,
    private val onSelectionChanged: (List<UserPlant>) -> Unit
) : RecyclerView.Adapter<SimplePlantAdapter.ViewHolder>() {

    private val selectedItems = ArrayList<UserPlant>(initialSelection)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card_root)
        val tvName: TextView = itemView.findViewById(R.id.tv_name)
        val ivCheck: ImageView = itemView.findViewById(R.id.iv_check)

        fun bind(plant: UserPlant) {
            tvName.text = plant.nickname

            val isSelected = selectedItems.any { it.id == plant.id }

            // --- TRANG TRÍ GIAO DIỆN ---
            if (isSelected) {
                // TRẠNG THÁI ĐƯỢC CHỌN:
                // 1. Viền Xanh (#4CAF50)
                card.strokeColor = Color.parseColor("#4CAF50")
                card.strokeWidth = 3 // Viền dày hơn chút

                // 2. Nền Xanh Nhạt (#F1F8E9) - Rất dịu mắt
                card.setCardBackgroundColor(Color.parseColor("#F1F8E9"))

                // 3. Chữ Xanh
                tvName.setTextColor(Color.parseColor("#2E7D32")) // Xanh đậm hơn chút cho dễ đọc
                ivCheck.visibility = View.VISIBLE
            } else {
                // TRẠNG THÁI BÌNH THƯỜNG:
                // 1. Viền Xám mờ (#E0E0E0)
                card.strokeColor = Color.parseColor("#E0E0E0")
                card.strokeWidth = 1

                // 2. Nền Trắng (hoặc xám cực nhạt #FAFAFA)
                card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))

                // 3. Chữ Đen/Xám đậm
                tvName.setTextColor(Color.parseColor("#1A1C1E"))
                ivCheck.visibility = View.GONE
            }

            itemView.setOnClickListener {
                if (isMultiSelect) {
                    if (isSelected) selectedItems.removeAll { it.id == plant.id }
                    else selectedItems.add(plant)
                } else {
                    selectedItems.clear()
                    selectedItems.add(plant)
                }
                notifyDataSetChanged()
                onSelectionChanged(selectedItems)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plant_simple, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(plants[position])
    override fun getItemCount() = plants.size
}