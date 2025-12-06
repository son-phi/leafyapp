package com.example.leafyapp.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Import Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Plant

class SearchAdapter(private val onClick: (Plant) -> Unit) :
    ListAdapter<Plant, SearchAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        holder.title.text = p.name
        holder.sub.text = p.scientificName

        // Xử lý ảnh với Glide
        if (!p.image.isNullOrBlank()) {

            val directUrl = convertGoogleDriveLink(p.image)
            // Nếu link là Google Drive, cần convert sang link xem trực tiếp (nếu cần)
            // Tuy nhiên, Glide thường load tốt các URL chuẩn.
            Glide.with(holder.itemView.context)
                .load(directUrl)
                .placeholder(android.R.drawable.ic_menu_gallery) // Ảnh chờ
                .error(android.R.drawable.ic_delete) // Ảnh lỗi
                .centerCrop()
                .into(holder.thumb)
        } else {
            holder.thumb.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onClick(p) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumb: ImageView = itemView.findViewById(R.id.iv_thumb)
        val title: TextView = itemView.findViewById(R.id.tv_title)
        val sub: TextView = itemView.findViewById(R.id.tv_sub)
    }

    class Diff : DiffUtil.ItemCallback<Plant>() {
        override fun areItemsTheSame(oldItem: Plant, newItem: Plant) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Plant, newItem: Plant) = oldItem == newItem
    }

    // --- Thêm hàm hỗ trợ này xuống dưới cùng class Adapter ---
    private fun convertGoogleDriveLink(originalUrl: String): String {
        // Link gốc trong DB: https://drive.google.com/file/d/FILE_ID/view?usp=drive_link
        // Link cần chuyển:   https://drive.google.com/uc?export=view&id=FILE_ID

        return if (originalUrl.contains("drive.google.com")) {
            try {
                val id = originalUrl.substringAfter("/d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) {
                originalUrl // Nếu lỗi thì trả về link gốc
            }
        } else {
            originalUrl // Nếu không phải link Drive thì giữ nguyên
        }
    }
}