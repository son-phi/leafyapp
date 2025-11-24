package com.example.leafyapp.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

        // cố gắng load drawable từ tên lưu trong p.image (nếu image lưu tên drawable)
        var loaded = false
        val ctx = holder.itemView.context
        if (!p.image.isNullOrBlank()) {
            // nếu DB lưu "peace_lily" hoặc "peace_lily.png" -> lấy tên trước dấu chấm
            val name = p.image.substringBeforeLast('.')
            val resId = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (resId != 0) {
                holder.thumb.setImageResource(resId)
                loaded = true
            }
        }
        if (!loaded) {
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
}
