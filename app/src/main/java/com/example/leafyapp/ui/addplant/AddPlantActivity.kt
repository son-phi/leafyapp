package com.example.leafyapp.ui.addplant

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.leafyapp.MainActivity
import com.example.leafyapp.R
import com.example.leafyapp.databinding.ActivityAddPlantBinding
import com.example.leafyapp.ui.identify.SpeciesInfoRenderer
import com.example.leafyapp.ui.identify.SpeciesPickerBottomSheet
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Luồng 8.3 Thêm cây trồng. Mở từ kết quả nhận diện (loài + ảnh điền sẵn) hoặc từ vườn. */
class AddPlantActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SPECIES_ID = "SPECIES_ID"
        private const val EXTRA_PHOTO_PATH = "PHOTO_PATH"
        private const val EXTRA_IDENTIFICATION_ID = "IDENTIFICATION_ID"
        private const val EXTRA_RETURN_RESULT = "RETURN_RESULT"
        const val RESULT_PLANT_ID = "PLANT_ID"

        /** Mở để thêm cây rồi trả plantId về màn gọi (luồng chẩn đoán từ màn hình chính 8.2). */
        fun intentForResult(context: Context, speciesId: String, photoPath: String?): Intent =
            Intent(context, AddPlantActivity::class.java)
                .putExtra(EXTRA_SPECIES_ID, speciesId)
                .putExtra(EXTRA_PHOTO_PATH, photoPath)
                .putExtra(EXTRA_RETURN_RESULT, true)

        fun start(context: Context, speciesId: String? = null, photoPath: String? = null, identificationId: String? = null) {
            context.startActivity(
                Intent(context, AddPlantActivity::class.java)
                    .putExtra(EXTRA_SPECIES_ID, speciesId)
                    .putExtra(EXTRA_PHOTO_PATH, photoPath)
                    .putExtra(EXTRA_IDENTIFICATION_ID, identificationId)
            )
        }
    }

    private lateinit var binding: ActivityAddPlantBinding
    private val viewModel: AddPlantViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
    private var nameEditedByUser = false
    private var settingNameProgrammatically = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setPhoto(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPlantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.init(
            intent.getStringExtra(EXTRA_SPECIES_ID),
            intent.getStringExtra(EXTRA_PHOTO_PATH),
            intent.getStringExtra(EXTRA_IDENTIFICATION_ID)
        )

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickPhoto.setOnClickListener { pickPhoto.launch("image/*") }
        binding.btnRemovePhoto.setOnClickListener { viewModel.setPhoto(null) }
        binding.btnChangeSpecies.setOnClickListener { openSpeciesPicker() }
        binding.cardSpecies.setOnClickListener { openSpeciesPicker() }
        binding.etPlantedDate.setOnClickListener { pickPlantedDate() }
        binding.btnSave.setOnClickListener { onSaveClicked(uploadPhoto = true) }

        binding.etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!settingNameProgrammatically) nameEditedByUser = true
                binding.tilName.error = null
            }
        })

        supportFragmentManager.setFragmentResultListener(SpeciesPickerBottomSheet.REQUEST_KEY, this) { _, bundle ->
            bundle.getString(SpeciesPickerBottomSheet.RESULT_SPECIES_ID)?.let { viewModel.selectSpecies(it) }
        }

        observe()
    }

    private fun observe() {
        viewModel.species.observe(this) { s ->
            binding.btnSave.isEnabled = s != null
            if (s == null) {
                binding.tvSpeciesName.text = getString(R.string.add_plant_species_required)
                binding.tvSpeciesSci.text = ""
                binding.cardThreshold.visibility = View.GONE
                return@observe
            }
            binding.tvSpeciesName.text = s.commonName
            binding.tvSpeciesSci.text = s.scientificName
            SpeciesInfoRenderer.loadSpeciesImage(binding.imgSpecies, s.imageUrl)
            if (!nameEditedByUser || binding.etName.text.isNullOrBlank()) {
                settingNameProgrammatically = true
                binding.etName.setText(viewModel.suggestedName)
                binding.etName.setSelection(binding.etName.text?.length ?: 0)
                settingNameProgrammatically = false
                nameEditedByUser = false
            }
            binding.cardThreshold.visibility = View.VISIBLE
            binding.containerThreshold.removeAllViews()
            SpeciesInfoRenderer.thresholdRows(binding.containerThreshold, LayoutInflater.from(this), s.threshold)
        }

        viewModel.photoPath.observe(this) { path ->
            if (path != null) {
                Glide.with(this).load(path).centerCrop().into(binding.imgPlantPhoto)
                binding.btnPickPhoto.text = getString(R.string.add_plant_photo_change)
                binding.btnRemovePhoto.visibility = View.VISIBLE
            } else {
                binding.imgPlantPhoto.setImageResource(R.drawable.leaf_solid_full)
                binding.imgPlantPhoto.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                binding.btnPickPhoto.text = getString(R.string.add_plant_photo_add)
                binding.btnRemovePhoto.visibility = View.GONE
            }
        }

        viewModel.plantedDate.observe(this) { date ->
            binding.etPlantedDate.setText(dateFormat.format(date))
            binding.tilPlantedDate.error = if (viewModel.isPlantedDateValid(date)) null else getString(R.string.add_plant_date_future)
        }

        viewModel.drafts.observe(this) { renderDrafts(it) }

        viewModel.loadError.observe(this) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        viewModel.saveState.observe(this) { state ->
            val saving = state is SaveState.Saving
            binding.btnSave.isEnabled = !saving && viewModel.species.value != null
            binding.btnSave.text = getString(if (saving) R.string.add_plant_saving else R.string.add_plant_save)
            when (state) {
                is SaveState.Success -> {
                    // QT08: chỉ báo thành công sau khi Firestore xác nhận
                    Toast.makeText(this, R.string.add_plant_success, Toast.LENGTH_SHORT).show()
                    if (intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)) {
                        setResult(RESULT_OK, Intent().putExtra(RESULT_PLANT_ID, state.plantId))
                        finish()
                        return@observe
                    }
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("OPEN_GARDEN", true)
                    })
                    finish()
                }
                is SaveState.Error -> {
                    viewModel.consumeSaveState()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.add_plant_error_title)
                        .setMessage(state.message)
                        .setPositiveButton(R.string.common_retry) { _, _ -> onSaveClicked(uploadPhoto = true) }
                        .setNegativeButton(R.string.common_cancel, null)
                        .show()
                }
                is SaveState.UploadError -> {
                    viewModel.consumeSaveState()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.add_plant_upload_error_title)
                        .setMessage(getString(R.string.add_plant_upload_error_body, state.message))
                        .setPositiveButton(R.string.common_retry) { _, _ -> onSaveClicked(uploadPhoto = true) }
                        .setNeutralButton(R.string.add_plant_save_without_photo) { _, _ -> onSaveClicked(uploadPhoto = false) }
                        .setNegativeButton(R.string.common_cancel, null)
                        .show()
                }
                else -> Unit
            }
        }
    }

    private fun renderDrafts(drafts: List<ScheduleDraftUi>) {
        val container = binding.containerSchedules
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        drafts.forEach { d ->
            val v = inflater.inflate(R.layout.item_schedule_draft, container, false)
            v.findViewById<TextView>(R.id.tvDraftIcon).text = SpeciesInfoRenderer.taskIcon(d.taskType)
            v.findViewById<TextView>(R.id.tvDraftTitle).text = d.title
            v.findViewById<TextView>(R.id.tvDraftNote).text = d.note
            val fields = v.findViewById<View>(R.id.layoutDraftFields)
            fields.visibility = if (d.enabled) View.VISIBLE else View.GONE

            v.findViewById<MaterialSwitch>(R.id.switchDraft).apply {
                isChecked = d.enabled
                setOnCheckedChangeListener { _, checked -> viewModel.updateDraft(d.id) { it.copy(enabled = checked) } }
            }

            val til = v.findViewById<TextInputLayout>(R.id.tilDraftInterval)
            v.findViewById<EditText>(R.id.etDraftInterval).apply {
                setText(d.intervalDays.toString())
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val days = s?.toString()?.toIntOrNull()
                        if (days == null || days !in 1..365) {
                            til.error = getString(R.string.add_plant_interval_invalid)
                        } else {
                            til.error = null
                            // Cập nhật không vẽ lại danh sách để không mất con trỏ khi gõ
                            viewModel.updateDraftSilently(d.id, days)
                        }
                    }
                })
            }

            v.findViewById<MaterialButton>(R.id.btnDraftStart).apply {
                text = dateFormat.format(d.startDate)
                setOnClickListener { pickDate(d.startDate, null) { date -> viewModel.updateDraft(d.id) { it.copy(startDate = date) } } }
            }
            v.findViewById<MaterialButton>(R.id.btnDraftTime).apply {
                text = d.timeOfDay
                setOnClickListener { pickTime(d.timeOfDay) { time -> viewModel.updateDraft(d.id) { it.copy(timeOfDay = time) } } }
            }
            container.addView(v)
        }
    }

    private fun pickPlantedDate() {
        pickDate(viewModel.plantedDate.value ?: Date(), maxDate = Date()) { viewModel.setPlantedDate(it) }
    }

    private fun pickDate(initial: Date, maxDate: Date?, onPicked: (Date) -> Unit) {
        val cal = Calendar.getInstance().apply { time = initial }
        DatePickerDialog(this, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }.time)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            if (maxDate != null) datePicker.maxDate = maxDate.time
        }.show()
    }

    private fun pickTime(current: String, onPicked: (String) -> Unit) {
        val parts = current.split(":")
        TimePickerDialog(this, { _, h, min ->
            onPicked("%02d:%02d".format(h, min))
        }, parts.getOrNull(0)?.toIntOrNull() ?: 8, parts.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
    }

    private fun openSpeciesPicker() {
        if (viewModel.saveState.value is SaveState.Saving) return
        if (supportFragmentManager.findFragmentByTag(SpeciesPickerBottomSheet.TAG) == null) {
            SpeciesPickerBottomSheet().show(supportFragmentManager, SpeciesPickerBottomSheet.TAG)
        }
    }

    private fun onSaveClicked(uploadPhoto: Boolean) {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        var ok = true
        if (viewModel.species.value == null) {
            Toast.makeText(this, R.string.add_plant_species_required, Toast.LENGTH_SHORT).show()
            ok = false
        }
        if (name.isBlank()) {
            binding.tilName.error = getString(R.string.add_plant_name_required)
            ok = false
        }
        if (!viewModel.isPlantedDateValid()) {
            binding.tilPlantedDate.error = getString(R.string.add_plant_date_future)
            ok = false
        }
        if (viewModel.draftsSnapshot.any { it.enabled && it.intervalDays !in 1..365 }) {
            Toast.makeText(this, R.string.add_plant_interval_invalid, Toast.LENGTH_SHORT).show()
            ok = false
        }
        if (ok) viewModel.save(name, uploadPhoto)
    }
}
