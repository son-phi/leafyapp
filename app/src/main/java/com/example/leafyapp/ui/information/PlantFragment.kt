package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView // Import mới quan trọng
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

    // SỬA 1: Đổi LinearLayout thành NestedScrollView để khớp với XML
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>

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
        setupAddButton()
    }

    private fun receiveArguments() {
        arguments?.let {
            plantId = it.getInt("ID", -1)
            plantLabel = it.getString("LABEL") ?: "Unknown"
            plantConfidence = it.getFloat("CONFIDENCE", 0f)
        }
    }

    /** =====================
     * BOTTOM SHEET SETUP
     * ===================== */
    private fun setupBottomSheet() {
        // Lấy Behavior từ view bottomSheet (đang là NestedScrollView)
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        // SỬA 2: Thiết lập trạng thái ban đầu
        // Không set peekHeight ở đây nữa (để XML 300dp tự lo)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Nếu bạn muốn sheet không che hết ảnh khi mở rộng tối đa (chừa lại 1 chút ở trên)
        // bottomSheetBehavior.isFitToContents = false
        // bottomSheetBehavior.expandedOffset = 200
        // Nhưng với NestedScrollView thì để mặc định là mượt nhất.

        // Callback (Tùy chọn - để log hoặc xử lý animation nút Add nếu cần)
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Không cần ép logic cưỡng bức ở đây nữa để tránh bị giật
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Có thể làm mờ ảnh nền khi kéo lên tại đây nếu muốn
                // binding.imgPlant.alpha = 1f - slideOffset
            }
        })
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupAddButton() {
        binding.btnAddPlant.setOnClickListener {
            // Xử lý sự kiện thêm vào vườn của tôi
            // TODO: Thêm logic lưu vào My Garden
        }
    }

    /** =====================
     * CONVERT DRIVE LINK
     * ===================== */
    private fun convertDrive(url: String): String {
        return if (url.contains("drive.google.com")) {
            try {
                val id = url.substringAfter("d/").substringBefore("/")
                "https://drive.google.com/uc?export=view&id=$id"
            } catch (e: Exception) { url }
        } else url
    }

    /** =====================
     * LOAD DATA
     * ===================== */
    private fun loadPlantFromDatabase() {
        if (plantId < 0) {
            showError("Không xác định được cây từ AI.")
            return
        }

        val ctx = context ?: return
        val db = DatabaseHelper(ctx)

        // Logic +1 id của bạn giữ nguyên
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
     * HIỂN THỊ THÔNG TIN
     * ===================== */
    private fun displayPlantInfo(plant: Plant) {
        // Ảnh
        binding.imgPlant.load(convertDrive(plant.image ?: "")) {
            crossfade(true)
            // placeholder(R.drawable.loading) // Thêm placeholder nếu cần
            // error(R.drawable.error)
        }

        // Tên cây
        binding.tvPlantName.text = plant.name

        // Tên khoa học
        binding.tvScientificName.text = plant.scientificName

        // Mô tả
        binding.tvDescription.text = plant.description ?: "Đang cập nhật..."

        // Thông số (Dùng template string cho gọn)
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