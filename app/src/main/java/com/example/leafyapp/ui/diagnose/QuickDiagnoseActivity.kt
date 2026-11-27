package com.example.leafyapp.ui.diagnose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareTaskType
import com.example.leafyapp.data.model.Severity
import com.example.leafyapp.databinding.ActivityQuickDiagnoseBinding
import com.example.leafyapp.ui.addplant.AddPlantActivity
import com.example.leafyapp.ui.identify.SpeciesInfoRenderer
import com.example.leafyapp.ui.identify.SpeciesPickerBottomSheet
import com.example.leafyapp.util.ImageUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 8.2 Chẩn đoán bệnh trên lá từ màn hình chính: loài + bệnh + hướng xử lý theo loài × bệnh × mức độ + thông tin loài.
 * Từ đây: "Thêm cây trồng" (tạo cây rồi gắn chẩn đoán) hoặc "Ảnh này là cây trong vườn của tôi".
 * Kết quả chỉ được lưu khi gắn vào một cây (QT03) – bước lưu dùng chung màn [DiagnoseActivity].
 */
class QuickDiagnoseActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PHOTO = "PHOTO_PATH"
        fun start(context: Context, photoPath: String) {
            context.startActivity(Intent(context, QuickDiagnoseActivity::class.java).putExtra(EXTRA_PHOTO, photoPath))
        }
    }

    private lateinit var binding: ActivityQuickDiagnoseBinding
    private val viewModel: QuickDiagnoseViewModel by viewModels()

    private val addPlant = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val plantId = res.data?.getStringExtra(AddPlantActivity.RESULT_PLANT_ID)
        if (res.resultCode == RESULT_OK && plantId != null) attachTo(plantId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickDiagnoseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val path = intent.getStringExtra(EXTRA_PHOTO)
        if (path.isNullOrBlank()) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnErrorBack.setOnClickListener { finish() }
        binding.btnErrorRetry.setOnClickListener { viewModel.analyze() }
        binding.btnPickSpecies.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(SpeciesPickerBottomSheet.TAG) == null)
                SpeciesPickerBottomSheet().show(supportFragmentManager, SpeciesPickerBottomSheet.TAG)
        }
        supportFragmentManager.setFragmentResultListener(SpeciesPickerBottomSheet.REQUEST_KEY, this) { _, b ->
            b.getString(SpeciesPickerBottomSheet.RESULT_SPECIES_ID)?.let { viewModel.selectSpecies(it) }
        }
        binding.btnAddPlant.setOnClickListener { onAddPlant() }
        binding.btnMyPlant.setOnClickListener { pickMyPlant() }

        viewModel.state.observe(this) { render(it) }
        viewModel.start(path)
    }

    private fun showOnly(v: View) {
        listOf(binding.layoutLoading, binding.layoutError, binding.layoutResult)
            .forEach { it.visibility = if (it === v) View.VISIBLE else View.GONE }
    }

    private fun render(s: QuickUiState) {
        when (s) {
            is QuickUiState.Loading -> {
                showOnly(binding.layoutLoading)
                Glide.with(this).load(s.photoPath).centerCrop().into(binding.imgLoading)
            }
            is QuickUiState.Error -> { showOnly(binding.layoutError); binding.tvError.text = s.message }
            is QuickUiState.Result -> renderResult(s)
        }
    }

    private fun renderResult(s: QuickUiState.Result) {
        showOnly(binding.layoutResult)
        Glide.with(this).load(s.photoPath).centerCrop().into(binding.imgResult)
        val d = s.disease
        val healthy = d.diseaseId == com.example.leafyapp.data.model.HEALTHY_AI_LABEL
        binding.tvDiseaseName.text = when {
            healthy -> getString(R.string.diagnose_healthy)
            s.diseaseInfo == null -> getString(R.string.diagnose_unknown)
            else -> s.diseaseInfo.nameVi
        }
        binding.tvSeverity.visibility = if (s.diseaseInfo != null) View.VISIBLE else View.GONE
        binding.tvSeverity.text = getString(when (s.severity) {
            Severity.MILD -> R.string.severity_mild
            Severity.MODERATE -> R.string.severity_moderate
            Severity.SEVERE -> R.string.severity_severe
            Severity.NONE -> R.string.severity_none
        })
        val conf = ImageUtils.percent(d.confidence)
        binding.tvMetrics.text = if (s.diseaseInfo != null)
            getString(R.string.diagnose_metrics, conf, String.format(Locale.US, "%.1f", d.severity?.percentArea ?: 0.0))
        else getString(R.string.diagnose_metrics_healthy, conf)
        val reliable = d.confidence >= 60.0 && d.note.isNullOrBlank()
        binding.cardUncertain.visibility = if (reliable) View.GONE else View.VISIBLE
        binding.tvUncertainBody.text = listOfNotNull(d.note, getString(R.string.diagnose_uncertain_body)).joinToString("\n")

        renderCandidates(s)
        renderPlan(s, healthy)
        binding.containerSpeciesInfo.removeAllViews()
        s.species?.let { SpeciesInfoRenderer.render(binding.containerSpeciesInfo, it) }
    }

    private fun renderCandidates(s: QuickUiState.Result) {
        val box = binding.containerCandidates
        box.removeAllViews()
        val reliable = s.identify?.isReliable == true
        binding.tvSpeciesHint.text = getString(if (reliable) R.string.quick_species_reliable else R.string.quick_species_uncertain)
        val inf = LayoutInflater.from(this)
        val candidates = s.identify?.candidates.orEmpty()
        // Loài chọn từ danh mục mà không nằm trong top 3 → hiện thêm một dòng cho loài đó
        val extra = s.species?.takeIf { sp -> candidates.none { it.species?.id == sp.id } }
        extra?.let { sp -> box.addView(candidateView(inf, box, sp.commonName, sp.scientificName, null, sp.imageUrl, true) {}) }
        candidates.forEach { c ->
            val selected = c.species != null && c.species.id == s.species?.id
            box.addView(candidateView(inf, box, c.species?.commonName ?: c.aiLabel,
                c.species?.scientificName ?: getString(R.string.identify_not_in_catalog_short),
                getString(R.string.identify_confidence, ImageUtils.percent(c.confidence)), c.species?.imageUrl, selected) {
                viewModel.selectCandidate(c.aiLabel)
            })
        }
    }

    private fun candidateView(inf: LayoutInflater, parent: LinearLayout, name: String, sub: String, conf: String?,
                              image: String?, selected: Boolean, onClick: () -> Unit): View {
        val v = inf.inflate(R.layout.item_species_candidate, parent, false)
        v.findViewById<TextView>(R.id.tvCandidateName).text = name
        v.findViewById<TextView>(R.id.tvCandidateSub).text = sub
        v.findViewById<TextView>(R.id.tvCandidateConfidence).apply { text = conf.orEmpty(); visibility = if (conf == null) View.GONE else View.VISIBLE }
        SpeciesInfoRenderer.loadSpeciesImage(v.findViewById(R.id.imgCandidate), image)
        v.background = ContextCompat.getDrawable(this, if (selected) R.drawable.bg_leafy_card_selected else R.drawable.bg_leafy_card)
        v.setOnClickListener { onClick() }
        return v
    }

    private fun renderPlan(s: QuickUiState.Result, healthy: Boolean) {
        val c = binding.containerSections
        c.removeAllViews()
        val inf = LayoutInflater.from(this)
        val plan = s.plan
        if (plan == null) {
            section(c, inf, getString(if (healthy) R.string.diagnose_section_healthy else R.string.diagnose_unknown)) {
                it.addView(text(getString(if (healthy) R.string.diagnose_healthy_body else R.string.diagnose_unknown_body)))
            }
            return
        }
        if (s.species == null) c.addView(text(getString(R.string.quick_plan_generic), secondary = true))
        if (plan.normalCondition && plan.normalNote.isNotBlank())
            section(c, inf, getString(R.string.diagnose_section_normal)) { it.addView(text(plan.normalNote)) }
        if (plan.likelyCauses.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_causes)) { b ->
            plan.likelyCauses.forEach { b.addView(text("• $it")) }
        }
        section(c, inf, getString(R.string.diagnose_section_care)) { b -> plan.careInfo.forEach { b.addView(text("• $it")) } }
        if (plan.tasks.isNotEmpty()) section(c, inf, getString(R.string.diagnose_section_tasks)) { b ->
            plan.tasks.forEach { t ->
                val due = if (t.dueInDays <= 0) getString(R.string.task_due_today) else getString(R.string.task_due_in_days, t.dueInDays)
                val repeat = if (t.occurrences > 1) getString(R.string.task_repeat, t.repeatIntervalDays, t.occurrences) else getString(R.string.task_once)
                b.addView(text("${SpeciesInfoRenderer.taskIcon(CareTaskType.parse(t.taskType))} ${t.title} – $due · $repeat"))
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

    // ---------------- hành động ----------------
    private fun onAddPlant() {
        val s = viewModel.state.value as? QuickUiState.Result ?: return
        val sp = s.species ?: run {
            Toast.makeText(this, R.string.quick_need_species, Toast.LENGTH_LONG).show(); return
        }
        addPlant.launch(AddPlantActivity.intentForResult(this, sp.id, viewModel.compressed?.absolutePath ?: s.photoPath))
    }

    private fun pickMyPlant() {
        val s = viewModel.state.value as? QuickUiState.Result ?: return
        lifecycleScope.launch {
            val plants = runCatching { viewModel.myPlants() }.getOrElse {
                Toast.makeText(this@QuickDiagnoseActivity, it.message, Toast.LENGTH_LONG).show(); return@launch
            }
            if (plants.isEmpty()) {
                Toast.makeText(this@QuickDiagnoseActivity, R.string.quick_no_plants, Toast.LENGTH_LONG).show(); return@launch
            }
            // Cây cùng loài với kết quả nhận diện lên đầu
            val sorted = plants.sortedWith(compareByDescending<com.example.leafyapp.data.model.PlantProfile> { it.speciesId == s.species?.id }.thenBy { it.name })
            val labels = sorted.map { p ->
                val same = if (p.speciesId == s.species?.id) " · " + getString(R.string.quick_same_species) else ""
                "${p.name}$same"
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@QuickDiagnoseActivity)
                .setTitle(R.string.quick_pick_plant_title)
                .setItems(labels) { _, i -> attachTo(sorted[i].id) }
                .show()
        }
    }

    /** Gắn kết quả vào cây → màn kiểm tra sức khỏe cây để chọn việc điều trị, đổi lịch rồi lưu. */
    private fun attachTo(plantId: String) {
        val s = viewModel.state.value as? QuickUiState.Result ?: return
        DiagnoseActivity.startWithResult(this, plantId, viewModel.compressed?.absolutePath ?: s.photoPath, s.disease)
        finish()
    }

    // ---------------- view helpers ----------------
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
        setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, (2 * resources.displayMetrics.density).toInt())
    }
}
