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
    private var selectedFrequency: Int = 1 // Lưu tổng số ngày
    private var selectedHour: Int = 8
    private var selectedMinute: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.optionSelectPlant.setOnClickListener { showSelectPlantDialog() }
        binding.optionRemindAbout.setOnClickListener { showSelectTaskTypeDialog() }
        binding.optionRepeat.setOnClickListener { showSelectFrequencyDialog() }
        binding.optionTime.setOnClickListener { showTimePickerDialog() }
        binding.btnSaveTask.setOnClickListener { saveTask() }
    }

    // --- 1. CHỌN CÂY ---
    private fun showSelectPlantDialog() {
        val plantList = viewModel.allUserPlants.value ?: emptyList()
        if (plantList.isEmpty()) {
            Toast.makeText(context, "Bạn chưa có cây nào. Hãy thêm cây trước!", Toast.LENGTH_SHORT).show()
            return
        }
        val plantNames = plantList.map { it.nickname }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Plant")
            .setItems(plantNames) { _, which ->
                selectedPlant = plantList[which]
                binding.tvSelectedPlant.text = selectedPlant?.nickname
            }
            .show()
    }

    // --- 2. CHỌN LOẠI TASK ---
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

    // --- 3. CHỌN TẦN SUẤT (CUSTOM) ---
    private fun showSelectFrequencyDialog() {
        // Inflate layout tùy chỉnh
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_repeat, null)
        val etCount = dialogView.findViewById<EditText>(R.id.et_repeat_count)
        val spUnit = dialogView.findViewById<Spinner>(R.id.sp_repeat_unit)

        // Setup Spinner
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

                // Tính ra số ngày tương ứng
                val days = when (unitIndex) {
                    0 -> count // Days
                    1 -> count * 7 // Weeks
                    2 -> count * 30 // Months (xấp xỉ)
                    3 -> count * 365 // Years
                    else -> count
                }

                selectedFrequency = days

                // Hiển thị text cho đẹp (VD: 2 Weeks)
                val unitText = units[unitIndex]
                binding.tvSelectedFrequency.text = "$count $unitText"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- 4. CHỌN GIỜ (CUSTOM SPINNER) ---
    private fun showTimePickerDialog() {
        // Inflate layout tùy chỉnh
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_time, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker_spinner)

        // Cấu hình TimePicker: 24h và set giờ hiện tại
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

    // --- HÀM LƯU ---
    private fun saveTask() {
        if (selectedPlant == null) {
            Toast.makeText(context, "Vui lòng chọn cây!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTaskType == null) {
            Toast.makeText(context, "Vui lòng chọn loại nhiệm vụ!", Toast.LENGTH_SHORT).show()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)

        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val newTask = CareTask(
            userPlantId = selectedPlant!!.id,
            type = selectedTaskType!!,
            frequencyDays = selectedFrequency,
            timeHour = selectedHour,
            timeMinute = selectedMinute,
            nextDueDate = calendar.timeInMillis,
            isAutoReminder = binding.switchAutoReminder.isChecked
        )

        viewModel.insertTask(newTask)
        Toast.makeText(context, "Đã tạo lịch nhắc thành công!", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}