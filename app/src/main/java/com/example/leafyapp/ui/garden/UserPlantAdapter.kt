package com.example.leafyapp.ui.garden

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.R
import com.example.leafyapp.data.model.HealthStatus
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.util.ImageUtils

/**
 * Danh sách cây trong vườn dạng lưới 2 cột: thẻ tóm tắt ở đầu (số cây, việc hôm nay, cây cần chú ý),
 * mỗi cây một thẻ ảnh + tên + loài + tình trạng sức khỏe + số việc hôm nay.
 */
class UserPlantAdapter(
    private val onMenuClick: (View, UserPlant) -> Unit,
    private val onItemClick: (UserPlant) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_SUMMARY = 0
        const val TYPE_PLANT = 1
    }

    private var plants: List<UserPlant> = emptyList()
    private var todayCount: Map<String, Int> = emptyMap()

    fun submitList(list: List<UserPlant>) {
        plants = list.sortedWith(compareBy<UserPlant> { healthRank(it.healthStatus) }.thenBy { it.nickname })
        notifyDataSetChanged()
    }

    /** Số việc đến hạn hôm nay (kể cả quá hạn) theo id cây. */
    fun submitTodayCounts(counts: Map<String, Int>) {
        todayCount = counts
        notifyDataSetChanged()
    }

    /** Cây bệnh nặng lên đầu để dễ thấy. */
    private fun healthRank(s: String?) = when (s) {
        HealthStatus.SEVERE.name -> 0
        HealthStatus.MODERATE.name -> 1
        HealthStatus.MILD.name -> 2
        else -> 3
    }

    fun spanLookup() = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int) = if (getItemViewType(position) == TYPE_SUMMARY) 2 else 1
    }

    override fun getItemViewType(position: Int) = if (position == 0) TYPE_SUMMARY else TYPE_PLANT
    override fun getItemCount() = if (plants.isEmpty()) 0 else plants.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SUMMARY) SummaryVH(inf.inflate(R.layout.item_garden_summary, parent, false))
        else PlantVH(inf.inflate(R.layout.item_garden_plant_card, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SummaryVH -> holder.bind()
            is PlantVH -> holder.bind(plants[position - 1])
        }
    }

    inner class SummaryVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvPlants = v.findViewById<TextView>(R.id.tvSumPlants)
        private val tvToday = v.findViewById<TextView>(R.id.tvSumToday)
        private val tvAttention = v.findViewById<TextView>(R.id.tvSumAttention)
        fun bind() {
            val ctx = itemView.context
            tvPlants.text = "🪴\n" + ctx.getString(R.string.garden_summary_plants, plants.size)
            tvToday.text = "📅\n" + ctx.getString(R.string.garden_summary_today, plants.sumOf { todayCount[it.id] ?: 0 })
            tvAttention.text = "🩺\n" + ctx.getString(R.string.garden_summary_attention, plants.count { healthRank(it.healthStatus) < 3 })
        }
    }

    inner class PlantVH(v: View) : RecyclerView.ViewHolder(v) {
        private val img = v.findViewById<ImageView>(R.id.imgPlant)
        private val tvName = v.findViewById<TextView>(R.id.tvName)
        private val tvSpecies = v.findViewById<TextView>(R.id.tvSpecies)
        private val tvHealth = v.findViewById<TextView>(R.id.tvHealth)
        private val tvTasks = v.findViewById<TextView>(R.id.tvTasks)
        private val btnMore = v.findViewById<ImageButton>(R.id.btnMore)

        fun bind(p: UserPlant) {
            val ctx = itemView.context
            // Cây cũ: tra danh mục SQLite theo plantId để lấy tên loài/ảnh mẫu
            val legacy = if (p.speciesId == null) DatabaseHelper(ctx).getPlantById(p.plantId) else null
            tvName.text = p.nickname.trim().ifBlank { p.speciesName ?: legacy?.name ?: "" }
            val species = p.speciesName ?: legacy?.name ?: ""
            tvSpecies.text = species
            tvSpecies.visibility = if (species.isBlank() || species == tvName.text.toString()) View.GONE else View.VISIBLE
            loadImage(img, p.imagePath?.takeIf { it.isNotBlank() } ?: legacy?.image)

            val (label, color) = when (p.healthStatus) {
                HealthStatus.HEALTHY.name -> ctx.getString(R.string.health_healthy) to R.color.leafy_primary_dark
                HealthStatus.MILD.name -> ctx.getString(R.string.health_mild) to R.color.leafy_warning
                HealthStatus.MODERATE.name -> ctx.getString(R.string.health_moderate) to R.color.leafy_warning
                HealthStatus.SEVERE.name -> ctx.getString(R.string.health_severe) to R.color.leafy_danger
                else -> null to R.color.leafy_text_secondary
            }
            tvHealth.visibility = if (label == null) View.GONE else View.VISIBLE
            tvHealth.text = label
            tvHealth.setTextColor(ContextCompat.getColor(ctx, color))

            val n = todayCount[p.id] ?: 0
            tvTasks.text = if (n > 0) "📅 " + ctx.getString(R.string.garden_plant_tasks_today, n)
            else "✓ " + ctx.getString(R.string.garden_plant_no_tasks)
            tvTasks.setTextColor(ContextCompat.getColor(ctx, if (n > 0) R.color.leafy_warning else R.color.leafy_text_secondary))

            btnMore.setOnClickListener { onMenuClick(it, p) }
            itemView.setOnClickListener { onItemClick(p) }
        }

        private fun loadImage(view: ImageView, path: String?) {
            val ctx = view.context
            if (path.isNullOrBlank()) { view.setImageDrawable(null); return }
            val resId = ctx.resources.getIdentifier(path, "drawable", ctx.packageName)
            val src: Any = if (resId != 0) resId else (ImageUtils.directImageUrl(path) ?: path)
            Glide.with(ctx).load(src).centerCrop().into(view)
        }
    }
}
