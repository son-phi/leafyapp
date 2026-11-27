package com.example.leafyapp.ui.plantdetail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareLog
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.HealthStatus
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.databinding.ActivityPlantDetailBinding
import com.example.leafyapp.databinding.DialogEditScheduleBinding
import com.example.leafyapp.ui.diagnose.DiagnoseActivity
import com.example.leafyapp.ui.identify.SpeciesInfoRenderer
import com.example.leafyapp.util.ImageUtils
import com.example.leafyapp.util.LocalPlantImages
import com.example.leafyapp.util.ThresholdUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 8.6 Hồ sơ cây trồng – màn chính để theo dõi một cây trong vườn:
 * tóm tắt sức khỏe + Kiểm tra sức khỏe cây, tab Tổng quan (điều trị đang theo dõi, việc sắp tới, môi trường, thông tin
 * chăm sóc), tab Lịch chăm sóc (thêm/sửa/tạm dừng/xóa/đánh dấu xong), tab Lịch sử (chẩn đoán, nhật ký chăm sóc có lọc).
 */
class PlantDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PLANT_ID = "PLANT_ID"
        fun start(context: Context, plantId: String) =
            context.startActivity(Intent(context, PlantDetailActivity::class.java).putExtra(EXTRA_PLANT_ID, plantId))
    }

    private lateinit var binding: ActivityPlantDetailBinding
    private val viewModel: PlantDetailViewModel by viewModels()
    private var tab = 0
    private var historyMode = 0      // 0 chẩn đoán, 1 chăm sóc
    private var logFilterDays = 30   // 7, 30, 0 = tất cả
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))
    private val dateTimeFmt = SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale("vi"))
    private val timeFmt = SimpleDateFormat("HH:mm", Locale("vi"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlantDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val plantId = intent.getStringExtra(EXTRA_PLANT_ID) ?: run { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showPlantMenu(it) }
        binding.btnHealthCheck.setOnClickListener { DiagnoseActivity.start(this, plantId) }
        listOf(R.string.pd_tab_overview, R.string.pd_tab_schedule, R.string.pd_tab_history).forEach {
            binding.tabs.addTab(binding.tabs.newTab().setText(it))
        }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; renderTab() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        viewModel.data.observe(this) { d ->
            binding.progress.visibility = if (d == null) View.VISIBLE else View.GONE
            if (d != null) { renderHeader(d); renderTab() }
        }
        viewModel.message.observe(this) { m ->
            if (!m.isNullOrBlank()) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
        }
        viewModel.deleted.observe(this) { if (it) finish() }
        viewModel.load(plantId)
    }

    // ===================== HEADER =====================
    private fun renderHeader(d: PlantDetailData) {
        val p = d.plant
        binding.tvName.text = p.name
        val speciesName = d.species?.commonName.orEmpty()
        binding.tvSpecies.text = speciesName
        binding.tvSpecies.visibility = if (speciesName.isBlank() || speciesName == p.name) View.GONE else View.VISIBLE
        binding.tvScientific.text = d.species?.scientificName.orEmpty()
        val cover = LocalPlantImages.pathIfExists(this, p.id) ?: p.imageUrl ?: ImageUtils.directImageUrl(d.species?.imageUrl)
        Glide.with(this).load(cover).centerCrop().into(binding.imgCover)

        val (label, color) = healthLabel(p.healthStatus)
        binding.tvHealth.text = label
        binding.tvHealth.setTextColor(ContextCompat.getColor(this, color))

        val today = endOfToday()
        binding.tvStatToday.text = d.schedules.count { it.active && it.status != CareSchedule.STATUS_PAUSED && dueMs(it) <= today }.toString()
        binding.tvStatCare.text = d.logs.size.toString()
        binding.tvStatDiag.text = d.diagnoses.size.toString()
        binding.tvLastCheck.text = p.lastCheckAt?.let { getString(R.string.pd_last_check, dateFmt.format(it.toDate())) }
            ?: getString(R.string.pd_never_checked)
    }

    private fun healthLabel(status: String): Pair<String, Int> = when (status) {
        HealthStatus.HEALTHY.name -> getString(R.string.health_healthy) to R.color.leafy_primary_dark
        HealthStatus.MILD.name -> getString(R.string.health_mild) to R.color.leafy_warning
        HealthStatus.MODERATE.name -> getString(R.string.health_moderate) to R.color.leafy_warning
        HealthStatus.SEVERE.name -> getString(R.string.health_severe) to R.color.leafy_danger
        else -> getString(R.string.health_unknown) to R.color.leafy_text_secondary
    }

    private fun renderTab() {
        val d = viewModel.data.value ?: return
        trendMap = com.example.leafyapp.data.repository.TreatmentPlanBuilder.trends(d.diagnoses)
        treatmentScheduleIds = d.schedules.filter { it.kind == CareSchedule.KIND_TREATMENT }.map { it.id }.toSet()
        binding.containerTab.removeAllViews()
        when (tab) {
            0 -> renderOverview(d)
            1 -> renderSchedules(d)
            else -> renderHistory(d)
        }
    }

    // ===================== TỔNG QUAN =====================
    private fun renderOverview(d: PlantDetailData) {
        val c = binding.containerTab
        val inf = LayoutInflater.from(this)

        // Điều trị đang theo dõi (từ lần chẩn đoán gần nhất)
        val last = d.diagnoses.firstOrNull()
        val plan = d.currentPlanTasks
        if (last != null && plan.isNotEmpty() && d.activeTreatment.isNotEmpty()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_leafy_card_treatment)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = lp(top = 16)
            }
            val name = last.treatmentSnapshot?.diseaseName ?: last.aiLabel
            card.addView(text(getString(R.string.pd_treatment_title, name), bold = true, size = 16f))
            val doneCount = plan.count { it.status == CareSchedule.STATUS_DONE }
            card.addView(text(getString(R.string.pd_treatment_progress, doneCount, plan.size, severityText(last.severity)), secondary = true))
            card.addView(LinearProgressIndicator(this).apply {
                max = plan.size; progress = doneCount; trackCornerRadius = dp(4)
                setIndicatorColor(ContextCompat.getColor(context, R.color.leafy_warning))
                trackColor = ContextCompat.getColor(context, R.color.leafy_warning_container)
                layoutParams = lp(top = 8)
            })
            trendText(last)?.let { card.addView(text(it, size = 13f).apply { setPadding(0, dp(8), 0, 0) }) }
            c.addView(card)
        }

        // Việc sắp tới
        c.addView(sectionTitle(getString(R.string.pd_upcoming)))
        val upcoming = d.dueSorted(d.activeTreatment + d.activeCare).take(3)
        if (upcoming.isEmpty()) c.addView(text(getString(R.string.pd_no_upcoming), secondary = true))
        upcoming.forEach { c.addView(taskCard(inf, it, compact = true)) }
        if (d.schedules.size > 3) c.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.pd_see_all)
            setOnClickListener { binding.tabs.getTabAt(1)?.select() }
        })

        // Môi trường (IoT tích hợp sau)
        section(c, inf, "🌡️ " + getString(R.string.pd_env_title)) { b ->
            b.addView(text(getString(R.string.pd_env_not_connected), secondary = true))
            val th = ThresholdUtils.effective(d.plant, d.species)
            if (th != null) {
                b.addView(text(getString(if (d.plant.useSpeciesThresholds) R.string.pd_env_threshold_species else R.string.pd_env_threshold_custom),
                    bold = true).apply { setPadding(0, dp(10), 0, 0) })
                SpeciesInfoRenderer.thresholdRows(b, inf, th)
            }
        }

        // Thông tin chăm sóc của loài
        d.species?.let { sp ->
            c.addView(sectionTitle(getString(R.string.pd_care_info)))
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            SpeciesInfoRenderer.render(box, sp)
            c.addView(box)
        }
    }

    // ===================== LỊCH CHĂM SÓC =====================
    private fun renderSchedules(d: PlantDetailData) {
        val c = binding.containerTab
        val inf = LayoutInflater.from(this)
        c.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.pd_add_schedule)
            layoutParams = lp(top = 16)
            setOnClickListener { editSchedule(null) }
        })
        if (d.activeTreatment.isNotEmpty()) {
            c.addView(sectionTitle("🩺 " + getString(R.string.pd_section_treatment)))
            d.dueSorted(d.activeTreatment).forEach { c.addView(taskCard(inf, it)) }
        }
        c.addView(sectionTitle("🌿 " + getString(R.string.pd_section_care)))
        if (d.activeCare.isEmpty()) c.addView(text(getString(R.string.pd_no_upcoming), secondary = true))
        d.dueSorted(d.activeCare).forEach { c.addView(taskCard(inf, it)) }
        if (d.pausedCare.isNotEmpty()) {
            c.addView(sectionTitle("⏸ " + getString(R.string.pd_section_paused)))
            d.pausedCare.forEach { c.addView(taskCard(inf, it)) }
        }
    }

    private fun taskCard(inf: LayoutInflater, s: CareSchedule, compact: Boolean = false): View {
        val v = inf.inflate(R.layout.item_care_task_card, binding.containerTab, false)
        val type = CareTaskType.parse(s.taskType)
        v.findViewById<TextView>(R.id.tvIcon).text = SpeciesInfoRenderer.taskIcon(type)
        v.findViewById<TextView>(R.id.tvTitle).text = s.title.ifBlank { type.name }
        val paused = !s.active || s.status == CareSchedule.STATUS_PAUSED
        val due = v.findViewById<TextView>(R.id.tvDue)
        val (dueText, dueColor) = if (paused) getString(R.string.pd_paused) to R.color.leafy_text_secondary else dueLabel(s)
        due.text = dueText
        due.setTextColor(ContextCompat.getColor(this, dueColor))
        v.findViewById<TextView>(R.id.tvRepeat).text = when {
            s.kind == CareSchedule.KIND_TREATMENT && s.occurrences > 1 ->
                getString(R.string.pd_repeat_treatment, s.completedCount + 1, s.occurrences, s.repeatIntervalDays)
            s.kind == CareSchedule.KIND_TREATMENT -> getString(R.string.pd_repeat_once)
            else -> getString(R.string.pd_repeat_every, s.repeatIntervalDays)
        }
        v.findViewById<TextView>(R.id.tvNote).apply {
            text = s.note; visibility = if (s.note.isBlank() || compact) View.GONE else View.VISIBLE
        }
        if (s.kind == CareSchedule.KIND_TREATMENT) v.background = ContextCompat.getDrawable(this, R.drawable.bg_leafy_card_treatment)
        v.findViewById<MaterialButton>(R.id.btnDone).apply {
            visibility = if (paused) View.GONE else View.VISIBLE
            setOnClickListener { isEnabled = false; viewModel.complete(s) }
        }
        v.findViewById<View>(R.id.btnTaskMenu).setOnClickListener { anchor -> taskMenu(anchor, s, paused) }
        return v
    }

    private fun dueLabel(s: CareSchedule): Pair<String, Int> {
        val due = dueMs(s)
        if (due == Long.MAX_VALUE) return "" to R.color.leafy_text_secondary
        val time = timeFmt.format(Date(due))
        val days = TimeUnit.MILLISECONDS.toDays(startOfDay(due) - startOfDay(System.currentTimeMillis())).toInt()
        return when {
            days < 0 -> getString(R.string.pd_due_overdue, -days, time) to R.color.leafy_danger
            days == 0 -> getString(R.string.pd_due_today, time) to R.color.leafy_warning
            days == 1 -> getString(R.string.pd_due_tomorrow, time) to R.color.leafy_primary_dark
            else -> getString(R.string.pd_due_on, dateTimeFmt.format(Date(due))) to R.color.leafy_text_secondary
        }
    }

    private fun taskMenu(anchor: View, s: CareSchedule, paused: Boolean) {
        val m = PopupMenu(this, anchor)
        if (s.kind == CareSchedule.KIND_CARE) m.menu.add(0, 1, 0, R.string.pd_menu_edit)
        m.menu.add(0, 2, 1, if (paused) R.string.pd_menu_resume else R.string.pd_menu_pause)
        m.menu.add(0, 3, 2, R.string.pd_menu_delete)
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> editSchedule(s)
                2 -> viewModel.setPaused(s, !paused)
                3 -> MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.pd_delete_schedule_confirm, s.title))
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.common_delete) { _, _ -> viewModel.deleteSchedule(s) }.show()
            }
            true
        }
        m.show()
    }

    /** Thêm (s = null) hoặc sửa lịch định kỳ. Cũng dùng để ghi lại việc đã làm (asLog = true). */
    private fun editSchedule(s: CareSchedule?, asLog: Boolean = false) {
        val b = DialogEditScheduleBinding.inflate(layoutInflater)
        val types = CareTaskType.values().toList()
        val typeNames = types.map { "${SpeciesInfoRenderer.taskIcon(it)} ${defaultTitle(it)}" }
        b.spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeNames)
        val cal = Calendar.getInstance()
        var time = s?.timeOfDay ?: if (asLog) timeFmt.format(Date()) else "08:00"
        (s?.nextDueAt?.toDate())?.let { cal.time = it }
        s?.let { b.spType.setSelection(types.indexOf(CareTaskType.parse(it.taskType))) }
        b.etTitle.setText(s?.title.orEmpty())
        b.etInterval.setText((s?.repeatIntervalDays ?: 7).toString())
        b.etNote.setText(s?.note.orEmpty())
        b.rowInterval.visibility = View.VISIBLE
        b.tilInterval.visibility = if (asLog) View.GONE else View.VISIBLE

        fun refresh() {
            b.btnTime.text = getString(R.string.pd_field_time, time)
            b.btnDate.text = getString(if (asLog) R.string.pd_field_done_at else R.string.pd_field_date, dateFmt.format(cal.time))
        }
        refresh()
        b.spType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (b.etTitle.text.isNullOrBlank() || types.any { b.etTitle.text.toString() == defaultTitle(it) })
                    b.etTitle.setText(defaultTitle(types[pos]))
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        b.btnTime.setOnClickListener {
            val (h, m) = time.split(":").map { it.toInt() }
            TimePickerDialog(this, { _, hh, mm -> time = String.format(Locale.US, "%02d:%02d", hh, mm); refresh() }, h, m, true).show()
        }
        b.btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, mo, da -> cal.set(y, mo, da); refresh() },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                if (asLog) datePicker.maxDate = System.currentTimeMillis() else datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(when { asLog -> R.string.pd_log_title; s == null -> R.string.pd_dialog_add; else -> R.string.pd_dialog_edit })
            .setView(b.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_save, null)
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val type = types[b.spType.selectedItemPosition]
            val title = b.etTitle.text.toString().trim().ifBlank { defaultTitle(type) }
            val note = b.etNote.text.toString().trim()
            if (asLog) {
                val at = (cal.clone() as Calendar).apply {
                    val (h, m) = time.split(":").map { it.toInt() }; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                }.timeInMillis.coerceAtMost(System.currentTimeMillis())
                viewModel.addLog(type, title, at, note)
            } else {
                val interval = b.etInterval.text.toString().toIntOrNull()
                if (interval == null || interval !in 1..365) { b.tilInterval.error = getString(R.string.pd_invalid_interval); return@setOnClickListener }
                viewModel.saveSchedule(s?.id, type, title, interval, time, cal.time, note)
            }
            dialog.dismiss()
        }
    }

    private fun defaultTitle(t: CareTaskType) = when (t) {
        CareTaskType.WATERING -> "Tưới nước"
        CareTaskType.FERTILIZING -> "Bón phân"
        CareTaskType.PRUNING -> "Cắt tỉa"
        CareTaskType.SPRAYING -> "Phun thuốc"
        CareTaskType.HEALTH_CHECK -> "Kiểm tra sức khỏe cây"
        CareTaskType.OTHER -> "Công việc khác"
    }

    // ===================== LỊCH SỬ =====================
    private fun renderHistory(d: PlantDetailData) {
        val c = binding.containerTab
        c.addView(ChipGroup(this).apply {
            isSingleSelection = true
            layoutParams = lp(top = 12)
            listOf(R.string.pd_history_diagnoses, R.string.pd_history_care).forEachIndexed { i, res ->
                addView(Chip(context).apply {
                    text = getString(res); isCheckable = true; isChecked = historyMode == i
                    setOnClickListener { historyMode = i; renderTab() }
                })
            }
        })
        if (historyMode == 0) renderDiagnoses(d) else renderLogs(d)
    }

    private fun renderDiagnoses(d: PlantDetailData) {
        val c = binding.containerTab
        if (d.diagnoses.isEmpty()) { c.addView(text(getString(R.string.pd_no_diagnoses), secondary = true).apply { setPadding(0, dp(12), 0, 0) }); return }
        d.diagnoses.forEach { g ->
            val row = timelineRow(
                icon = if (g.isHealthy) "💚" else "🔬",
                title = when { g.isHealthy -> getString(R.string.diagnose_healthy); g.treatmentSnapshot != null -> g.treatmentSnapshot.diseaseName; else -> getString(R.string.diagnose_unknown) },
                sub = listOfNotNull(
                    g.createdAt?.let { dateTimeFmt.format(it.toDate()) },
                    if (g.diseaseId != null) severityText(g.severity) else null,
                    if (!g.reliable) getString(R.string.pd_unreliable) else null
                ).joinToString(" · "),
                extra = trendText(g)
            )
            row.setOnClickListener { showDiagnosis(g) }
            c.addView(row)
        }
    }

    private fun showDiagnosis(g: Diagnosis) {
        val plan = g.treatmentSnapshot
        val sb = StringBuilder()
        sb.append(getString(R.string.pd_diag_detail_conf, ImageUtils.percent(g.confidence), String.format(Locale.US, "%.1f", g.lesionAreaPercent)))
        trendText(g)?.let { sb.append("\n").append(it) }
        g.treatmentSnapshot?.modelNote?.takeIf { it.isNotBlank() }?.let { sb.append("\n").append(it) }
        plan?.let { p ->
            if (p.likelyCauses.isNotEmpty()) sb.append("\n\n").append(getString(R.string.diagnose_section_causes)).append(":\n").append(p.likelyCauses.joinToString("\n") { "• $it" })
            if (p.careInfo.isNotEmpty()) sb.append("\n\n").append(getString(R.string.diagnose_section_care)).append(":\n").append(p.careInfo.joinToString("\n") { "• $it" })
            if (p.avoid.isNotEmpty()) sb.append("\n\n").append(getString(R.string.diagnose_section_avoid)).append(":\n").append(p.avoid.joinToString("\n") { "⚠ $it" })
        }
        val changes = g.treatmentSnapshot?.scheduleChanges.orEmpty().filter { it.applied }
        if (changes.isNotEmpty()) sb.append("\n\n").append(getString(R.string.diagnose_section_changes)).append(":\n")
            .append(changes.joinToString("\n") { "• ${it.title}: ${it.reason}" })
        MaterialAlertDialogBuilder(this)
            .setTitle(plan?.diseaseName ?: getString(if (g.isHealthy) R.string.diagnose_healthy else R.string.diagnose_unknown))
            .setMessage(sb.toString())
            .setPositiveButton(R.string.common_close, null).show()
    }

    private fun renderLogs(d: PlantDetailData) {
        val c = binding.containerTab
        c.addView(ChipGroup(this).apply {
            isSingleSelection = true
            listOf(7 to R.string.pd_filter_7, 30 to R.string.pd_filter_30, 0 to R.string.pd_filter_all).forEach { (days, res) ->
                addView(Chip(context).apply {
                    text = getString(res); isCheckable = true; isChecked = logFilterDays == days
                    setOnClickListener { logFilterDays = days; renderTab() }
                })
            }
        })
        c.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.pd_add_log)
            setOnClickListener { editSchedule(null, asLog = true) }
        })
        val from = if (logFilterDays == 0) 0L else System.currentTimeMillis() - TimeUnit.DAYS.toMillis(logFilterDays.toLong())
        val logs = d.logs.filter { (it.performedAt?.toDate()?.time ?: 0L) >= from }
        if (logs.isEmpty()) c.addView(text(getString(R.string.pd_no_logs), secondary = true))
        logs.forEach { l -> c.addView(logRow(l)) }
    }

    private fun logRow(l: CareLog): View = timelineRow(
        icon = SpeciesInfoRenderer.taskIcon(CareTaskType.parse(l.taskType)),
        title = l.title.ifBlank { l.taskType },
        sub = l.performedAt?.let { dateTimeFmt.format(it.toDate()) }.orEmpty() +
            if (treatmentScheduleIds.contains(l.careScheduleId)) " · 🩺" else "",
        extra = l.note.ifBlank { null }
    )

    // ===================== helpers =====================
    private fun timelineRow(icon: String, title: String, sub: String, extra: String?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_leafy_card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = lp(top = 10)
        }
        row.addView(TextView(this).apply {
            text = icon; textSize = 20f; gravity = android.view.Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_leafy_icon_tile)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(title, bold = true))
            if (sub.isNotBlank()) addView(text(sub, secondary = true))
            extra?.let { addView(text(it, size = 13f)) }
        })
        return row
    }

    private fun section(container: LinearLayout, inf: LayoutInflater, title: String, build: (LinearLayout) -> Unit) {
        val v = inf.inflate(R.layout.item_info_section, container, false)
        v.findViewById<TextView>(R.id.tvSectionTitle).text = title
        build(v.findViewById(R.id.containerSectionBody))
        container.addView(v)
    }

    private fun sectionTitle(t: String) = text(t, bold = true, size = 17f).apply { setPadding(0, dp(20), 0, dp(2)) }

    private fun text(value: String, secondary: Boolean = false, bold: Boolean = false, size: Float = if (secondary) 13f else 14f) =
        TextView(this).apply {
            text = value; textSize = size
            setTextColor(ContextCompat.getColor(context, if (secondary) R.color.leafy_text_secondary else R.color.leafy_text))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    /** Xu hướng tính khi hiển thị (QT05), không lưu trong Firestore. */
    private var trendMap: Map<String, String?> = emptyMap()
    private var treatmentScheduleIds: Set<String?> = emptySet()

    private fun trendText(g: Diagnosis): String? = when (trendMap[g.id]) {
        Diagnosis.TREND_IMPROVED -> "↓ Tốt lên so với lần trước"
        Diagnosis.TREND_WORSE -> "↑ Xấu đi so với lần trước"
        Diagnosis.TREND_STABLE -> "→ Ổn định so với lần trước"
        else -> null
    }

    private fun severityText(s: String) = getString(when (runCatching { Severity.valueOf(s) }.getOrDefault(Severity.NONE)) {
        Severity.MILD -> R.string.severity_mild
        Severity.MODERATE -> R.string.severity_moderate
        Severity.SEVERE -> R.string.severity_severe
        Severity.NONE -> R.string.severity_none
    })

    private fun showPlantMenu(anchor: View) {
        val d = viewModel.data.value ?: return
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, R.string.pd_rename)
        m.menu.add(0, 2, 1, R.string.pd_delete_plant)
        m.setOnMenuItemClickListener {
            if (it.itemId == 1) {
                val et = EditText(this).apply { setText(d.plant.name); setSelection(text.length) }
                MaterialAlertDialogBuilder(this).setTitle(R.string.pd_rename)
                    .setView(LinearLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(et, lp()) })
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.common_save) { _, _ -> et.text.toString().trim().takeIf { n -> n.isNotBlank() }?.let(viewModel::rename) }
                    .show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.pd_delete_plant_confirm, d.plant.name))
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.common_delete) { _, _ -> viewModel.deletePlant() }.show()
            }
            true
        }
        m.show()
    }

    private fun dueMs(s: CareSchedule) = (s.nextDueAt ?: s.startAt)?.toDate()?.time ?: Long.MAX_VALUE
    private fun startOfDay(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun endOfToday() = startOfDay(System.currentTimeMillis()) + TimeUnit.DAYS.toMillis(1) - 1
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }
}
