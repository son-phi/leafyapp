package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.leafyapp.databinding.FragmentDiseaseBinding
import com.example.leafyapp.DatabaseHelper // Import DatabaseHelper
import com.example.leafyapp.data.model.Disease // Import Disease model

class DiseaseFragment : Fragment() {

    private var _binding: FragmentDiseaseBinding? = null
    private val binding get() = _binding!!

    // Sử dụng biến này như là Plant ID để tra cứu bệnh liên quan
    private var plantId: Int = -1
    private var diseaseLabel: String = "Unknown"
    private var diseaseConfidence: Float = 0f

    companion object {
        fun newInstance(id: Int, label: String, confidence: Float): DiseaseFragment {
            val fragment = DiseaseFragment()
            val bundle = Bundle()
            // Truyền ID của cây vào bundle (giả định "ID" là plant_id)
            bundle.putInt("ID", id)
            bundle.putString("LABEL", label)
            bundle.putFloat("CONFIDENCE", confidence)
            fragment.arguments = bundle
            return fragment
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

        arguments?.let { bundle ->
            // Lấy ID của cây (giả định "ID" ở đây là plant_id)
            plantId = bundle.getInt("ID", -1)
            diseaseLabel = bundle.getString("LABEL") ?: "Unknown"
            diseaseConfidence = bundle.getFloat("CONFIDENCE", 0f)
        }

        binding.tvDiseaseResult.text =
            "Plant ID: $plantId\nDisease: $diseaseLabel\nConfidence: ${(diseaseConfidence * 100).toInt()}%"

        // --- Bổ sung Code để hiển thị thông tin bệnh từ Database ---
        if (plantId != -1 && context != null) {
            val dbHelper = DatabaseHelper(requireContext())

            // Lấy danh sách bệnh dựa trên Plant ID
            val diseases = dbHelper.getDiseasesByPlantId(plantId)

            displayDiseaseInfo(diseases)
        } else {
            // Giả định bạn có một TextView tên là tvDiseaseDetails
            binding.tvDiseaseDetails.text = "Không thể tìm kiếm thông tin bệnh (ID cây không hợp lệ)."
        }
    }

    /**
     * Hàm helper để định dạng và hiển thị danh sách bệnh và giải pháp.
     */
    private fun displayDiseaseInfo(diseases: List<Disease>) {
        val detailsBuilder = StringBuilder()

        if (diseases.isNotEmpty()) {
            detailsBuilder.append("--- Thông Tin Bệnh & Giải Pháp ---")
            diseases.forEachIndexed { index, disease ->
                // Định dạng thông tin:
                // Tên bệnh (pests): ${disease.pests}
                // Giải pháp (solutions): ${disease.solutions}
                detailsBuilder.append("\n\n[Bệnh ${index + 1}]")
                detailsBuilder.append("\nSâu/Bệnh: ${disease.pests}")
                detailsBuilder.append("\nGiải Pháp: ${disease.solutions}")
            }
        } else {
            detailsBuilder.append("\n\nKhông có thông tin bệnh kèm theo trong CSDL cho ID cây này ($plantId).")
        }

        // Cập nhật TextView chi tiết
        binding.tvDiseaseDetails.text = detailsBuilder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}