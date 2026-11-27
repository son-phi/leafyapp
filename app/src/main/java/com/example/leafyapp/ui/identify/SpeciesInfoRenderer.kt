package com.example.leafyapp.ui.identify

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.model.Threshold
import com.example.leafyapp.util.ImageUtils
import com.example.leafyapp.util.ThresholdUtils

/** Dựng các khối thông tin loài + thông tin chăm sóc (hiển thị cùng lúc, không cần nút riêng). */
object SpeciesInfoRenderer {

    fun careLevelText(context: Context, level: String): String = context.getString(
        when (level) {
            "EASY" -> R.string.care_level_easy
            "HARD" -> R.string.care_level_hard
            else -> R.string.care_level_medium
        }
    )

    fun taskIcon(type: CareTaskType): String = when (type) {
        CareTaskType.WATERING -> "💧"
        CareTaskType.FERTILIZING -> "🧪"
        CareTaskType.PRUNING -> "✂️"
        CareTaskType.SPRAYING -> "🌫️"
        CareTaskType.HEALTH_CHECK -> "🔍"
        CareTaskType.OTHER -> "🌿"
    }

    fun loadSpeciesImage(view: ImageView, url: String?) {
        Glide.with(view)
            .load(ImageUtils.directImageUrl(url))
            .placeholder(R.drawable.leaf_solid_full)
            .error(R.drawable.leaf_solid_full)
            .centerCrop()
            .into(view)
    }

    fun render(container: LinearLayout, species: Species) {
        container.removeAllViews()
        val ctx = container.context
        val inflater = LayoutInflater.from(ctx)

        section(container, inflater, ctx.getString(R.string.species_section_about)) { body ->
            text(body, inflater, species.description)
            row(body, inflater, "🌍", ctx.getString(R.string.species_origin), species.origin)
            if (species.otherNames.isNotEmpty())
                row(body, inflater, "🏷️", ctx.getString(R.string.species_other_names), species.otherNames.joinToString(", "))
        }

        section(container, inflater, ctx.getString(R.string.species_section_care)) { body ->
            row(body, inflater, "☀️", ctx.getString(R.string.species_light),
                listOf(species.lightNeedLabel, species.lightDescription).filter { it.isNotBlank() }.joinToString(". "))
            row(body, inflater, "💧", ctx.getString(R.string.species_water),
                ctx.getString(R.string.species_every_days, species.wateringCycleDays) + ". " + species.wateringDescription)
            val fert = if (!species.noFertilizer && species.fertilizingCycleDays > 0)
                ctx.getString(R.string.species_every_days, species.fertilizingCycleDays) + ". " + species.fertilizerDescription
            else ctx.getString(R.string.species_no_fertilizer) + ". " + species.fertilizerDescription
            row(body, inflater, "🧪", ctx.getString(R.string.species_fertilizer), fert)
            row(body, inflater, "🪴", ctx.getString(R.string.species_soil), species.soilDescription)
            row(body, inflater, "🌡️", ctx.getString(R.string.species_temperature), species.temperatureDescription)
            row(body, inflater, "💦", ctx.getString(R.string.species_humidity), species.humidityDescription)
            row(body, inflater, "🗓️", ctx.getString(R.string.species_seasonal), species.seasonalNotes)
        }

        section(container, inflater, ctx.getString(R.string.species_section_threshold)) { body ->
            thresholdRows(body, inflater, species.threshold)
        }

        if (species.suggestedTemplates().isNotEmpty()) {
            section(container, inflater, ctx.getString(R.string.species_section_schedule)) { body ->
                species.suggestedTemplates().forEach { t ->
                    row(body, inflater, taskIcon(CareTaskType.parse(t.taskType)), t.title,
                        ctx.getString(R.string.species_every_days, t.repeatIntervalDays) + ". " + t.note)
                }
            }
        }

        if (species.careNotes.isNotEmpty()) section(container, inflater, ctx.getString(R.string.species_section_notes)) { body ->
            text(body, inflater, species.careNotes.joinToString("\n") { "• $it" })
        }
        if (species.commonProblems.isNotEmpty()) section(container, inflater, ctx.getString(R.string.species_section_problems)) { body ->
            text(body, inflater, species.commonProblems.joinToString("\n") { "• $it" })
        }

        section(container, inflater, ctx.getString(R.string.species_section_toxicity)) { body ->
            val tags = mutableListOf<String>()
            if (species.toxicity.pets) tags += ctx.getString(R.string.species_toxic_pets)
            if (species.toxicity.humans) tags += ctx.getString(R.string.species_toxic_humans)
            val head = if (tags.isEmpty()) "✅ " + ctx.getString(R.string.species_non_toxic) else "⚠️ " + tags.joinToString(" · ")
            text(body, inflater, head + "\n" + species.toxicity.note)
        }

        section(container, inflater, ctx.getString(R.string.species_section_extra)) { body ->
            row(body, inflater, "🌱", ctx.getString(R.string.species_family), species.family)
            row(body, inflater, "📏", ctx.getString(R.string.species_size), species.matureSize)
            row(body, inflater, "📈", ctx.getString(R.string.species_growth), species.growthRate)
            row(body, inflater, "🌿", ctx.getString(R.string.species_propagation), species.propagation)
            if (species.synonyms.isNotEmpty())
                row(body, inflater, "📚", ctx.getString(R.string.species_synonyms), species.synonyms.joinToString(", "))
        }
    }

    fun thresholdRows(body: LinearLayout, inflater: LayoutInflater, t: Threshold) {
        val ctx = body.context
        row(body, inflater, "🪴", ctx.getString(R.string.species_soil_moisture), ThresholdUtils.soilText(t))
        row(body, inflater, "🌡️", ctx.getString(R.string.species_temperature), ThresholdUtils.temperatureText(t))
        row(body, inflater, "💦", ctx.getString(R.string.species_humidity), ThresholdUtils.humidityText(t))
        row(body, inflater, "☀️", ctx.getString(R.string.species_light_lux), ThresholdUtils.lightText(t))
    }

    private fun section(container: LinearLayout, inflater: LayoutInflater, title: String, build: (LinearLayout) -> Unit) {
        val view = inflater.inflate(R.layout.item_info_section, container, false)
        view.findViewById<TextView>(R.id.tvSectionTitle).text = title
        build(view.findViewById(R.id.containerSectionBody))
        container.addView(view)
    }

    fun row(body: LinearLayout, inflater: LayoutInflater, icon: String, label: String, value: String) {
        if (value.isBlank()) return
        val view = inflater.inflate(R.layout.item_info_row, body, false)
        view.findViewById<TextView>(R.id.tvRowIcon).text = icon
        view.findViewById<TextView>(R.id.tvRowLabel).text = label
        view.findViewById<TextView>(R.id.tvRowValue).text = value
        body.addView(view)
    }

    private fun text(body: LinearLayout, inflater: LayoutInflater, value: String) {
        if (value.isBlank()) return
        val view = inflater.inflate(R.layout.item_info_row, body, false)
        view.findViewById<TextView>(R.id.tvRowIcon).visibility = android.view.View.GONE
        view.findViewById<TextView>(R.id.tvRowLabel).visibility = android.view.View.GONE
        view.findViewById<TextView>(R.id.tvRowValue).text = value
        body.addView(view)
    }
}
