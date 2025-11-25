package com.example.leafyapp.ui.information

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope // Import mới
import coil.load
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.MainActivity
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentPlantBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch // Import mới
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

        // Chỉ gọi loadPlantFromDatabase, việc setup nút Add sẽ làm trong đó
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
        binding.btnClose.setOnClickListener {
            requireActivity().finish()
        }
    }

    // Hàm setup nút Add dựa trên trạng thái cây
    private fun setupAddButton(plant: Plant) {
        lifecycleScope.launch {
            // Kiểm tra xem cây đã có trong vườn chưa
            val exists = gardenViewModel.checkPlantExists(plant.id)

            if (exists) {
                // Nếu ĐÃ CÓ -> Đổi text, khóa nút, làm mờ
                binding.btnAddPlant.text = "Đã có trong vườn"
                binding.btnAddPlant.isEnabled = false
                binding.btnAddPlant.alpha = 0.5f
            } else {
                // Nếu CHƯA CÓ -> Cho phép thêm
                binding.btnAddPlant.text = "Thêm vào vườn"
                binding.btnAddPlant.isEnabled = true
                binding.btnAddPlant.alpha = 1.0f

                binding.btnAddPlant.setOnClickListener {
                    addToGarden(plant)
                }
            }
        }
    }

    private fun addToGarden(plantToSave: Plant) {
        val newUserPlant = UserPlant(
            plantId = plantToSave.id,
            nickname = plantToSave.name,
            imagePath = plantToSave.image
        )

        gardenViewModel.insert(newUserPlant)

        Toast.makeText(context, "Đã thêm ${plantToSave.name} vào vườn!", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        requireActivity().finish()
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

        // Logic +1 id (do index AI bắt đầu từ 0, DB từ 1)
        val plant = db.getPlantById(plantId + 1)

        if (plant == null) {
            showError("Không tìm thấy cây trong database.")
            return
        }

        currentDisplayPlant = plant
        displayPlantInfo(plant)

        // Quan trọng: Setup nút Add sau khi đã có thông tin cây
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