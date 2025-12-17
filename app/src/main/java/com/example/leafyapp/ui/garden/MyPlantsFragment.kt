package com.example.leafyapp.ui.garden

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // <--- QUAN TRỌNG: Nhớ import cái này
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.R
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentMyPlantsBinding
import com.example.leafyapp.ui.camera.LoadingActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class MyPlantsFragment : Fragment() {

    private var _binding: FragmentMyPlantsBinding? = null
    private val binding get() = _binding!!

    // [QUAN TRỌNG] Đổi viewModels() -> activityViewModels()
    // Để nhận được tín hiệu khi gạt Switch từ GardenFragment
    private val viewModel: GardenViewModel by activityViewModels()

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    openLoadingActivity(uri.toString())
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyPlantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView với Adapter
        val adapter = UserPlantAdapter(
            // Callback 1: Khi bấm nút 3 chấm (Menu)
            onMenuClick = { view, plant ->
                showPlantOptionsBottomSheet(plant)
            },
            // Callback 2: Khi bấm vào item cây -> Mở Timeline
            // Callback 2: Khi bấm vào item cây -> Mở Timeline
            onItemClick = { plant ->
                // 1. Tạo gói dữ liệu (Bundle) chứa ID cây
                val bundle = android.os.Bundle().apply {
                    putString("PLANT_ID", plant.id)
                }

                try {
                    // Cách 1: Thử điều hướng từ Fragment hiện tại
                    findNavController().navigate(R.id.action_garden_to_timeline, bundle)
                } catch (e: Exception) {
                    // Cách 2: Nếu lỗi, tìm NavController từ Activity cha (Sửa lỗi Too many arguments)
                    androidx.navigation.Navigation.findNavController(
                        requireActivity(),
                        R.id.nav_host_fragment_activity_main
                    ).navigate(R.id.action_garden_to_timeline, bundle)
                }
            }
        )

        binding.rvMyPlants.adapter = adapter
        binding.rvMyPlants.layoutManager = LinearLayoutManager(requireContext())

        // Lắng nghe dữ liệu từ ViewModel CHUNG
        viewModel.allUserPlants.observe(viewLifecycleOwner) { plants ->
            adapter.submitList(plants)

            // Hiển thị giao diện trống nếu không có cây
            if (plants.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvMyPlants.visibility = View.GONE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvMyPlants.visibility = View.VISIBLE
            }
        }

        binding.btnAddFirstPlant.setOnClickListener {
            showAddPlantBottomSheet()
        }
    }

    // --- CÁC HÀM UI LOGIC (GIỮ NGUYÊN CỦA BẠN) ---

    private fun showAddPlantBottomSheet() {
        // Kiểm tra xem class AddPlantBottomSheet của bạn có tồn tại không
        // Nếu không thì bạn phải tạo class đó hoặc dùng code tạo dialog thủ công
        // Giả sử class đó đã có sẵn:
        val bottomSheet = AddPlantBottomSheet(
            onTakePhotoClick = {
                try {
                    val args = bundleOf("CAMERA_MODE" to "Plant")
                    findNavController().navigate(R.id.navigation_camera, args)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Lỗi điều hướng Camera", Toast.LENGTH_SHORT).show()
                }
            },
            onGalleryClick = {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                galleryLauncher.launch(intent)
            }
        )
        bottomSheet.show(parentFragmentManager, "AddPlantBottomSheet")
    }

    private fun openLoadingActivity(path: String) {
        val intent = Intent(requireContext(), LoadingActivity::class.java)
        intent.putExtra("PHOTO_PATH", path)
        intent.putExtra("SCAN_MODE", "Plant")
        startActivity(intent)
    }

    private fun showPlantOptionsBottomSheet(plant: UserPlant) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_plant_options, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.option_edit_name).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(plant)
        }

        view.findViewById<View>(R.id.option_delete_plant).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(plant)
        }

        dialog.show()
    }

    private fun showRenameDialog(plant: UserPlant) {
        // 1. Inflate Layout
        val dialogView = layoutInflater.inflate(com.example.leafyapp.R.layout.dialog_rename_plant, null)

        // 2. Ánh xạ View
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.leafyapp.R.id.et_plant_name)
        val btnSave = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_save)
        val btnCancel = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_cancel)

        // 3. Fill dữ liệu cũ & đưa con trỏ về cuối
        etName.setText(plant.nickname)
        etName.setSelection(plant.nickname.length) // Đưa con trỏ xuống cuối dòng cho tiện

        // 4. Tạo Dialog
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        val dialog = builder.create()

        // --- QUAN TRỌNG: Làm nền trong suốt để bo góc ---
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        // Tự động bật bàn phím khi dialog hiện lên (Tùy chọn, giúp UX tốt hơn)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        // 5. Xử lý sự kiện
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()

            if (newName.isNotEmpty()) {
                // Gọi ViewModel cập nhật
                viewModel.updatePlantName(plant, newName)
                android.widget.Toast.makeText(context, "Updated name to '$newName'", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                // Báo lỗi nhẹ nhàng bằng cách set Error cho EditText
                etName.error = "Name cannot be empty"
            }
        }

        dialog.show()
    }

    // Hàm này dùng chung cho các dialog xác nhận
    private fun showDeleteConfirmation(plant: UserPlant) {
        showConfirmationDialog(
            title = "Delete Plant",
            message = "Are you sure you want to delete '${plant.nickname}'? This action cannot be undone.",
            positiveButtonTitle = "Delete",
            isDestructive = true // QUAN TRỌNG: Để nút hiện màu đỏ cảnh báo
        ) {
            // Hành động khi người dùng bấm nút Delete
            viewModel.delete(plant)
            android.widget.Toast.makeText(context, "Deleted ${plant.nickname}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Hàm này dùng chung cho các dialog xác nhận
    private fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButtonTitle: String,
        isDestructive: Boolean = false, // True = Màu đỏ, False = Màu xanh
        onConfirm: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(com.example.leafyapp.R.layout.dialog_confirmation, null)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(com.example.leafyapp.R.id.tv_title)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(com.example.leafyapp.R.id.tv_message)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.leafyapp.R.id.btn_confirm)
        val btnCancel = dialogView.findViewById<android.view.View>(com.example.leafyapp.R.id.btn_cancel)

        tvTitle.text = title
        tvMessage.text = message
        btnConfirm.text = positiveButtonTitle

        // Xử lý màu nút
        if (isDestructive) {
            btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D32F2F")) // Đỏ
        } else {
            btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")) // Xanh
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        val dialog = builder.create()

        // Nền trong suốt để bo góc đẹp
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}