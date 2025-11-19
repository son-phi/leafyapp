package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.example.leafyapp.DatabaseHelper
import com.example.leafyapp.data.model.Disease
import com.example.leafyapp.databinding.FragmentDiseaseBinding
import com.example.leafyapp.databinding.ItemDiseaseBlockBinding
import com.example.leafyapp.R

class DiseaseFragment : Fragment() {

    private var _binding: FragmentDiseaseBinding? = null
    private val binding get() = _binding!!

    private var diseaseId: Int = -1

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
        loadDisease()
    }

    private fun setupCloseButton() {
        binding.btnCloseDisease.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // đảm bảo nút nổi lên trên ScrollView
        binding.btnCloseDisease.bringToFront()
    }

    private fun loadDisease() {
        val ctx = context ?: return
        val db = DatabaseHelper(ctx)

        // Healthy case: id = 2
        if (diseaseId == 2) {
            showHealthyUi()
            return
        }

        val disease = db.getDiseaseById(diseaseId)

        if (disease == null) {
            showHealthyUi()
        } else {
            showDiseaseBlocks(disease)
        }
    }

    // ----------- UI: HEALTHY ----------
    private fun showHealthyUi() {
        binding.layoutHealthy.visibility = View.VISIBLE
        binding.scrollDisease.visibility = View.GONE

        val customFont = ResourcesCompat.getFont(requireContext(), R.font.healthy)
        binding.tvHealthy.typeface = customFont

        binding.tvHealthy.paint.isFakeBoldText = true   // Đậm cấp 1
        binding.tvHealthy.paint.strokeWidth = 2f        // Tăng nét (1f-4f)
        binding.tvHealthy.invalidate()

        binding.lottieHealthy.playAnimation()

        // Fade-in chữ đẹp hơn
        binding.tvHealthy.alpha = 0f
        binding.tvHealthy.animate()
            .alpha(1f)
            .setDuration(1200)
            .start()
    }

    // ----------- UI: DISEASE ----------
    private fun showDiseaseBlocks(d: Disease) {
        binding.layoutHealthy.visibility = View.GONE
        binding.scrollDisease.visibility = View.VISIBLE

        binding.containerDiseases.removeAllViews()

        // cập nhật tiêu đề bệnh
        binding.tvDiseaseTitle.text = "Thông tin bệnh: ${d.diseaseName}"

        // loop theo số lượng lý do
        for (i in d.reasons.indices) {

            // Code mới (Giữ lại margin và layout params)
            val item = ItemDiseaseBlockBinding.inflate(layoutInflater, binding.containerDiseases, false)

            // đổi tên block thành Nguyên nhân #
            item.tvDiseaseName.text = "🌱 Nguyên nhân ${i + 1}"

            item.tvReason.text = "• Lý do: ${d.reasons[i]}"
            item.tvSolution.text = "• Giải pháp: ${d.solutions.getOrNull(i) ?: "Không có"}"
            item.tvPlants.text = "• Cây thường bị: ${d.plants.getOrNull(i) ?: "Không có"}"

            binding.containerDiseases.addView(item.root)
        }

        // đảm bảo nút X nằm trên cùng
        binding.btnCloseDisease.bringToFront()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
