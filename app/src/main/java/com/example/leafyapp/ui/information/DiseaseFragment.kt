package com.example.leafyapp.ui.information

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.MainActivity
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Disease
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentDiseaseBinding
import com.example.leafyapp.databinding.ItemDiseaseBlockBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DiseaseFragment : Fragment() {

    private var _binding: FragmentDiseaseBinding? = null
    private val binding get() = _binding!!

    // ViewModel này sẽ load cây từ Repository
    private val gardenViewModel: GardenViewModel by viewModels()

    private var diseaseId: Int = -1
    private var currentDiseaseName: String = ""

    // Danh sách cây hiện có (Tổng hợp từ cả 2 nguồn)
    private var myPlants: List<UserPlant> = emptyList()

    companion object {
        fun newInstance(id: Int, label: String, confidence: Float) =
            DiseaseFragment().apply {
                arguments = Bundle().apply {
                    putInt("ID", id)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiseaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        diseaseId = arguments?.getInt("ID", -1) ?: -1
        setupCloseButton()

        // QUAN TRỌNG: Quan sát masterPlantList để tab Family không bị trống
        gardenViewModel.masterPlantList.observe(viewLifecycleOwner) { plants ->
            myPlants = plants
        }

        binding.btnCloseDisease.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Tải lại dữ liệu từ Firebase
        gardenViewModel.loadCombinedPlants()
        loadDisease()
    }

    private fun setupCloseButton() {
        binding.btnCloseDisease.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun loadDisease() {
        val ctx = context ?: return
        val db = DatabaseHelper(ctx)

        if (diseaseId == 2) { // Giả sử ID 2 là Healthy
            showHealthyUi()
            return
        }

        val disease = db.getDiseaseById(diseaseId)

        if (disease == null) {
            showHealthyUi()
        } else {
            currentDiseaseName = disease.diseaseName
            showDiseaseBlocks(disease)
        }
    }

    // ----------- UI: HEALTHY ----------
    private fun showHealthyUi() {
        binding.layoutHealthy.visibility = View.VISIBLE
        binding.scrollDisease.visibility = View.GONE

        try {
            val customFont = ResourcesCompat.getFont(requireContext(), R.font.healthy)
            binding.tvHealthy.typeface = customFont
        } catch (e: Exception) {}

        binding.tvHealthy.text = "Your plant is healthy!"
        binding.tvHealthy.paint.isFakeBoldText = true
        binding.lottieHealthy.playAnimation()
        binding.tvHealthy.animate().alpha(1f).setDuration(1200).start()
    }

    // ----------- UI: DISEASE ----------
    private fun showDiseaseBlocks(d: Disease) {
        binding.layoutHealthy.visibility = View.GONE
        binding.scrollDisease.visibility = View.VISIBLE
        binding.containerDiseases.removeAllViews()

        binding.tvDiseaseTitle.text = "Thông tin bệnh: ${d.diseaseName}"
        binding.btnMarkSick.text = "Đánh dấu cây bị bệnh"
        binding.btnMarkSick.visibility = View.VISIBLE

        for (i in d.reasons.indices) {
            val item = ItemDiseaseBlockBinding.inflate(layoutInflater, binding.containerDiseases, false)
            item.tvDiseaseName.text = "🌱 Nguyên nhân #${i + 1}"
            item.tvReason.text = "• Nguyên nhân: ${d.reasons[i]}"
            item.tvSolution.text = "• Cách xử lý: ${d.solutions.getOrNull(i) ?: "Không có"}"
            item.tvPlants.text = "• Cây bị ảnh hưởng: ${d.plants.getOrNull(i) ?: "Không có"}"
            binding.containerDiseases.addView(item.root)
        }

        binding.btnMarkSick.setOnClickListener {
            showSelectPlantsDialog()
        }
    }

    // =========================================================================
    // HÀM HIỆN DIALOG CHỌN CÂY (LOGIC TAB SWITCH)
    // =========================================================================
    private fun showSelectPlantsDialog() {
        if (myPlants.isEmpty()) {
            Toast.makeText(context, "Đang tải danh sách cây, vui lòng thử lại sau!", Toast.LENGTH_SHORT).show()
            return
        }

        val personalPlants = myPlants.filter { it.gardenId.isNullOrEmpty() }
        val familyPlants = myPlants.filter { !it.gardenId.isNullOrEmpty() }

        val dialogView = layoutInflater.inflate(R.layout.dialog_select_plants_switch, null)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupGarden)
        val rvPlants = dialogView.findViewById<RecyclerView>(R.id.rvPlantsSelector)

        // Ánh xạ các nút mới trong Layout
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelDialog)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveDialog)

        val initialList = if (personalPlants.isNotEmpty()) personalPlants else familyPlants
        val adapter = DiseasePlantSelectionAdapter(initialList)

        rvPlants.layoutManager = LinearLayoutManager(context)
        rvPlants.adapter = adapter

        // Chuyển Tab
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTabPersonal -> adapter.updateData(personalPlants)
                    R.id.btnTabFamily -> adapter.updateData(familyPlants)
                }
            }
        }

        // Logic check nút mặc định
        if (personalPlants.isEmpty() && familyPlants.isNotEmpty()) {
            toggleGroup.check(R.id.btnTabFamily)
        } else {
            toggleGroup.check(R.id.btnTabPersonal)
        }

        // Khởi tạo Dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        // Xử lý nút Hủy
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Xử lý nút Lưu
        btnSave.setOnClickListener {
            val selectedList = adapter.selectedPlants.toList()
            if (selectedList.isNotEmpty()) {
                gardenViewModel.markPlantsAsInfected(selectedList, currentDiseaseName)
                Toast.makeText(context, "Đã lưu bệnh cho ${selectedList.size} cây.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                // 2. NAV trực tiếp về My Garden
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("OPEN_GARDEN", true)
                }
                startActivity(intent)

                // 3. Đóng màn hình hiện tại
                requireActivity().finish()
            } else {
                Toast.makeText(context, "Chưa chọn cây nào.", Toast.LENGTH_SHORT).show()
            }
        }

        // Làm trong suốt window để hiện đúng nền trắng và bo góc từ XML của bạn
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}