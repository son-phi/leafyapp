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
import androidx.fragment.app.viewModels
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
    private val viewModel: GardenViewModel by viewModels({ requireParentFragment() })

    private var selectedPlant: UserPlant? = null
    private var selectedTaskType: TaskType? = null
    private var selectedFrequency: Int = 1
    private var selectedHour: Int = 8
    private var selectedMinute: Int = 0

    private var editingTaskId: Long = -1L
    private var editingTask: CareTask? = null

    private var availablePlants: List<UserPlant> = emptyList()

    companion object {
        fun newInstance(taskId: Long = -1L): CreateTaskBottomSheet {
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

        editingTaskId = arguments?.getLong("TASK_ID", -1L) ?: -1L

        viewModel.allUserPlants.observe(viewLifecycleOwner) { plants ->
            availablePlants = plants

            if (editingTaskId != -1L) {
                loadTaskData(plants)
            } else {
                binding.tvSheetTitle.text = "Create Task"
                binding.btnDeleteTask.visibility = View.GONE

                if (plants.size == 1 && selectedPlant == null) {
                    selectedPlant = plants[0]
                    binding.tvSelectedPlant.text = selectedPlant?.nickname
                }
            }
        }

        setupClickEvents()
    }

    private fun loadTaskData(plants: List<UserPlant>) {
        // --- SỬA LỖI TẠI ĐÂY ---
        // Lấy danh sách đã gom nhóm (groupedTasksForSelectedDate)
        val groupedTasks = viewModel.groupedTasksForSelectedDate.value ?: emptyList()

        // Tìm task trong cấu trúc lồng nhau: List<Group> -> List<Task>
        var foundTask: CareTask? = null

        for (group in groupedTasks) {
            val task = group.tasks.find { it.id == editingTaskId }
            if (task != null) {
                foundTask = task
                break
            }
        }

        // Nếu không tìm thấy trong ngày hiện tại (do đổi lịch), thử tìm bằng query DB riêng (optional)
        // Ở đây để đơn giản, nếu không thấy thì coi như lỗi hoặc task đã bị xóa

        if (foundTask != null) {
            editingTask = foundTask
            selectedPlant = plants.find { it.id == foundTask!!.userPlantId }
            selectedTaskType = foundTask!!.type
            selectedFrequency = foundTask!!.frequencyDays
            selectedHour = foundTask!!.timeHour
            selectedMinute = foundTask!!.timeMinute

            // Update UI
            binding.tvSheetTitle.text = "Edit Task"
            binding.tvSelectedPlant.text = selectedPlant?.nickname
            binding.tvSelectedTaskType.text = selectedTaskType?.displayName
            binding.tvSelectedFrequency.text = "$selectedFrequency Days"
            binding.tvSelectedTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            binding.switchAutoReminder.isChecked = foundTask!!.isAutoReminder

            binding.btnDeleteTask.visibility = View.VISIBLE
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
        val plantNames = availablePlants.map { it.nickname }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Plant")
            .setItems(plantNames) { _, which ->
                selectedPlant = availablePlants[which]
                binding.tvSelectedPlant.text = selectedPlant?.nickname
            }
            .show()
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
                    0 -> count
                    1 -> count * 7
                    2 -> count * 30
                    3 -> count * 365
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
        if (selectedPlant == null) {
            Toast.makeText(context, "Please select a plant!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTaskType == null) {
            Toast.makeText(context, "Please select task type!", Toast.LENGTH_SHORT).show()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)

        var timeMillis = calendar.timeInMillis
        if (timeMillis < System.currentTimeMillis() && editingTaskId == -1L) {
            timeMillis += 24 * 60 * 60 * 1000
        }

        val taskToSave = if (editingTaskId != -1L && editingTask != null) {
            // UPDATE
            editingTask!!.copy(
                userPlantId = selectedPlant!!.id,
                type = selectedTaskType!!,
                frequencyDays = selectedFrequency,
                timeHour = selectedHour,
                timeMinute = selectedMinute,
                nextDueDate = timeMillis,
                isAutoReminder = binding.switchAutoReminder.isChecked
            )
        } else {
            // CREATE NEW
            CareTask(
                userPlantId = selectedPlant!!.id,
                type = selectedTaskType!!,
                frequencyDays = selectedFrequency,
                timeHour = selectedHour,
                timeMinute = selectedMinute,
                startDate = timeMillis,
                nextDueDate = timeMillis,
                isAutoReminder = binding.switchAutoReminder.isChecked
            )
        }

        viewModel.insertTask(taskToSave)

        val message = if (editingTaskId != -1L) "Task Updated" else "Task Created"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}