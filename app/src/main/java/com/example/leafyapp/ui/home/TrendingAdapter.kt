package com.example.leafyapp.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Plant

class TrendingAdapter(private val onClick: (Plant) -> Unit) :
    ListAdapter<Plant, TrendingAdapter.TrendingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trending_plant, parent, false)

        // --- LOGIC TÍNH TOÁN 2.5 CỘT ---

        // 1. Lấy chiều rộng của RecyclerView cha
        // Nếu parent.measuredWidth = 0 (chưa đo xong), dùng tạm chiều rộng màn hình
        var parentWidth = parent.measuredWidth
        if (parentWidth == 0) {
            parentWidth = parent.context.resources.displayMetrics.widthPixels
        }

        // 2. Công thức: (Chiều rộng cha / 2.5) - Margin
        // Lưu ý: item_trending_plant.xml của bạn có layout_marginEnd="16dp"
        // Ta cần đổi 16dp ra pixel để tính toán chính xác
        val marginInPixels = (10 * parent.context.resources.displayMetrics.density).toInt()

        // Chiều rộng mỗi item sẽ bằng: (Tổng chiều rộng / 2.5) - khoảng cách margin
        val itemWidth = (parentWidth / 2.5).toInt() - marginInPixels

        // 3. Gán chiều rộng mới này vào View
        val layoutParams = view.layoutParams
        layoutParams.width = itemWidth
        view.layoutParams = layoutParams
        // -------------------------------

        return TrendingViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class TrendingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_plant_image)
        private val tvName: TextView = itemView.findViewById(R.id.tv_plant_name)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(getItem(position))
                }
            }
        }

        fun bind(plant: Plant) {
            tvName.text = plant.name

            // Xử lý hiển thị ảnh
            if (!plant.image.isNullOrBlank()) {
                val directUrl = convertGoogleDriveLink(plant.image)
                Glide.with(itemView.context)
                    .load(directUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery) // Ảnh chờ
                    .error(android.R.drawable.ic_delete) // Ảnh lỗi
                    .centerCrop() // Cắt ảnh cho vừa khít thẻ
                    .into(ivImage)
            } else {
                ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        // Hàm chuyển đổi link Google Drive (nếu cần)
        private fun convertGoogleDriveLink(originalUrl: String): String {
            return if (originalUrl.contains("drive.google.com")) {
                try {
                    val id = originalUrl.substringAfter("/d/").substringBefore("/")
                    "https://drive.google.com/uc?export=view&id=$id"
                } catch (e: Exception) {
                    originalUrl
                }
            } else {
                originalUrl
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Plant>() {
        override fun areItemsTheSame(oldItem: Plant, newItem: Plant) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Plant, newItem: Plant) = oldItem == newItem
    }
}