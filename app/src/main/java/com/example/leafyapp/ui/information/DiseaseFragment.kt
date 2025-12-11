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

    // ViewModel này sẽ load cây từ Repository (Mặc định là Vườn Riêng)
    private val gardenViewModel: GardenViewModel by viewModels()

    private var diseaseId: Int = -1
    private var currentDiseaseName: String = ""

    // Danh sách cây hiện có để người dùng chọn
    private var myPlants: List<UserPlant> = emptyList()

    companion object {
        fun newInstance(id: Int, label: String, confidence: Float) =
            DiseaseFragment().apply {
                arguments = Bundle().apply {
                    putInt("ID", id)
                    // Có thể truyền thêm Label/Confidence nếu muốn hiển thị
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
        super.onViewCreated(view, savedInstanceState)

        diseaseId = arguments?.getInt("ID", -1) ?: -1

        setupCloseButton()

        // 1. Lắng nghe danh sách cây (Để dùng cho Dialog chọn cây bị bệnh)
        gardenViewModel.allUserPlants.observe(viewLifecycleOwner) { plants ->
            myPlants = plants
        }

        loadDisease()
    }

    private fun setupCloseButton() {
        binding.btnCloseDisease.setOnClickListener {
            requireActivity().finish() // Đóng Activity thay vì onBackPressed cho chắc
        }
        binding.btnCloseDisease.bringToFront()
    }

    private fun loadDisease() {
        val ctx = context ?: return
        val db = DatabaseHelper(ctx)

        // Case Healthy (ID = 2 hoặc theo database của bạn)
        // Nếu bạn đã cộng 1 ở ResultActivity thì phải check ID tương ứng
        if (diseaseId == 2) { // Ví dụ: Healthy ID
            showHealthyUi()
            return
        }

        val disease = db.getDiseaseById(diseaseId)

        if (disease == null) {
            // Nếu không tìm thấy bệnh, fallback về Healthy hoặc báo lỗi
            showHealthyUi()
        } else {
            currentDiseaseName = disease.diseaseName
            showDiseaseBlocks(disease)
        }
    }

    // ----------- UI: HEALTHY (Cây khỏe) ----------
    private fun showHealthyUi() {
        binding.layoutHealthy.visibility = View.VISIBLE
        binding.scrollDisease.visibility = View.GONE

        // Animation và Font chữ
        try {
            val customFont = ResourcesCompat.getFont(requireContext(), R.font.healthy)
            binding.tvHealthy.typeface = customFont
        } catch (e: Exception) {
            // Font lỗi thì thôi, dùng mặc định
        }

        binding.tvHealthy.text = "Your plant is healthy! 🎉" // Tiếng Anh
        binding.tvHealthy.paint.isFakeBoldText = true
        binding.tvHealthy.invalidate()

        binding.lottieHealthy.playAnimation()
        binding.tvHealthy.alpha = 0f
        binding.tvHealthy.animate().alpha(1f).setDuration(1200).start()
    }

    // ----------- UI: DISEASE (Cây bệnh) ----------
    private fun showDiseaseBlocks(d: Disease) {
        binding.layoutHealthy.visibility = View.GONE
        binding.scrollDisease.visibility = View.VISIBLE

        binding.containerDiseases.removeAllViews()

        // Tiêu đề Tiếng Anh
        binding.tvDiseaseTitle.text = "Disease Info: ${d.diseaseName}"

        // Nút lưu bệnh
        binding.btnMarkSick.text = "Mark plants as Infected"
        binding.btnMarkSick.visibility = View.VISIBLE

        for (i in d.reasons.indices) {
            val item = ItemDiseaseBlockBinding.inflate(layoutInflater, binding.containerDiseases, false)

            // Nội dung chi tiết (Dữ liệu database có thể là tiếng Việt, nhưng nhãn là tiếng Anh)
            item.tvDiseaseName.text = "🌱 Cause #${i + 1}"
            item.tvReason.text = "• Reason: ${d.reasons[i]}"
            item.tvSolution.text = "• Solution: ${d.solutions.getOrNull(i) ?: "N/A"}"
            item.tvPlants.text = "• Affected Plants: ${d.plants.getOrNull(i) ?: "N/A"}"

            binding.containerDiseases.addView(item.root)
        }

        // --- XỬ LÝ NÚT LƯU BỆNH ---
        binding.btnMarkSick.setOnClickListener {
            showSelectPlantsDialog()
        }
    }

    // --- HÀM HIỆN DIALOG CHỌN CÂY ---
    private fun showSelectPlantsDialog() {
        if (myPlants.isEmpty()) {
            Toast.makeText(context, "Your garden is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        // Lấy tên cây để hiển thị
        val plantNames = myPlants.map { it.nickname }.toTypedArray()
        val checkedItems = BooleanArray(myPlants.size)
        val selectedPlants = ArrayList<UserPlant>()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select affected plants") // Tiếng Anh
            .setMultiChoiceItems(plantNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedPlants.add(myPlants[which])
                } else {
                    selectedPlants.remove(myPlants[which])
                }
            }
            .setPositiveButton("Save") { dialog, _ ->
                if (selectedPlants.isNotEmpty()) {
                    // Gọi ViewModel để lưu vào SharedPreferences (hoặc Firebase sau này)
                    gardenViewModel.markPlantsAsInfected(selectedPlants, currentDiseaseName)
                    Toast.makeText(context, "Marked ${selectedPlants.size} plants as infected.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}