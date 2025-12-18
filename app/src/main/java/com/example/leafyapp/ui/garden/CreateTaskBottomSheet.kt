package com.example.leafyapp.ui.garden

import SimplePlantAdapter
import TaskTypeAdapter
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
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.CareTask
import com.example.leafyapp.data.model.TaskType
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.BottomSheetCreateTaskBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar
import java.util.UUID

class CreateTaskBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateTaskBinding? = null
    private val binding get() = _binding!!

    // Dùng activityViewModels để chia sẻ dữ liệu với GardenFragment
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

        val isMultiSelect = (editingTaskId == null)
        val tempSelectedList = ArrayList<UserPlant>(selectedPlants)

        // Setup RecyclerView
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            // ClipToPadding = false giúp nội dung cuộn lên không bị cắt bóng đổ
            clipToPadding = false
            setPadding(0, 24, 0, 24)
        }

        val adapter = SimplePlantAdapter(
            plants = availablePlants,
            initialSelection = selectedPlants,
            isMultiSelect = isMultiSelect
        ) { currentSelection ->
            tempSelectedList.clear()
            tempSelectedList.addAll(currentSelection)
        }
        recyclerView.adapter = adapter

        // Hiển thị Dialog
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.LeafyDialogTheme)
            .setTitle(if (isMultiSelect) "Select Plants" else "Select Plant")
            .setView(recyclerView)
            .setPositiveButton("OK") { _, _ ->
                selectedPlants.clear()
                selectedPlants.addAll(tempSelectedList)

                // Update Text UI
                binding.tvSelectedPlant.text = if (selectedPlants.isNotEmpty()) {
                    if (selectedPlants.size == 1) selectedPlants[0].nickname
                    else "${selectedPlants.size} plants selected"
                } else "Select"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSelectTaskTypeDialog() {
        // 1. Setup RecyclerView
        val recyclerView = androidx.recyclerview.widget.RecyclerView(requireContext()).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            setPadding(0, 24, 0, 24)
            clipToPadding = false // Để bóng đổ không bị cắt
        }

        val types = TaskType.values()

        // 2. Tạo Dialog Builder
        // Dùng LeafyDialogTheme để có bo góc 24dp và tiêu đề xanh
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.LeafyDialogTheme)
            .setTitle("Remind Me About")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()

        // 3. Gắn Adapter
        val adapter = TaskTypeAdapter(types, selectedTaskType) { newType ->
            // Cập nhật biến tạm
            selectedTaskType = newType

            // Cập nhật UI bên ngoài Dialog
            binding.tvSelectedTaskType.text = newType.displayName

            // (Tùy chọn) Cập nhật cả icon bên ngoài nếu bạn muốn
            // binding.ivTypeIcon.setImageResource(newType.iconResId)
            // binding.ivTypeIcon.setColorFilter(Color.parseColor("#4CAF50"))

            // Đóng dialog sau 150ms để user kịp thấy hiệu ứng chọn xanh
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                dialog.dismiss()
            }, 150)
        }
        recyclerView.adapter = adapter

        dialog.show()
    }

    private fun showSelectFrequencyDialog() {
        // 1. Inflate Layout mới
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_frequency, null)

        val etCount = dialogView.findViewById<android.widget.EditText>(R.id.et_repeat_count)
        val tvUnit = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.tv_repeat_unit)

        // 2. Setup Dropdown Data (Thay thế Spinner)
        val units = arrayOf("Days", "Weeks", "Months", "Years")
        val adapter = android.widget.ArrayAdapter(requireContext(), com.google.android.material.R.layout.support_simple_spinner_dropdown_item, units)
        tvUnit.setAdapter(adapter)

        // Mặc định chọn cái đầu tiên (Days) và không cho nhập tay
        tvUnit.setText(units[0], false) // false để không bung list ra ngay lập tức
        tvUnit.keyListener = null // Chặn không cho user gõ phím vào ô Unit

        // 3. Hiển thị Dialog (Dùng Theme LeafyDialogTheme)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.LeafyDialogTheme)
            .setTitle("Repeat Every")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                // Lấy số lượng
                val countStr = etCount.text.toString()
                val count = if (countStr.isNotEmpty()) countStr.toInt() else 1

                // Lấy đơn vị (Text từ AutoCompleteTextView)
                val unitText = tvUnit.text.toString()

                // Tính toán số ngày
                val days = when (unitText) {
                    "Days" -> count
                    "Weeks" -> count * 7
                    "Months" -> count * 30
                    "Years" -> count * 365
                    else -> count
                }

                // Lưu và cập nhật UI
                selectedFrequency = days
                binding.tvSelectedFrequency.text = "$count $unitText"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePickerDialog() {
        // 1. Inflate Layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_time, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker_spinner)

        // 2. Cấu hình TimePicker
        timePicker.setIs24HourView(true) // Chế độ 24h
        timePicker.hour = selectedHour
        timePicker.minute = selectedMinute

        // 3. Hiển thị Dialog với Theme bo tròn (LeafyDialogTheme)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.LeafyDialogTheme)
            .setTitle("Set Time")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute

                // Format giờ đẹp (VD: 08:05)
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

        // Tính thời gian bắt đầu
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)

        // Logic tự cộng 24h nếu giờ chọn < giờ hiện tại
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            Toast.makeText(context, "Task set for tomorrow as time has passed", Toast.LENGTH_SHORT).show()
        }
        val timeMillis = calendar.timeInMillis

        if (editingTaskId != null && editingTask != null) {
            // --- CẬP NHẬT TASK CŨ ---
            val taskToSave = editingTask!!.copy(
                userPlantId = selectedPlants[0].id,
                // [QUAN TRỌNG] Cập nhật tên cây để Notification hiện đúng
                plantName = selectedPlants[0].nickname,

                type = selectedTaskType!!,
                frequencyDays = selectedFrequency,
                timeHour = selectedHour,
                timeMinute = selectedMinute,
                nextDueDate = timeMillis,
                isAutoReminder = binding.switchAutoReminder.isChecked
            )
            viewModel.updateTask(taskToSave)
            Toast.makeText(context, "Task Updated", Toast.LENGTH_SHORT).show()
        } else {
            // --- TẠO TASK MỚI ---
            for (plant in selectedPlants) {
                val newTask = CareTask(
                    id = "", // Để rỗng, Repository/Firestore sẽ tự sinh ID
                    userPlantId = plant.id,

                    // [QUAN TRỌNG] Lưu tên cây vào Task
                    plantName = plant.nickname,

                    type = selectedTaskType!!,
                    frequencyDays = selectedFrequency,
                    timeHour = selectedHour,
                    timeMinute = selectedMinute,
                    startDate = timeMillis,
                    nextDueDate = timeMillis,
                    isAutoReminder = binding.switchAutoReminder.isChecked,

                    // 2 trường này để ViewModel tự điền (Family/Personal + OwnerID)
                    gardenId = null,
                    ownerId = ""
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