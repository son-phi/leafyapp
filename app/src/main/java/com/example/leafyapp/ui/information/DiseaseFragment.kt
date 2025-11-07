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

    companion object {
        fun newInstance(result: String): DiseaseFragment {
            val fragment = DiseaseFragment()
            val bundle = Bundle()
            bundle.putString("RESULT", result)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiseaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val result = arguments?.getString("RESULT") ?: "Unknown"
        binding.tvDiseaseResult.text = result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
