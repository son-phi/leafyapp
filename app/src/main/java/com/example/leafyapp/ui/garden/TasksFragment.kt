package com.example.leafyapp.ui.garden

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.databinding.FragmentTasksBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GardenViewModel by viewModels()

    private var isViewingToday = true

    // SỬA: Dùng TaskGroupAdapter thay vì TaskAdapter
    private lateinit var taskAdapter: TaskGroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTasksList()
        setupCalendar()

        binding.btnAddTask.setOnClickListener {
            val bottomSheet = CreateTaskBottomSheet.newInstance()
            bottomSheet.show(childFragmentManager, "CreateTaskBottomSheet")
        }
    }

    private fun setupCalendar() {
        val calendarAdapter = CalendarAdapter { selectedDate ->
            viewModel.setSelectedDate(selectedDate)

            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            binding.tvMonthYear.text = monthFormat.format(selectedDate)

            isViewingToday = isSameDay(selectedDate, Date())

            taskAdapter.updateSelectedDate(selectedDate.time)
        }

        binding.rvCalendar.adapter = calendarAdapter

        val todayPos = calendarAdapter.getSelectedPositionInt()
        if (todayPos != -1) {
            (binding.rvCalendar.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(todayPos - 2, 0)
        }
    }

    private fun setupTasksList() {
        // Adapter mới
        taskAdapter = TaskGroupAdapter(
            selectedDateMillis = System.currentTimeMillis(),
            onTaskChecked = { task ->
                // --- SỬA Ở ĐÂY (QUAN TRỌNG) ---
                // Code cũ: val completedAt = viewModel.getSelectedDayStart() -> Gây lỗi 00:00
                // Code mới: Lấy thời gian thực tế lúc bấm nút
                val completedAt = System.currentTimeMillis()

                viewModel.markTaskAsCompleted(task, completedAt)
                Toast.makeText(context, "Đã hoàn thành!", Toast.LENGTH_SHORT).show()
            },
            onTaskClick = { task ->
                showEditDeleteDialog(task)
            }
        )


        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())

        // Lắng nghe dữ liệu đã gom nhóm
        viewModel.groupedTasksForSelectedDate.observe(viewLifecycleOwner) { groupedList ->
            taskAdapter.submitList(groupedList)

            if (groupedList.isEmpty()) {
                binding.layoutNoTask.visibility = View.VISIBLE
                binding.rvTasks.visibility = View.GONE
            } else {
                binding.layoutNoTask.visibility = View.GONE
                binding.rvTasks.visibility = View.VISIBLE
            }
        }
    }

    private fun showEditDeleteDialog(task: CareTask) {
        val options = arrayOf("Delete Task", "Edit Task", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Task: ${task.type.displayName}")
            .setItems(options) { dialog, which ->
                if (which == 0) {
                    viewModel.deleteTask(task)
                    Toast.makeText(context, "Đã xóa task", Toast.LENGTH_SHORT).show()
                } else if (which == 1) {
                    val bottomSheet = CreateTaskBottomSheet.newInstance(task.id)
                    bottomSheet.show(childFragmentManager, "EditTaskBottomSheet")
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}