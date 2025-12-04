package com.example.leafyapp.ui.garden

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.R
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.ItemPlantGroupBinding

// Class nội bộ để lưu dữ liệu nhóm
data class PlantGroupItem(
    val plantId: Int,
    val items: List<UserPlant>,
    var isExpanded: Boolean = false
)

class UserPlantAdapter(
    private val onMenuClick: (View, UserPlant) -> Unit, // Callback menu 3 chấm
    private val onItemClick: (UserPlant) -> Unit
) : RecyclerView.Adapter<UserPlantAdapter.GroupViewHolder>() {

    private val groupList = ArrayList<PlantGroupItem>()

    fun submitList(list: List<UserPlant>) {
        val groupedMap = list.groupBy { it.plantId }

        groupList.clear()
        for ((id, plants) in groupedMap) {
            groupList.add(PlantGroupItem(id, plants, false))
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemPlantGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groupList[position])
    }

    override fun getItemCount(): Int = groupList.size

    inner class GroupViewHolder(private val binding: ItemPlantGroupBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: PlantGroupItem) {
            val context = itemView.context
            val dbHelper = DatabaseHelper(context)
            val firstPlant = group.items.firstOrNull()

            val originalPlant = if (firstPlant != null) dbHelper.getPlantById(firstPlant.plantId) else null

            // --- HEADER ---
            binding.tvGroupName.text = originalPlant?.name ?: "Unknown Plant"
            binding.tvGroupCount.text = "${group.items.size} plants"

            val imagePath = originalPlant?.image
            loadImage(imagePath, binding.imgGroupIcon)

            binding.imgExpandArrow.rotation = if (group.isExpanded) 180f else 0f

            // --- CHILD ITEMS ---
            binding.layoutChildContainer.removeAllViews()

            if (group.isExpanded) {
                binding.layoutChildContainer.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(context)

                for (plant in group.items) {
                    val childView = inflater.inflate(R.layout.item_plant_child, binding.layoutChildContainer, false)

                    val tvNickname = childView.findViewById<TextView>(R.id.tv_child_nickname)
                    val tvSciName = childView.findViewById<TextView>(R.id.tv_child_sci_name)
                    val imgChild = childView.findViewById<ImageView>(R.id.img_child_plant)

                    // --- ĐÃ SỬA: Tìm đúng ID btn_more ---
                    val btnMore = childView.findViewById<ImageView>(R.id.btn_more)

                    tvNickname.text = plant.nickname
                    tvSciName.text = originalPlant?.scientificName ?: ""

                    val childImg = if (!plant.imagePath.isNullOrEmpty()) plant.imagePath else originalPlant?.image
                    loadImage(childImg, imgChild)

                    // Sự kiện Click 3 chấm
                    btnMore.setOnClickListener {
                        onMenuClick(it, plant)
                    }

                    childView.setOnClickListener { onItemClick(plant) }

                    binding.layoutChildContainer.addView(childView)
                }
            } else {
                binding.layoutChildContainer.visibility = View.GONE
            }

            binding.layoutHeaderClick.setOnClickListener {
                group.isExpanded = !group.isExpanded
                notifyItemChanged(adapterPosition)
            }
        }

        private fun loadImage(path: String?, imageView: ImageView) {
            if (path.isNullOrEmpty()) {
                imageView.setImageResource(R.drawable.ic_launcher_background)
                return
            }
            val context = imageView.context
            val resId = context.resources.getIdentifier(path, "drawable", context.packageName)

            if (resId != 0) {
                Glide.with(context).load(resId).centerCrop().into(imageView)
            } else {
                val finalUrl = if (path.contains("drive.google.com")) {
                    try {
                        val id = path.substringAfter("d/").substringBefore("/")
                        "https://drive.google.com/uc?export=view&id=$id"
                    } catch (e: Exception) { path }
                } else path

                Glide.with(context)
                    .load(finalUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(imageView)
            }
        }
    }
}