package com.example.leafyapp.ui.information

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.leafyapp.MainActivity
import com.example.leafyapp.data.model.Garden
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentPlantBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await // Cần import cái này để dùng await()
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

    private fun fetchPlantFromFirebase() {
        if (plantId == -1) {
            showError("ID cây không hợp lệ.")
            return
        }

        binding.tvPlantName.text = plantLabel ?: "Loading..."

        val db = FirebaseFirestore.getInstance()
        db.collection("plants").document(plantId.toString())
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val plant = document.toObject(Plant::class.java)
                    if (plant != null) {
                        currentDisplayPlant = plant
                        displayPlantInfo(plant)
                        setupAddButton(plant)
                    } else {
                        showError("Lỗi dữ liệu cây.")
                    }
                } else {
                    showError("Không tìm thấy cây này.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PlantFragment", "Lỗi tải Firebase", e)
                showError("Lỗi kết nối: ${e.message}")
            }
    }

    private fun displayPlantInfo(plant: Plant) {
        if (!plant.image.isNullOrBlank()) {
            val directUrl = convertGoogleDriveLink(plant.image)
            Glide.with(this)
                .load(directUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_delete)
                .centerCrop()
                .into(binding.imgPlant)
        }

        binding.tvPlantName.text = plant.name
        binding.tvScientificName.text = plant.scientificName
        binding.tvDescription.text = plant.description ?: "Updating..."

        binding.tvLight.text = "☀️ Light: ${plant.light ?: "N/A"}"
        binding.tvWater.text = "💧 Water: ${plant.watering ?: "N/A"}"
        binding.tvSoil.text = "🪨 Soil: ${plant.soil ?: "N/A"}"
        binding.tvFertilizer.text = "🧪 Fertilizer: ${plant.fertilizer ?: "N/A"}"
        binding.tvTemp.text = "🌡️ Temperature: ${plant.temperature ?: "N/A"}"
        binding.tvHumidity.text = "💦 Humidity: ${plant.humidity ?: "N/A"}"
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
        binding.tvPlantName.text = "Error"
        binding.tvDescription.text = msg
        binding.btnAddPlant.isEnabled = false
        binding.btnAddPlant.alpha = 0.5f
    }

    private fun setupAddButton(plant: Plant) {
        binding.btnAddPlant.text = "Add to Garden"
        binding.btnAddPlant.isEnabled = true
        binding.btnAddPlant.alpha = 1.0f

        binding.btnAddPlant.setOnClickListener {
            showQuantityDialog(plant)
        }
    }

    private fun showQuantityDialog(plant: Plant) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
            // gravity = android.view.Gravity.CENTER // Bỏ cái này đi để checkbox căn lề trái cho đẹp
        }

        // 1. Chọn số lượng
        val numberPicker = NumberPicker(context).apply {
            minValue = 1
            maxValue = 10
            value = 1
            wrapSelectorWheel = false
        }
        // Cho NumberPicker ra giữa
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
            bottomMargin = 30 // Cách đoạn checkbox ra 1 chút
        }
        layout.addView(numberPicker, layoutParams)

        // 2. Tạo 2 Checkbox
        val cbPersonal = CheckBox(context).apply {
            text = "Thêm vào Vườn Cá Nhân" // Personal Garden
            textSize = 16f
            isChecked = true // Mặc định chọn Cá nhân
        }

        val cbFamily = CheckBox(context).apply {
            text = "Thêm vào Vườn Gia Đình" // Family Garden
            textSize = 16f
            isChecked = false
        }

        // 3. Xử lý Logic "Chỉ được chọn 1 trong 2"
        // Dùng setOnClickListener thay vì setOnCheckedChangeListener để tránh vòng lặp vô tận
        cbPersonal.setOnClickListener {
            cbPersonal.isChecked = true  // Luôn giữ trạng thái true khi bấm vào chính nó
            cbFamily.isChecked = false   // Tắt cái kia đi
        }

        cbFamily.setOnClickListener {
            cbFamily.isChecked = true    // Luôn giữ trạng thái true khi bấm vào chính nó
            cbPersonal.isChecked = false // Tắt cái kia đi
        }

        layout.addView(cbPersonal)
        layout.addView(cbFamily)

        AlertDialog.Builder(context)
            .setTitle("Select Quantity")
            .setMessage("How many '${plant.name}' do you want to add?")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val quantity = numberPicker.value
                // Chỉ cần kiểm tra cbFamily có được chọn hay không là biết mode nào
                val isFamily = cbFamily.isChecked
                addMultiplePlantsToGarden(plant, quantity, isFamily)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- LOGIC TỰ ĐỘNG TÌM ID VƯỜN ---
    private fun addMultiplePlantsToGarden(plantToSave: Plant, quantity: Int, isFamilyMode: Boolean) {
        lifecycleScope.launch {
            // Hiển thị loading nhẹ hoặc disable nút bấm nếu cần (ở đây làm đơn giản)

            if (isFamilyMode) {
                // 1. Tự động tìm vườn của user trên Firebase
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Toast.makeText(context, "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    val db = FirebaseFirestore.getInstance()
                    // Query tìm vườn nào có chứa UID của mình trong mảng 'members'
                    val querySnapshot = db.collection("gardens")
                        .whereArrayContains("members", currentUser.uid)
                        .limit(1) // Lấy vườn đầu tiên tìm thấy
                        .get()
                        .await() // Đợi kết quả trả về

                    if (!querySnapshot.isEmpty) {
                        val gardenDoc = querySnapshot.documents[0]
                        // Convert sang Object Garden
                        val garden = gardenDoc.toObject(Garden::class.java)

                        // Nếu object rỗng nhưng doc tồn tại, gán thủ công ID
                        val finalGarden = garden?.apply { id = gardenDoc.id } ?: Garden(id = gardenDoc.id)

                        // Set ViewModel sang chế độ Family với ID vừa tìm được
                        gardenViewModel.setGardenMode(finalGarden)

                        // Tiến hành thêm cây
                        performAddPlants(plantToSave, quantity, true)

                    } else {
                        Toast.makeText(context, "Bạn chưa tham gia vườn gia đình nào!", Toast.LENGTH_LONG).show()
                        // Không thêm cây nữa vì không có vườn
                    }
                } catch (e: Exception) {
                    Log.e("PlantFragment", "Lỗi tìm vườn", e)
                    Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                }

            } else {
                // 2. Chế độ riêng tư -> Không cần tìm ID
                gardenViewModel.setGardenMode(null)
                performAddPlants(plantToSave, quantity, false)
            }
        }
    }

    // Tách hàm thêm cây ra cho gọn code
    private suspend fun performAddPlants(plantToSave: Plant, quantity: Int, isFamilyMode: Boolean) {
        val currentCount = gardenViewModel.getPlantCount(plantToSave.id)
        val baseName = plantToSave.name

        for (i in 1..quantity) {
            val newIndex = currentCount + i
            val finalName = if (currentCount == 0 && quantity == 1) baseName else "$baseName $newIndex"

            val newUserPlant = UserPlant(
                plantId = plantToSave.id,
                nickname = finalName,
                imagePath = plantToSave.image
            )
            gardenViewModel.insert(newUserPlant)
        }

        val dest = if (isFamilyMode) "Family Garden" else "My Garden"
        Toast.makeText(context, "Added $quantity plants to $dest!", Toast.LENGTH_SHORT).show()

        // Chuyển màn hình
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("OPEN_FAMILY_MODE", isFamilyMode)
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}