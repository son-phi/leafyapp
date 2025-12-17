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
        // 1. Inflate Layout
        val dialogView = layoutInflater.inflate(com.example.leafyapp.R.layout.dialog_add_plant_quantity, null)

        // 2. Ánh xạ View
        val tvPlantName = dialogView.findViewById<android.widget.TextView>(com.example.leafyapp.R.id.tv_plant_name)
        val tvQuantity = dialogView.findViewById<android.widget.TextView>(com.example.leafyapp.R.id.tv_quantity)
        val btnMinus = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_minus)
        val btnPlus = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_plus)

        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(com.example.leafyapp.R.id.radio_group_garden)
        val rbFamily = dialogView.findViewById<android.widget.RadioButton>(com.example.leafyapp.R.id.rb_family)

        val btnAdd = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_add)
        val btnCancel = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_cancel)

        // 3. Setup Data ban đầu
        tvPlantName.text = "'${plant.name}'"
        var currentQuantity = 1

        // --- LOGIC TĂNG GIẢM SỐ LƯỢNG ---
        btnMinus.setOnClickListener {
            if (currentQuantity > 1) {
                currentQuantity--
                tvQuantity.text = currentQuantity.toString()
            }
        }

        btnPlus.setOnClickListener {
            if (currentQuantity < 20) { // Giới hạn max là 20 (hoặc 10 tùy bạn)
                currentQuantity++
                tvQuantity.text = currentQuantity.toString()
            }
        }

        // 4. Tạo Dialog
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        val dialog = builder.create()

        // Làm nền trong suốt để bo góc đẹp
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // 5. Xử lý nút Hành động
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnAdd.setOnClickListener {
            // Kiểm tra xem user chọn Family hay Personal
            // Nếu radio Family được check -> isFamily = true
            val isFamily = rbFamily.isChecked

            // Gọi hàm thêm cây
            addMultiplePlantsToGarden(plant, currentQuantity, isFamily)

            dialog.dismiss()
        }

        dialog.show()
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
                        performAddPlants(plantToSave, quantity, true, gardenDoc.id)

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
                performAddPlants(plantToSave, quantity, false, null)
            }
        }
    }

    private suspend fun performAddPlants(plantToSave: Plant, quantity: Int, isFamilyMode: Boolean, gardenId: String?) {
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

        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            // Gửi các cờ tín hiệu
            putExtra("NAVIGATE_TO_GARDEN", true)
            putExtra("IS_FAMILY_MODE", isFamilyMode)
            if (gardenId != null) {
                putExtra("TARGET_GARDEN_ID", gardenId)
            }
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}