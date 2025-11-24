package com.example.leafyapp.ui.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.databinding.FragmentTasksBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GardenViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Setup Calendar Adapter
        val calendarAdapter = CalendarAdapter { selectedDate ->
            viewModel.setSelectedDate(selectedDate)

            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            binding.tvMonthYear.text = monthFormat.format(selectedDate)
        }

        binding.rvCalendar.adapter = calendarAdapter

        // --- SỬA ĐỔI: Scroll đến ngày hôm nay ---
        val todayPosition = calendarAdapter.getSelectedPositionInt()
        if (todayPosition != -1) {
            // Dùng scrollToPositionWithOffset để căn ngày hôm nay ra giữa màn hình (hoặc lệch trái một chút cho đẹp)
            (binding.rvCalendar.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(todayPosition - 2, 0)
        }

        // 2. Setup Task Adapter
        val taskAdapter = TaskAdapter()
        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())

        // 3. Lắng nghe dữ liệu Task
        viewModel.tasksForSelectedDate.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.submitList(tasks)

            if (tasks.isEmpty()) {
                binding.layoutNoTask.visibility = View.VISIBLE
                binding.rvTasks.visibility = View.GONE
            } else {
                binding.layoutNoTask.visibility = View.GONE
                binding.rvTasks.visibility = View.VISIBLE
            }
        }

        // 4. Nút Add Task
        binding.btnAddTask.setOnClickListener {
            // Mở BottomSheet tạo Task
            val bottomSheet = CreateTaskBottomSheet()
            bottomSheet.show(childFragmentManager, "CreateTaskBottomSheet")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}