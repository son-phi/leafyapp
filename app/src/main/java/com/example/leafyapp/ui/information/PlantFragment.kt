package com.example.leafyapp.ui.information

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.leafyapp.databinding.FragmentPlantBinding

class PlantFragment : Fragment() {

    private var _binding: FragmentPlantBinding? = null
    private val binding get() = _binding!!

    private var plantId: Int = -1
    private var plantLabel: String = "Unknown"
    private var plantConfidence: Float = 0f

    companion object {
        fun newInstance(id: Int, label: String, confidence: Float): PlantFragment {
            val fragment = PlantFragment()
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
        _binding = FragmentPlantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        arguments?.let { bundle ->
            plantId = bundle.getInt("ID", -1)
            plantLabel = bundle.getString("LABEL") ?: "Unknown"
            plantConfidence = bundle.getFloat("CONFIDENCE", 0f)
        }

        // Hiển thị thông tin lên UI
        binding.tvPlantResult.text =
            "ID: $plantId\nPlant: $plantLabel\nConfidence: ${(plantConfidence * 100).toInt()}%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
