package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.leafyapp.databinding.FragmentDiseaseBinding

class DiseaseFragment : Fragment() {

    private var _binding: FragmentDiseaseBinding? = null
    private val binding get() = _binding!!

    private var diseaseId: Int = -1
    private var diseaseLabel: String = "Unknown"
    private var diseaseConfidence: Float = 0f

    companion object {
        fun newInstance(id: Int, label: String, confidence: Float): DiseaseFragment {
            val fragment = DiseaseFragment()
            val bundle = Bundle()
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
            diseaseId = bundle.getInt("ID", -1)
            diseaseLabel = bundle.getString("LABEL") ?: "Unknown"
            diseaseConfidence = bundle.getFloat("CONFIDENCE", 0f)
        }

        binding.tvDiseaseResult.text =
            "ID: $diseaseId\nDisease: $diseaseLabel\nConfidence: ${(diseaseConfidence * 100).toInt()}%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
