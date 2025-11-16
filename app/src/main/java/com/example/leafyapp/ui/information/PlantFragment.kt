package com.example.leafyapp.ui.information

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import coil.load
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.databinding.FragmentPlantBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior

class PlantFragment : Fragment() {

    private var _binding: FragmentPlantBinding? = null
    private val binding get() = _binding!!

    private var plantId: Int = -1
    private var plantLabel: String = "Unknown"
    private var plantConfidence: Float = 0f

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    companion object {
        fun newInstance(id: Int, label: String, confidence: Float) =
            PlantFragment().apply {
                arguments = Bundle().apply {
                    putInt("ID", id)
                    putString("LABEL", label)
                    putFloat("CONFIDENCE", confidence)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        receiveArguments()
        setupBottomSheet()
        setupCloseButton()
        loadPlantFromDatabase()
    }

    private fun receiveArguments() {
        arguments?.let {
            plantId = it.getInt("ID", -1)
            plantLabel = it.getString("LABEL") ?: "Unknown"
            plantConfidence = it.getFloat("CONFIDENCE", 0f)
        }
    }

    /** =====================
     *   BOTTOM SHEET
     *  ===================== */
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        bottomSheetBehavior.peekHeight = 320   // match XML
        bottomSheetBehavior.isFitToContents = true
        bottomSheetBehavior.halfExpandedRatio = 0.70f
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        // Không cho lên full
        bottomSheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_HALF_EXPANDED
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            }
        )
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /** =====================
     *   CONVERT DRIVE LINK
     *  ===================== */
    private fun convertDrive(url: String): String {
        return if (url.contains("drive.google.com")) {
            try {
                val id = url.substringAfter("d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) { url }
        } else url
    }

    /** =====================
     *   LOAD DATA
     *  ===================== */
    private fun loadPlantFromDatabase() {
        if (plantId < 0) {
            showError("Không xác định được cây từ AI.")
            return
        }

        val ctx = context ?: return
        val db = DatabaseHelper(ctx)
        val plant = db.getPlantById(plantId + 1)

        if (plant == null) {
            showError("Không tìm thấy cây trong database.")
            return
        }

        displayPlantInfo(plant)
    }

    private fun showError(msg: String) {
        binding.tvPlantName.text = "Lỗi"
        binding.tvScientificName.text = ""
        binding.tvDescription.text = msg
    }

    /** =====================
     *   HIỂN THỊ THÔNG TIN
     *  ===================== */
    private fun displayPlantInfo(plant: Plant) {

        // Ảnh
        binding.imgPlant.load(convertDrive(plant.image ?: "")) {
            crossfade(true)
        }

        // Tên cây
        binding.tvPlantName.text = plant.name

        // Tên khoa học
        binding.tvScientificName.text = plant.scientificName

        // Mô tả
        binding.tvDescription.text = "🌿 Mô tả:\n${plant.description}"

        // Thông số
        binding.tvLight.text = "☀️ Ánh sáng: ${plant.light}"
        binding.tvWater.text = "💧 Tưới nước: ${plant.watering}"
        binding.tvSoil.text = "🪨 Đất: ${plant.soil}"
        binding.tvFertilizer.text = "🧪 Phân bón: ${plant.fertilizer}"
        binding.tvTemp.text = "🌡️ Nhiệt độ: ${plant.temperature}"
        binding.tvHumidity.text = "💦 Độ ẩm: ${plant.humidity}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
