package com.example.leafyapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.leafyapp.databinding.FragmentHomeBinding

import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import kotlin.text.clear


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vm = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Hiển thị vị trí
        vm.location.observe(viewLifecycleOwner) { binding.tvLocation.text = it }

        // Clicks cho 4 tool
        binding.cardToolPlant.setOnClickListener {
            Toast.makeText(requireContext(), "Plant Identifier", Toast.LENGTH_SHORT).show()
            // TODO: điều hướng tới màn nhận dạng cây
        }
        binding.cardToolDisease.setOnClickListener {
            Toast.makeText(requireContext(), "Disease Identifier", Toast.LENGTH_SHORT).show()
        }
        binding.cardToolLight.setOnClickListener {
            Toast.makeText(requireContext(), "Light Meter", Toast.LENGTH_SHORT).show()
        }
        binding.cardToolWater.setOnClickListener {
            Toast.makeText(requireContext(), "Water Meter", Toast.LENGTH_SHORT).show()
        }
        binding.tvLocation.text = "Hà Nội"           // sau sẽ gán từ GPS
        binding.tvTemp.text = "30°C"
        binding.tvTempRange.text = "(30°C · 30°C)"
        binding.etSearch.doAfterTextChanged {
            binding.btnClear.isVisible = !it.isNullOrEmpty()
        }
        binding.btnClear.setOnClickListener { binding.etSearch.text?.clear() }



        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
