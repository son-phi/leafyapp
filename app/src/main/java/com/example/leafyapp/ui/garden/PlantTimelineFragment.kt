package com.example.leafyapp.ui.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.databinding.FragmentPlantTimelineBinding // Cần tạo layout fragment_plant_timeline.xml

class PlantTimelineFragment : Fragment() {

    private var _binding: FragmentPlantTimelineBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GardenViewModel by viewModels() // Share ViewModel nếu cần, hoặc tạo mới

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlantTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val plantId = arguments?.getInt("PLANT_ID") ?: -1
        val adapter = TimelineAdapter()

        binding.rvTimeline.layoutManager = LinearLayoutManager(context)
        binding.rvTimeline.adapter = adapter

        // Nút back
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        if (plantId != -1) {
            viewModel.getPlantTimeline(plantId).observe(viewLifecycleOwner) { items ->
                adapter.submitList(items)
                if (items.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}