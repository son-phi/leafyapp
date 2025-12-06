package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Disease
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentDiseaseBinding
import com.example.leafyapp.databinding.ItemDiseaseBlockBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DiseaseFragment : Fragment() {

    private var _binding: FragmentDiseaseBinding? = null
    private val binding get() = _binding!!

    // Gọi GardenViewModel để lấy danh sách cây và lưu lịch sử
    private val gardenViewModel: GardenViewModel by viewModels()

    private var diseaseId: Int = -1
    private var currentDiseaseName: String = ""

    // Danh sách cây của người dùng (lấy từ ViewModel)
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiseaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        diseaseId = arguments?.getInt("ID", -1) ?: -1

        setupCloseButton()

        // 1. Lắng nghe danh sách cây từ Database để sẵn sàng hiển thị dialog
        gardenViewModel.allUserPlants.observe(viewLifecycleOwner) { plants ->
            myPlants = plants
        }

        loadDisease()
    }

    private fun setupCloseButton() {
        binding.btnCloseDisease.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.btnCloseDisease.bringToFront()
    }

    private fun loadDisease() {
        val ctx = context ?: return
        val db = DatabaseHelper(ctx)

        if (diseaseId == 2) {
            showHealthyUi()
            return
        }

        val disease = db.getDiseaseById(diseaseId)

        if (disease == null) {
            showHealthyUi()
        } else {
            // Lưu tên bệnh để dùng khi lưu vào lịch sử
            currentDiseaseName = disease.diseaseName
            showDiseaseBlocks(disease)
        }
    }

    // ----------- UI: HEALTHY ----------
    private fun showHealthyUi() {
        binding.layoutHealthy.visibility = View.VISIBLE
        binding.scrollDisease.visibility = View.GONE

        val customFont = ResourcesCompat.getFont(requireContext(), R.font.healthy)
        binding.tvHealthy.typeface = customFont
        binding.tvHealthy.paint.isFakeBoldText = true
        binding.tvHealthy.paint.strokeWidth = 2f
        binding.tvHealthy.invalidate()
        binding.lottieHealthy.playAnimation()
        binding.tvHealthy.alpha = 0f
        binding.tvHealthy.animate().alpha(1f).setDuration(1200).start()
    }

    // ----------- UI: DISEASE ----------
    private fun showDiseaseBlocks(d: Disease) {
        binding.layoutHealthy.visibility = View.GONE
        binding.scrollDisease.visibility = View.VISIBLE

        binding.containerDiseases.removeAllViews()
        binding.tvDiseaseTitle.text = "Thông tin bệnh: ${d.diseaseName}"

        for (i in d.reasons.indices) {
            val item = ItemDiseaseBlockBinding.inflate(layoutInflater, binding.containerDiseases, false)
            item.tvDiseaseName.text = "🌱 Nguyên nhân ${i + 1}"
            item.tvReason.text = "• Lý do: ${d.reasons[i]}"
            item.tvSolution.text = "• Giải pháp: ${d.solutions.getOrNull(i) ?: "Không có"}"
            item.tvPlants.text = "• Cây thường bị: ${d.plants.getOrNull(i) ?: "Không có"}"
            binding.containerDiseases.addView(item.root)
        }

        // --- XỬ LÝ NÚT LƯU BỆNH ---
        binding.btnMarkSick.setOnClickListener {
            showSelectPlantsDialog()
        }

        binding.btnCloseDisease.bringToFront()
    }

    // --- HÀM HIỆN DIALOG CHỌN CÂY ---
    private fun showSelectPlantsDialog() {
        if (myPlants.isEmpty()) {
            Toast.makeText(context, "Vườn của bạn chưa có cây nào!", Toast.LENGTH_SHORT).show()
            return
        }

        val plantNames = myPlants.map { it.nickname }.toTypedArray()
        val checkedItems = BooleanArray(myPlants.size)
        val selectedPlants = ArrayList<UserPlant>()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chọn cây đang bị bệnh này")
            .setMultiChoiceItems(plantNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedPlants.add(myPlants[which])
                } else {
                    selectedPlants.remove(myPlants[which])
                }
            }
            .setPositiveButton("Lưu lại") { dialog, _ ->
                if (selectedPlants.isNotEmpty()) {
                    // Gọi ViewModel để lưu vào DB
                    gardenViewModel.markPlantsAsInfected(selectedPlants, currentDiseaseName)
                    Toast.makeText(context, "Đã lưu bệnh cho ${selectedPlants.size} cây", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}