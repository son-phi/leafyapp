package com.example.leafyapp.ui.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels // Đảm bảo import này đúng
// import androidx.lifecycle.ViewModelProvider // Hoặc dùng cái này nếu viewModels() lỗi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.databinding.FragmentPlantTimelineBinding

class PlantTimelineFragment : Fragment() {

    private var _binding: FragmentPlantTimelineBinding? = null
    private val binding get() = _binding!!

    // Sử dụng chung ViewModel với Activity hoặc tạo mới tùy kiến trúc của bạn
    // Ở đây mình dùng chung để tận dụng dữ liệu đã load
    private val viewModel: GardenViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlantTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- SỬA 1: Lấy ID dạng String ---
        // Code cũ: val plantId = arguments?.getInt("PLANT_ID") ?: -1
        val plantId = arguments?.getString("PLANT_ID")

        val adapter = TimelineAdapter()

        binding.rvTimeline.layoutManager = LinearLayoutManager(context)
        binding.rvTimeline.adapter = adapter

        // Nút back
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // --- SỬA 2: Kiểm tra null thay vì -1 ---
        if (plantId != null) {
            // viewModel.getPlantTimeline(String) -> Khớp với ViewModel mới
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