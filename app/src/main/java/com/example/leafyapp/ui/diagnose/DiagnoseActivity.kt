package com.example.leafyapp.ui.diagnose

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareSchedule
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Diagnosis
import com.example.leafyapp.data.model.PlanAdjustment
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.data.repository.TreatmentPlanBuilder
import com.example.leafyapp.databinding.ActivityDiagnoseBinding
import com.example.leafyapp.ui.identify.SpeciesInfoRenderer
import com.example.leafyapp.util.ImageUtils
import java.io.File
import java.util.Locale

/** 8.4 Kiểm tra sức khỏe cây – mở từ hồ sơ một cây trong vườn. */
class DiagnoseActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PLANT_ID = "PLANT_ID"
        private const val EXTRA_PHOTO = "PHOTO"
        private const val EXTRA_LABEL = "AI_LABEL"
        private const val EXTRA_CONF = "CONF"
        private const val EXTRA_LEVEL = "LEVEL"
        private const val EXTRA_AREA = "AREA"
        private const val EXTRA_NOTE = "NOTE"

        fun start(context: Context, plantId: String) {
            context.startActivity(Intent(context, DiagnoseActivity::class.java).putExtra(EXTRA_PLANT_ID, plantId))
        }

        /** Gắn kết quả chẩn đoán đã có (từ màn hình chính) vào một cây trong vườn. */
        fun startWithResult(context: Context, plantId: String, photoPath: String, r: com.example.leafyapp.api.DiseaseResult) {
            context.startActivity(Intent(context, DiagnoseActivity::class.java)
                .putExtra(EXTRA_PLANT_ID, plantId).putExtra(EXTRA_PHOTO, photoPath)
                .putExtra(EXTRA_LABEL, r.diseaseId.orEmpty()).putExtra(EXTRA_CONF, r.confidence)
                .putExtra(EXTRA_LEVEL, r.severity?.level).putExtra(EXTRA_AREA, r.severity?.percentArea ?: 0.0)
                .putExtra(EXTRA_NOTE, r.note))
        }
    }

    private lateinit var binding: ActivityDiagnoseBinding
    private val viewModel: DiagnoseViewModel by viewModels()
    private var cameraFile: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) viewModel.setPhoto(f.absolutePath)
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { copyToCache(it)?.let { path -> viewModel.setPhoto(path) } }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(this, R.string.diagnose_camera_denied, Toast.LENGTH_LONG).show()
            launchGallery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnoseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val plantId = intent.getStringExtra(EXTRA_PLANT_ID)
        if (plantId.isNullOrBlank()) { finish(); return }
        val presetPhoto = intent.getStringExtra(EXTRA_PHOTO)
        if (presetPhoto != null) {
            viewModel.loadWithResult(plantId, presetPhoto, com.example.leafyapp.api.DiseaseResult(
                diseaseId = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null },
                confidence = intent.getDoubleExtra(EXTRA_CONF, 0.0),
                severity = com.example.leafyapp.api.DiseaseSeverity(intent.getDoubleExtra(EXTRA_AREA, 0.0), intent.getStringExtra(EXTRA_LEVEL)),
                note = intent.getStringExtra(EXTRA_NOTE)))
        } else viewModel.load(plantId)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCamera.setOnClickListener { openCamera() }
        binding.btnGallery.setOnClickListener { launchGallery() }
        binding.btnAnalyze.setOnClickListener { viewModel.analyze() }
        binding.btnErrorRetry.setOnClickListener { viewModel.analyze() }
        binding.btnErrorBack.setOnClickListener { viewModel.backToPick() }
        binding.btnResultRetake.setOnClickListener { viewModel.backToPick() }
        binding.btnSave.setOnClickListener { viewModel.save() }

        viewModel.state.observe(this) { render(it) }
    }

    // ---------------- ảnh ----------------
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "diagnose").apply { mkdirs() }
        val f = File(dir, "leaf_${System.currentTimeMillis()}.jpg").also { cameraFile = it }
        val uri = FileProvider.getUriForFile(this, "$packageName.leafy.fileprovider", f)
        takePicture.launch(uri)
    }

    private fun launchGallery() {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun copyToCache(uri: Uri): String? = runCatching {
        val dir = File(cacheDir, "diagnose").apply { mkdirs() }
        val f = File(dir, "pick_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        f.absolutePath
    }.getOrNull()

    // ---------------- hiển thị ----------------
    private fun showOnly(v: View) {
        listOf(binding.layoutPick, binding.layoutLoading, binding.layoutError, binding.layoutResult)
            .forEach { it.visibility = if (it === v) View.VISIBLE else View.GONE }
    }

    private fun load(img: ImageView, path: String) = Glide.with(this).load(path).centerCrop().into(img)

    private fun render(s: DiagnoseUiState) {
        when (s) {
            is DiagnoseUiState.Pick -> {
                showOnly(binding.layoutPick)
                binding.tvPlantName.text = s.plant?.name.orEmpty()
                if (s.photoPath != null) load(binding.imgPick, s.photoPath)
                binding.tvPickHint.visibility = if (s.photoPath == null) View.VISIBLE else View.GONE
                binding.btnAnalyze.isEnabled = s.plant != null && s.photoPath != null
            }
            is DiagnoseUiState.Loading -> { showOnly(binding.layoutLoading); load(binding.imgLoading, s.photoPath) }
            is DiagnoseUiState.Error -> {
                showOnly(binding.layoutError)
                binding.tvError.text = s.message
                binding.btnErrorRetry.visibility = if (s.canRetry) View.VISIBLE else View.GONE
            }
            is DiagnoseUiState.Result -> renderResult(s)
            is DiagnoseUiState.Saved -> {
                Toast.makeText(this,
                    if (s.taskCount > 0) getString(R.string.diagnose_saved, s.taskCount) else getString(R.string.diagnose_saved_no_tasks),
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun renderResult(s: DiagnoseUiState.Result) {
        showOnly(binding.layoutResult)
        val d = s.draft
        load(binding.imgResult, s.photoPath)
        val conf = ImageUtils.percent(d.confidence)
        val lesion = fmt(d.lesionAreaPercent)

        binding.tvDiseaseName.text = when {
            d.isHealthy -> getString(R.string.diagnose_healthy)
            d.disease == null -> getString(R.string.diagnose_unknown)
            else -> d.disease.nameVi
        }
        binding.tvSeverity.visibility = if (d.disease != null) View.VISIBLE else View.GONE
        binding.tvSeverity.text = severityText(d.severity)
        binding.tvMetrics.text = if (d.disease != null) getString(R.string.diagnose_metrics, conf, lesion)
        else getString(R.string.diagnose_metrics_healthy, conf)

        val previewLesion = if (d.isHealthy) 0.0 else d.lesionAreaPercent
        val trend = TreatmentPlanBuilder.trend(d.reliable, previewLesion, d.previousReliable)
        binding.tvTrend.visibility = if (trend != null) View.VISIBLE else View.GONE
        if (trend != null) {
            val before = fmt(d.previousReliable!!.lesionAreaPercent)
            binding.tvTrend.text = getString(when (trend) {
                Diagnosis.TREND_IMPROVED -> R.string.trend_improved
                Diagnosis.TREND_WORSE -> R.string.trend_worse
                else -> R.string.trend_stable
            }, before, fmt(previewLesion))
        }

        binding.cardUncertain.visibility = if (d.reliable) View.GONE else View.VISIBLE
        binding.tvUncertainBody.text = listOfNotNull(d.modelNote, getString(R.string.diagnose_uncertain_body)).joinToString("\n")

        renderSections(s)

        binding.btnSave.isEnabled = !s.saving
        binding.btnSave.text = getString(when {
            s.saving -> R.string.diagnose_saving
            d.snapshot == null -> R.string.diagnose_save_healthy
            else -> R.string.diagnose_save
        })
        binding.tvSaveError.visibility = if (s.saveError != null) View.VISIBLE else View.GONE
        binding.tvSaveError.text = s.saveError
    }

    private fun renderSections(s: DiagnoseUiState.Result) {
        val c = binding.containerSections
        c.removeAllViews()
        val inf = LayoutInflater.from(this)
        val d = s.draft
        val plan = d.snapshot

        if (plan == null) {
            section(c, inf, getString(if (d.isHealthy) R.string.diagnose_section_healthy else R.string.diagnose_unknown)) {
                it.addView(text(getString(if (d.isHealthy) R.string.diagnose_healthy_body else R.string.diagnose_unknown_body)))
            }
            return
        }
        if (plan.normalCondition && plan.normalNote.isNotBlank()) {
            section(c, inf, getString(R.string.diagnose_section_normal)) { it.addView(text(plan.normalNote)) }
        }
        if (plan.likelyCauses.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_causes)) { b ->
            plan.likelyCauses.forEach { b.addView(text("• $it")) }
        }
        section(c, inf, getString(R.string.diagnose_section_care)) { b -> plan.careInfo.forEach { b.addView(text("• $it")) } }

        if (plan.tasks.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_tasks)) { b ->
            b.addView(text(getString(R.string.diagnose_section_tasks_hint), secondary = true))
            plan.tasks.forEach { t ->
                val icon = SpeciesInfoRenderer.taskIcon(CareTaskType.parse(t.taskType))
                val due = if (t.dueInDays <= 0) getString(R.string.task_due_today) else getString(R.string.task_due_in_days, t.dueInDays)
                val repeat = if (t.occurrences > 1) getString(R.string.task_repeat, t.repeatIntervalDays, t.occurrences) else getString(R.string.task_once)
                val prio = when (t.priority) {
                    com.example.leafyapp.data.model.PlanTask.PRIORITY_URGENT -> " · " + getString(R.string.priority_urgent)
                    com.example.leafyapp.data.model.PlanTask.PRIORITY_HIGH -> " · " + getString(R.string.priority_high)
                    else -> ""
                }
                b.addView(check("$icon ${t.title}", "${t.description}\n$due · $repeat$prio", t.key in s.selectedTasks) {
                    viewModel.toggleTask(t.key)
                })
            }
        }

        if (d.proposedChanges.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_changes)) { b ->
            d.proposedChanges.forEach { ch ->
                val name = ch.title.ifBlank { ch.taskType }
                val title = if (ch.action == PlanAdjustment.ACTION_PAUSE) getString(R.string.change_pause, name, ch.pauseDays)
                else getString(R.string.change_scale, name, ch.beforeIntervalDays, ch.afterIntervalDays)
                b.addView(check(title, ch.reason, ch.scheduleId in s.appliedChanges) { viewModel.toggleChange(ch.scheduleId) })
            }
        }
        if (plan.avoid.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_avoid)) { b ->
            plan.avoid.forEach { b.addView(text("⚠ $it")) }
        }
        if (plan.prevention.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_prevention)) { b ->
            plan.prevention.forEach { b.addView(text("• $it")) }
        }
        if (plan.safetyNote.isNotBlank()) section(c, inf, getString(R.string.diagnose_section_safety)) { it.addView(text(plan.safetyNote)) }
    }

    private fun section(container: LinearLayout, inf: LayoutInflater, title: String, build: (LinearLayout) -> Unit) {
        val v = inf.inflate(R.layout.item_info_section, container, false)
        v.findViewById<TextView>(R.id.tvSectionTitle).text = title
        build(v.findViewById(R.id.containerSectionBody))
        container.addView(v)
    }

    private fun text(value: String, secondary: Boolean = false) = TextView(this).apply {
        text = value
        textSize = if (secondary) 12f else 14f
        setTextColor(ContextCompat.getColor(context, if (secondary) R.color.leafy_text_secondary else R.color.leafy_text))
        setPadding(0, dp(4), 0, dp(2))
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun check(title: String, sub: String, checked: Boolean, onToggle: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(8), 0, dp(4))
        }
        val box = CheckBox(this).apply { isChecked = checked; isClickable = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(title).apply { setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(10), 0, 0) })
            if (sub.isNotBlank()) addView(text(sub, secondary = true))
        }
        row.addView(box); row.addView(col)
        row.setOnClickListener { onToggle() }
        return row
    }

    private fun severityText(s: Severity) = getString(when (s) {
        Severity.MILD -> R.string.severity_mild
        Severity.MODERATE -> R.string.severity_moderate
        Severity.SEVERE -> R.string.severity_severe
        Severity.NONE -> R.string.severity_none
    })

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
