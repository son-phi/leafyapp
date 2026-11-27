package com.example.leafyapp.ui.identify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.databinding.ActivityIdentifyBinding
import com.example.leafyapp.ui.addplant.AddPlantActivity
import com.example.leafyapp.util.ImageUtils

/**
 * Luồng 8.1 Nhận diện loài cây: xem trước ảnh → nhận diện → kết quả (thông tin loài + chăm sóc)
 * → "Thêm cây trồng" (8.3).
 */
class IdentifyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "PHOTO_PATH"

        fun start(context: Context, photoPath: String) {
            context.startActivity(Intent(context, IdentifyActivity::class.java).putExtra(EXTRA_PHOTO_PATH, photoPath))
        }
    }

    private lateinit var binding: ActivityIdentifyBinding
    private val viewModel: IdentifyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIdentifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_PHOTO_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        viewModel.setPhoto(path)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetake.setOnClickListener { finish() }
        binding.btnErrorRetake.setOnClickListener { finish() }
        binding.btnResultRetake.setOnClickListener { finish() }
        binding.btnIdentify.setOnClickListener { viewModel.identify() }
        binding.btnErrorRetry.setOnClickListener { viewModel.identify() }
        binding.btnPickFromCatalog.setOnClickListener { openSpeciesPicker() }
        binding.btnAddPlant.setOnClickListener { openAddPlant() }

        supportFragmentManager.setFragmentResultListener(SpeciesPickerBottomSheet.REQUEST_KEY, this) { _, bundle ->
            bundle.getString(SpeciesPickerBottomSheet.RESULT_SPECIES_ID)?.let { viewModel.selectFromCatalog(it) }
        }

        viewModel.state.observe(this) { render(it) }
    }

    private fun showOnly(view: View) {
        listOf(binding.layoutPreview, binding.layoutLoading, binding.layoutError, binding.layoutResult)
            .forEach { it.visibility = if (it === view) View.VISIBLE else View.GONE }
    }

    private fun loadPhoto(target: ImageView, path: String) {
        Glide.with(this).load(path).centerCrop().into(target)
    }

    private fun render(state: IdentifyUiState) {
        when (state) {
            is IdentifyUiState.Preview -> {
                showOnly(binding.layoutPreview)
                loadPhoto(binding.imgPreview, state.photoPath)
            }
            is IdentifyUiState.Loading -> {
                showOnly(binding.layoutLoading)
                loadPhoto(binding.imgLoading, state.photoPath)
            }
            is IdentifyUiState.Error -> {
                showOnly(binding.layoutError)
                binding.tvErrorMessage.text = state.message
            }
            is IdentifyUiState.Result -> renderResult(state)
        }
    }

    private fun renderResult(state: IdentifyUiState.Result) {
        showOnly(binding.layoutResult)
        loadPhoto(binding.imgResultPhoto, state.photoPath)

        binding.cardUncertain.visibility = if (state.result.isReliable) View.GONE else View.VISIBLE

        // Ứng viên đang chọn mà chưa có trong danh mục
        val notInCatalog = state.selectedAiLabel != null && state.selectedSpecies == null
        binding.cardNotInCatalog.visibility = if (notInCatalog) View.VISIBLE else View.GONE
        if (notInCatalog) {
            binding.tvNotInCatalogBody.text = getString(R.string.identify_not_in_catalog_body, state.selectedAiLabel)
        }

        val species = state.selectedSpecies
        if (species != null) {
            binding.cardSpecies.visibility = View.VISIBLE
            binding.tvSpeciesName.text = species.commonName
            binding.tvScientificName.text = species.scientificName
            SpeciesInfoRenderer.loadSpeciesImage(binding.imgSpecies, species.imageUrl)
            val conf = state.selectedCandidateConfidence
            binding.tvConfidence.visibility = if (conf != null) View.VISIBLE else View.GONE
            if (conf != null) binding.tvConfidence.text = getString(R.string.identify_confidence, ImageUtils.percent(conf))
            binding.tvCareLevel.text = SpeciesInfoRenderer.careLevelText(this, species.careLevel)
            SpeciesInfoRenderer.render(binding.containerSpeciesInfo, species)
        } else {
            binding.cardSpecies.visibility = View.GONE
            binding.containerSpeciesInfo.removeAllViews()
        }

        renderCandidates(state)

        // Ẩn nút Thêm cây trồng khi chưa có loài hợp lệ trong danh mục (8.1 ngoại lệ)
        binding.btnAddPlant.visibility = if (species != null) View.VISIBLE else View.GONE
        if (species == null && !notInCatalog) {
            binding.tvCandidatesTitle.text = getString(R.string.identify_choose_species_first)
        } else {
            binding.tvCandidatesTitle.text = getString(R.string.identify_candidates_title)
        }
    }

    private fun renderCandidates(state: IdentifyUiState.Result) {
        val container = binding.containerCandidates
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        state.result.candidates.forEach { c ->
            val view = inflater.inflate(R.layout.item_species_candidate, container, false)
            view.findViewById<TextView>(R.id.tvCandidateName).text = c.species?.commonName ?: c.aiLabel
            view.findViewById<TextView>(R.id.tvCandidateSub).text =
                c.species?.scientificName ?: getString(R.string.identify_not_in_catalog_short)
            view.findViewById<TextView>(R.id.tvCandidateConfidence).text =
                getString(R.string.identify_confidence, ImageUtils.percent(c.confidence))
            SpeciesInfoRenderer.loadSpeciesImage(view.findViewById(R.id.imgCandidate), c.species?.imageUrl)
            val selected = !state.fromCatalog && c.aiLabel == state.selectedAiLabel
            view.background = ContextCompat.getDrawable(
                this, if (selected) R.drawable.bg_leafy_card_selected else R.drawable.bg_leafy_card
            )
            view.setOnClickListener { viewModel.selectCandidate(c.aiLabel) }
            container.addView(view)
        }
    }

    private fun openSpeciesPicker() {
        if (supportFragmentManager.findFragmentByTag(SpeciesPickerBottomSheet.TAG) == null) {
            SpeciesPickerBottomSheet().show(supportFragmentManager, SpeciesPickerBottomSheet.TAG)
        }
    }

    private fun openAddPlant() {
        val state = viewModel.state.value as? IdentifyUiState.Result ?: return
        val species = state.selectedSpecies ?: return
        AddPlantActivity.start(
            this,
            speciesId = species.id,
            photoPath = viewModel.compressedFile?.absolutePath,
            identificationId = viewModel.identificationId
        )
    }
}
