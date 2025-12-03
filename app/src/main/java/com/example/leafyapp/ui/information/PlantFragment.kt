package com.example.leafyapp.ui.information

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.MainActivity
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentPlantBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import java.io.File

class PlantFragment : Fragment() {

    private var _binding: FragmentPlantBinding? = null
    private val binding get() = _binding!!

    private val gardenViewModel: GardenViewModel by viewModels()

    private var plantId: Int = -1
    private var plantLabel: String = "Unknown"
    private var plantConfidence: Float = 0f

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>
    private var currentDisplayPlant: Plant? = null

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
        inflater: LayoutInflater, container: ViewGroup?,
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

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener { requireActivity().finish() }
    }

    private fun setupAddButton(plant: Plant) {
        binding.btnAddPlant.text = "Thêm vào vườn"
        binding.btnAddPlant.isEnabled = true
        binding.btnAddPlant.alpha = 1.0f

        binding.btnAddPlant.setOnClickListener {
            showQuantityDialog(plant)
        }
    }

    private fun showQuantityDialog(plant: Plant) {
        val numberPicker = NumberPicker(requireContext())
        numberPicker.minValue = 1
        numberPicker.maxValue = 10
        numberPicker.value = 1
        numberPicker.wrapSelectorWheel = false

        val layout = android.widget.FrameLayout(requireContext())
        layout.addView(numberPicker, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        ))

        AlertDialog.Builder(requireContext())
            .setTitle("Chọn số lượng")
            .setMessage("Bạn muốn thêm bao nhiêu cây ${plant.name}?")
            .setView(layout)
            .setPositiveButton("Thêm") { _, _ ->
                val quantity = numberPicker.value
                addMultiplePlantsToGarden(plant, quantity)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /// --- LOGIC MỚI: TỰ ĐỘNG ĐÁNH SỐ THỨ TỰ ---
    private fun addMultiplePlantsToGarden(plantToSave: Plant, quantity: Int) {
        lifecycleScope.launch {
            // 1. Đếm số lượng cây hiện có trong DB trước
            val currentCount = gardenViewModel.getPlantCount(plantToSave.id)
            val baseName = plantToSave.name

            for (i in 1..quantity) {
                // 2. Tính số thứ tự mới = Số hiện có + i
                val newIndex = currentCount + i

                // Nếu chỉ có 1 cây duy nhất (và chưa có cây nào trước đó), có thể không cần số
                // Nhưng để thống nhất, cứ thêm số nếu muốn: "Hoa hồng 1", "Hoa hồng 2"
                // Hoặc logic: Nếu currentCount == 0 && quantity == 1 -> Giữ nguyên tên

                val finalName = if (currentCount == 0 && quantity == 1) baseName else "$baseName $newIndex"

                val newUserPlant = UserPlant(
                    plantId = plantToSave.id,
                    nickname = finalName,
                    imagePath = plantToSave.image
                )
                gardenViewModel.insert(newUserPlant)
            }

            Toast.makeText(context, "Đã thêm $quantity cây vào vườn!", Toast.LENGTH_SHORT).show()

            // --- SỬA ĐỔI TẠI ĐÂY: CHUYỂN VỀ MY GARDEN ---
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            // Gửi tín hiệu để MainActivity mở tab Garden
            intent.putExtra("OPEN_MY_GARDEN", true)

            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun convertDrive(url: String): String {
        return if (url.contains("drive.google.com")) {
            try {
                val id = url.substringAfter("d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) { url }
        } else url
    }

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

        currentDisplayPlant = plant
        displayPlantInfo(plant)
        setupAddButton(plant)
    }

    private fun showError(msg: String) {
        binding.tvPlantName.text = "Lỗi"
        binding.tvDescription.text = msg
        binding.btnAddPlant.isEnabled = false
    }

    private fun displayPlantInfo(plant: Plant) {
        val context = binding.root.context
        val resId = context.resources.getIdentifier(plant.image, "drawable", context.packageName)

        if (resId != 0) {
            binding.imgPlant.load(resId) { crossfade(true) }
        } else {
            binding.imgPlant.load(convertDrive(plant.image ?: "")) { crossfade(true) }
        }

        binding.tvPlantName.text = plant.name
        binding.tvScientificName.text = plant.scientificName
        binding.tvDescription.text = plant.description ?: "Đang cập nhật..."
        binding.tvLight.text = "☀️ Ánh sáng: ${plant.light ?: "N/A"}"
        binding.tvWater.text = "💧 Tưới nước: ${plant.watering ?: "N/A"}"
        binding.tvSoil.text = "🪨 Đất: ${plant.soil ?: "N/A"}"
        binding.tvFertilizer.text = "🧪 Phân bón: ${plant.fertilizer ?: "N/A"}"
        binding.tvTemp.text = "🌡️ Nhiệt độ: ${plant.temperature ?: "N/A"}"
        binding.tvHumidity.text = "💦 Độ ẩm: ${plant.humidity ?: "N/A"}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}