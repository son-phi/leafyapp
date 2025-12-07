package com.example.leafyapp.ui.information

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide // Đổi sang Glide để đồng bộ
import com.example.leafyapp.MainActivity
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentPlantBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class PlantFragment : Fragment() {

    private var _binding: FragmentPlantBinding? = null
    private val binding get() = _binding!!

    private val gardenViewModel: GardenViewModel by viewModels()

    private var plantId: Int = -1
    private var plantLabel: String? = null
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

        // Gọi hàm tải từ Firebase thay vì DatabaseHelper
        fetchPlantFromFirebase()
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

    // --- LOGIC MỚI: LẤY TỪ FIREBASE ---
    private fun fetchPlantFromFirebase() {
        if (plantId == -1) {
            showError("ID cây không hợp lệ.")
            return
        }

        // Hiển thị tên tạm thời trong lúc chờ tải
        binding.tvPlantName.text = plantLabel ?: "Đang tải..."

        val db = FirebaseFirestore.getInstance()

        // Truy vấn Document theo ID (Lưu ý: Không cộng 1 nữa)
        db.collection("plants").document(plantId.toString())
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Convert dữ liệu Firebase thành Object Plant
                    val plant = document.toObject(Plant::class.java)

                    if (plant != null) {
                        currentDisplayPlant = plant
                        displayPlantInfo(plant)
                        setupAddButton(plant)
                    } else {
                        showError("Dữ liệu cây bị lỗi.")
                    }
                } else {
                    showError("Không tìm thấy thông tin cây này trên hệ thống.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PlantFragment", "Lỗi tải Firebase", e)
                showError("Lỗi kết nối: ${e.message}")
            }
    }

    private fun displayPlantInfo(plant: Plant) {
        // Hiển thị ảnh bằng Glide (xử lý link Google Drive)
        if (!plant.image.isNullOrBlank()) {
            val directUrl = convertGoogleDriveLink(plant.image)
            Glide.with(this)
                .load(directUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_delete)
                .centerCrop()
                .into(binding.imgPlant)
        }

        // Gán thông tin text
        binding.tvPlantName.text = plant.name
        binding.tvScientificName.text = plant.scientificName
        binding.tvDescription.text = plant.description ?: "Đang cập nhật..."

        // Các thông số kỹ thuật (Ánh sáng, nước...)
        binding.tvLight.text = "☀️ Ánh sáng: ${plant.light ?: "N/A"}"
        binding.tvWater.text = "💧 Tưới nước: ${plant.watering ?: "N/A"}"
        binding.tvSoil.text = "🪨 Đất: ${plant.soil ?: "N/A"}"
        binding.tvFertilizer.text = "🧪 Phân bón: ${plant.fertilizer ?: "N/A"}"
        binding.tvTemp.text = "🌡️ Nhiệt độ: ${plant.temperature ?: "N/A"}"
        binding.tvHumidity.text = "💦 Độ ẩm: ${plant.humidity ?: "N/A"}"
    }

    private fun convertGoogleDriveLink(originalUrl: String): String {
        return if (originalUrl.contains("drive.google.com")) {
            try {
                val id = originalUrl.substringAfter("/d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) {
                originalUrl
            }
        } else {
            originalUrl
        }
    }

    private fun showError(msg: String) {
        binding.tvPlantName.text = "Thông báo"
        binding.tvDescription.text = msg
        binding.btnAddPlant.isEnabled = false
        binding.btnAddPlant.alpha = 0.5f
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

    private fun addMultiplePlantsToGarden(plantToSave: Plant, quantity: Int) {
        lifecycleScope.launch {
            // Đếm số lượng cây hiện có để đánh số thứ tự (Ví dụ: Hoa hồng 3, Hoa hồng 4)
            val currentCount = gardenViewModel.getPlantCount(plantToSave.id)
            val baseName = plantToSave.name

            for (i in 1..quantity) {
                val newIndex = currentCount + i
                val finalName = if (currentCount == 0 && quantity == 1) baseName else "$baseName $newIndex"

                val newUserPlant = UserPlant(
                    plantId = plantToSave.id,
                    nickname = finalName,
                    imagePath = plantToSave.image // Lưu lại link ảnh để hiển thị trong Garden
                )
                gardenViewModel.insert(newUserPlant)
            }

            Toast.makeText(context, "Đã thêm $quantity cây vào vườn!", Toast.LENGTH_SHORT).show()

            // Chuyển về màn hình My Garden
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("OPEN_MY_GARDEN", true)
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}