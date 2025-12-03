package com.example.leafyapp.ui.garden

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
import com.example.leafyapp.databinding.ItemPlantGroupHeaderBinding
import com.example.leafyapp.databinding.ItemUserPlantBinding

// Đảm bảo import đúng GardenDataItem
// Nếu GardenDataItem nằm trong file riêng, import nó.
// Nếu nó nằm trong UserPlantAdapter.kt thì không cần import.
// Giả sử GardenDataItem đã được tách ra file riêng ui/garden/GardenDataItem.kt

class UserPlantAdapter(
    private val onMenuClick: (View, UserPlant) -> Unit, // Callback khi bấm 3 chấm
    private val onItemClick: (UserPlant) -> Unit        // Callback khi bấm vào cây
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val originalList = ArrayList<UserPlant>()
    private val displayList = ArrayList<GardenDataItem>()
    private val groupsState = HashMap<Int, Boolean>()

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    fun submitList(list: List<UserPlant>) {
        originalList.clear()
        originalList.addAll(list)
        recalculateDisplayList()
    }

    private fun recalculateDisplayList() {
        displayList.clear()
        val grouped = originalList.groupBy { it.plantId }

        for ((plantId, plants) in grouped) {
            val firstPlant = plants[0]
            val isExpanded = groupsState[plantId] ?: false

            displayList.add(GardenDataItem.GroupHeader(
                plantId = plantId,
                name = "", // Tên sẽ lấy từ DB trong onBind
                image = firstPlant.imagePath,
                count = plants.size,
                isExpanded = isExpanded
            ))

            if (isExpanded) {
                for (plant in plants) {
                    displayList.add(GardenDataItem.PlantItem(plant))
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is GardenDataItem.GroupHeader -> TYPE_HEADER
            is GardenDataItem.PlantItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            // Sử dụng layout item_plant_group_header.xml (Header của nhóm)
            val binding = ItemPlantGroupHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            // Sử dụng layout item_plant_child.xml (Cây con)
            // Lưu ý: item_plant_child.xml phải được inflate đúng
            // Ở đây mình dùng view trực tiếp vì có thể chưa tạo binding cho item_plant_child
            val view = inflater.inflate(R.layout.item_plant_child, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is GardenDataItem.GroupHeader -> (holder as HeaderViewHolder).bind(item)
            is GardenDataItem.PlantItem -> (holder as ItemViewHolder).bind(item.userPlant)
        }
    }

    override fun getItemCount(): Int = displayList.size

    // --- HEADER VIEWHOLDER ---
    inner class HeaderViewHolder(private val binding: ItemPlantGroupHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: GardenDataItem.GroupHeader) {
            val context = itemView.context
            val dbHelper = DatabaseHelper(context)
            val originalPlant = dbHelper.getPlantById(header.plantId)

            binding.tvGroupName.text = originalPlant?.name ?: "Unknown Group"
            binding.tvGroupCount.text = "${header.count} plants"

            val imageToLoad = header.image ?: originalPlant?.image
            loadImage(imageToLoad, binding.imgGroupIcon)

            binding.imgExpandArrow.rotation = if (header.isExpanded) 180f else 0f

            binding.root.setOnClickListener {
                val newState = !header.isExpanded
                groupsState[header.plantId] = newState
                recalculateDisplayList()
            }
        }
    }

    // --- ITEM VIEWHOLDER (Cây con) ---
    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ánh xạ View thủ công (nếu không dùng Binding cho item con)
        private val tvNickname: TextView = itemView.findViewById(R.id.tv_child_nickname)
        private val tvSciName: TextView = itemView.findViewById(R.id.tv_child_sci_name)
        private val imgChild: ImageView = itemView.findViewById(R.id.img_child_plant)
        private val btnMore: ImageView = itemView.findViewById(R.id.btn_more)

        fun bind(plant: UserPlant) {
            tvNickname.text = plant.nickname

            val context = itemView.context
            val dbHelper = DatabaseHelper(context)
            val originalPlant = dbHelper.getPlantById(plant.plantId)

            tvSciName.text = originalPlant?.scientificName ?: ""

            val imageToLoad = if (!plant.imagePath.isNullOrEmpty()) plant.imagePath else originalPlant?.image
            loadImage(imageToLoad, imgChild)

            // Sự kiện Click 3 chấm -> Gọi callback về Fragment
            btnMore.setOnClickListener {
                onMenuClick(it, plant)
            }

            itemView.setOnClickListener { onItemClick(plant) }
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
                try { path.substringAfter("d/").substringBefore("/").let { "https://drive.google.com/uc?export=view&id=$it" } } catch (e: Exception) { path }
            } else path
            Glide.with(context).load(finalUrl).centerCrop().into(imageView)
        }
    }
}