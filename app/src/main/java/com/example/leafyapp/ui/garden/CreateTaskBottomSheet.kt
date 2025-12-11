package com.example.leafyapp.ui.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TimePicker
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels // <--- QUAN TRỌNG: Dùng cái này
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskType
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.BottomSheetCreateTaskBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class CreateTaskBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateTaskBinding? = null
    private val binding get() = _binding!!

    // [QUAN TRỌNG] Đổi thành activityViewModels() để nhận biết chế độ Family/Personal từ GardenFragment
    private val viewModel: GardenViewModel by activityViewModels()

    private val selectedPlants = ArrayList<UserPlant>()

    private var selectedTaskType: TaskType? = null
    private var selectedFrequency: Int = 1
    private var selectedHour: Int = 8
    private var selectedMinute: Int = 0

    // ID task đang sửa (String từ Firebase)
    private var editingTaskId: String? = null
    private var editingTask: CareTask? = null
    private var availablePlants: List<UserPlant> = emptyList()

    companion object {
        fun newInstance(taskId: String? = null): CreateTaskBottomSheet {
            return CreateTaskBottomSheet().apply {
                arguments = bundleOf("TASK_ID" to taskId)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lấy ID task cần sửa (nếu có)
        editingTaskId = arguments?.getString("TASK_ID")

        // Lắng nghe danh sách cây từ ViewModel CHUNG
        viewModel.allUserPlants.observe(viewLifecycleOwner) { plants ->
            availablePlants = plants

            if (editingTaskId != null) {
                // Nếu đang sửa -> Load dữ liệu cũ lên
                loadTaskData(plants)
            } else {
                // Nếu tạo mới -> UI mặc định
                binding.tvSheetTitle.text = "Create Task"
                binding.btnDeleteTask.visibility = View.GONE
            }
        }
        setupClickEvents()
    }

    private fun loadTaskData(plants: List<UserPlant>) {
        val groupedTasks = viewModel.groupedTasksForSelectedDate.value ?: emptyList()
        var foundTask: CareTask? = null

        // Tìm task cần sửa trong danh sách hiện có
        for (group in groupedTasks) {
            val task = group.tasks.find { it.id == editingTaskId }
            if (task != null) {
                foundTask = task
                break
            }
        }

        if (foundTask != null) {
            editingTask = foundTask
            // Tìm cây tương ứng với task này
            val plant = plants.find { it.id == foundTask!!.userPlantId }
            if (plant != null) {
                selectedPlants.clear()
                selectedPlants.add(plant)
            }

            // Gán các giá trị cũ vào biến tạm
            selectedTaskType = foundTask!!.type
            selectedFrequency = foundTask!!.frequencyDays
            selectedHour = foundTask!!.timeHour
            selectedMinute = foundTask!!.timeMinute

            // Cập nhật UI
            binding.tvSheetTitle.text = "Edit Task"
            binding.tvSelectedPlant.text = selectedPlants.firstOrNull()?.nickname ?: "Unknown Plant"
            binding.tvSelectedTaskType.text = selectedTaskType?.displayName
            binding.tvSelectedFrequency.text = "$selectedFrequency Days"
            binding.tvSelectedTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            binding.switchAutoReminder.isChecked = foundTask!!.isAutoReminder

            binding.btnDeleteTask.visibility = View.VISIBLE
        } else {
            // Trường hợp hy hữu: Có ID nhưng không tìm thấy Task (do load chậm hoặc đã bị xóa)
            Toast.makeText(context, "Task not found!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setupClickEvents() {
        binding.optionSelectPlant.setOnClickListener { showSelectPlantDialog() }
        binding.optionRemindAbout.setOnClickListener { showSelectTaskTypeDialog() }
        binding.optionRepeat.setOnClickListener { showSelectFrequencyDialog() }
        binding.optionTime.setOnClickListener { showTimePickerDialog() }
        binding.btnSaveTask.setOnClickListener { saveTask() }
        binding.btnCloseSheet.setOnClickListener { dismiss() }

        binding.btnDeleteTask.setOnClickListener {
            if (editingTask != null) {
                viewModel.deleteTask(editingTask!!)
                Toast.makeText(context, "Task Deleted", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun showSelectPlantDialog() {
        if (availablePlants.isEmpty()) {
            Toast.makeText(context, "No plants available. Please add a plant first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (editingTaskId != null) {
            // CHẾ ĐỘ SỬA: Chỉ được chọn 1 cây (Radio Button)
            val plantNames = availablePlants.map { it.nickname }.toTypedArray()

            // Tìm vị trí cây đang chọn hiện tại
            var checkedItem = -1
            if (selectedPlants.isNotEmpty()) {
                checkedItem = availablePlants.indexOfFirst { it.id == selectedPlants[0].id }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Plant")
                .setSingleChoiceItems(plantNames, checkedItem) { dialog, which ->
                    selectedPlants.clear()
                    selectedPlants.add(availablePlants[which])
                    binding.tvSelectedPlant.text = selectedPlants[0].nickname
                    dialog.dismiss() // Đóng dialog ngay sau khi chọn
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // CHẾ ĐỘ TẠO MỚI: Được chọn nhiều cây (Checkbox)
            val plantNames = availablePlants.map { it.nickname }.toTypedArray()
            val checkedItems = BooleanArray(availablePlants.size) { i ->
                selectedPlants.any { it.id == availablePlants[i].id }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Plants")
                .setMultiChoiceItems(plantNames, checkedItems) { _, which, isChecked ->
                    if (isChecked) {
                        // Tránh thêm trùng lặp
                        if (selectedPlants.none { it.id == availablePlants[which].id }) {
                            selectedPlants.add(availablePlants[which])
                        }
                    } else {
                        selectedPlants.removeAll { it.id == availablePlants[which].id }
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    if (selectedPlants.isNotEmpty()) {
                        if (selectedPlants.size == 1) {
                            binding.tvSelectedPlant.text = selectedPlants[0].nickname
                        } else {
                            binding.tvSelectedPlant.text = "${selectedPlants.size} plants selected"
                        }
                    } else {
                        binding.tvSelectedPlant.text = "Select"
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showSelectTaskTypeDialog() {
        val types = TaskType.values()
        val typeNames = types.map { it.displayName }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remind Me About")
            .setItems(typeNames) { _, which ->
                selectedTaskType = types[which]
                binding.tvSelectedTaskType.text = selectedTaskType?.displayName
            }
            .show()
    }

    private fun showSelectFrequencyDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_repeat, null)
        val etCount = dialogView.findViewById<EditText>(R.id.et_repeat_count)
        val spUnit = dialogView.findViewById<Spinner>(R.id.sp_repeat_unit)
        val units = arrayOf("Days", "Weeks", "Months", "Years")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, units)
        spUnit.adapter = adapter

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Repeat Every")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val countStr = etCount.text.toString()
                val count = if (countStr.isNotEmpty()) countStr.toInt() else 1
                val unitIndex = spUnit.selectedItemPosition
                val days = when (unitIndex) {
                    0 -> count          // Days
                    1 -> count * 7      // Weeks
                    2 -> count * 30     // Months (Xấp xỉ)
                    3 -> count * 365    // Years
                    else -> count
                }
                selectedFrequency = days
                val unitText = units[unitIndex]
                binding.tvSelectedFrequency.text = "$count $unitText"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePickerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_time, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker_spinner)
        timePicker.setIs24HourView(true)
        timePicker.hour = selectedHour
        timePicker.minute = selectedMinute

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Time")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                val timeStr = String.format("%02d:%02d", selectedHour, selectedMinute)
                binding.tvSelectedTime.text = timeStr
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveTask() {
        if (selectedPlants.isEmpty()) {
            Toast.makeText(context, "Please select at least one plant!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTaskType == null) {
            Toast.makeText(context, "Please select task type!", Toast.LENGTH_SHORT).show()
            return
        }

        // Tính thời gian bắt đầu (Ngày hôm nay + Giờ đã chọn)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)
        val timeMillis = calendar.timeInMillis

        if (editingTaskId != null && editingTask != null) {
            // --- TRƯỜNG HỢP 1: CẬP NHẬT TASK CŨ ---
            val taskToSave = editingTask!!.copy(
                userPlantId = selectedPlants[0].id, // Lấy cây đầu tiên (vì edit chỉ cho chọn 1)
                type = selectedTaskType!!,
                frequencyDays = selectedFrequency,
                timeHour = selectedHour,
                timeMinute = selectedMinute,
                nextDueDate = timeMillis, // Reset lại ngày nhắc tiếp theo
                isAutoReminder = binding.switchAutoReminder.isChecked
            )
            viewModel.updateTask(taskToSave)
            Toast.makeText(context, "Task Updated", Toast.LENGTH_SHORT).show()
        } else {
            // --- TRƯỜNG HỢP 2: TẠO TASK MỚI (Cho nhiều cây) ---
            for (plant in selectedPlants) {
                val newTask = CareTask(
                    id = "", // Để rỗng, Repository sẽ tự tạo ID mới
                    userPlantId = plant.id,
                    type = selectedTaskType!!,
                    frequencyDays = selectedFrequency,
                    timeHour = selectedHour,
                    timeMinute = selectedMinute,
                    startDate = timeMillis,
                    nextDueDate = timeMillis,
                    isAutoReminder = binding.switchAutoReminder.isChecked
                )
                viewModel.insertTask(newTask)
            }
            Toast.makeText(context, "${selectedPlants.size} Tasks Created", Toast.LENGTH_SHORT).show()
        }

        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}