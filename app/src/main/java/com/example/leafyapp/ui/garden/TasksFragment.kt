package com.example.leafyapp.ui.garden

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // <--- QUAN TRỌNG: Import cái này
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

    // [QUAN TRỌNG] Đổi viewModels() -> activityViewModels()
    // Để nhận biết được chế độ Family/Personal từ GardenFragment
    private val viewModel: GardenViewModel by activityViewModels()

    private var isViewingToday = true
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
        // Đảm bảo LayoutManager cho Calendar là Horizontal (nếu trong XML chưa set)
        binding.rvCalendar.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val calendarAdapter = CalendarAdapter { selectedDate ->
            viewModel.setSelectedDate(selectedDate)

            // --- ĐOẠN CODE MỚI ---
            // Tách format riêng cho Tháng (MMM - Viết tắt 3 chữ) và Năm (yyyy)
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault()) // Ví dụ: Nov, Dec
            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault()) // Ví dụ: 2025

            // Cập nhật lên 2 TextView mới
            binding.tvMonth.text = monthFormat.format(selectedDate)
            binding.tvYear.text = yearFormat.format(selectedDate)
            // ---------------------

            isViewingToday = isSameDay(selectedDate, Date())
            taskAdapter.updateSelectedDate(selectedDate.time)
        }

        binding.rvCalendar.adapter = calendarAdapter

        // Scroll đến ngày hôm nay
        val todayPos = calendarAdapter.getSelectedPositionInt()
        if (todayPos != -1) {
            (binding.rvCalendar.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(todayPos - 2, 0)
        }
    }

    private fun setupTasksList() {
        taskAdapter = TaskGroupAdapter(
            selectedDateMillis = System.currentTimeMillis(),
            onTaskChecked = { task ->
                // Lấy thời gian thực tế lúc bấm hoàn thành
                val completedAt = System.currentTimeMillis()
                viewModel.markTaskAsCompleted(task, completedAt)
                Toast.makeText(context, "Completed!", Toast.LENGTH_SHORT).show()
            },
            onTaskClick = { task ->
                showEditDeleteDialog(task)
            }
        )

        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())

        // Lắng nghe dữ liệu từ ViewModel CHUNG
        // Khi switch gạt -> groupedTasksForSelectedDate tự thay đổi -> List tự cập nhật
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
        // 1. Inflate Layout mới
        val dialogView = layoutInflater.inflate(com.example.leafyapp.R.layout.dialog_task_options, null)

        // 2. Tìm các view
        val cardEdit = dialogView.findViewById<View>(com.example.leafyapp.R.id.card_edit)
        val cardDelete = dialogView.findViewById<View>(com.example.leafyapp.R.id.card_delete)

        // 3. Tạo Dialog với Theme Leafy (Bo tròn, nền trắng)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), com.example.leafyapp.R.style.LeafyDialogTheme)
            .setTitle("Manage Task: ${task.type.displayName}") // Tiêu đề
            .setView(dialogView)
            .setNegativeButton("Cancel", null) // Nút Cancel bên dưới
            .create()

        // 4. Xử lý sự kiện Click

        // --- Nút SỬA ---
        cardEdit.setOnClickListener {
            dialog.dismiss() // Đóng dialog trước
            // Mở BottomSheet sửa
            val bottomSheet = CreateTaskBottomSheet.newInstance(task.id)
            bottomSheet.show(childFragmentManager, "EditTaskBottomSheet")
        }

        // --- Nút XÓA ---
        cardDelete.setOnClickListener {
            // Có thể thêm 1 dialog xác nhận "Bạn có chắc chắn muốn xóa?" ở đây nếu muốn an toàn hơn
            // Hiện tại làm xóa luôn cho nhanh như code cũ của bạn

            viewModel.deleteTask(task)
            Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
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