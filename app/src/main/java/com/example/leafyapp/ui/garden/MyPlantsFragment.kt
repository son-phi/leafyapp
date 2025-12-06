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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.R
import com.example.leafyapp.data.model.UserPlant
import com.example.leafyapp.databinding.FragmentMyPlantsBinding
import com.example.leafyapp.ui.camera.LoadingActivity
import com.example.leafyapp.ui.information.ResultActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class MyPlantsFragment : Fragment() {

    private var _binding: FragmentMyPlantsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GardenViewModel by viewModels()

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

        // Setup RecyclerView với Adapter mới
        val adapter = UserPlantAdapter(
            // Callback 1: Khi bấm nút 3 chấm (Menu)
            onMenuClick = { view, plant ->
                showPlantOptionsBottomSheet(plant) // Gọi hàm private trong class này
            },
            // Callback 2: Khi bấm vào item cây
            onItemClick = { plant ->
//                val intent = Intent(requireContext(), ResultActivity::class.java)
//                intent.putExtra("RESULT_ID", plant.plantId - 1)
//                intent.putExtra("RESULT_LABEL", plant.nickname)
//                intent.putExtra("RESULT_MODE", "Plant")
//                startActivity(intent)
                val timelineFragment = PlantTimelineFragment().apply {
                    arguments = Bundle().apply {
                        putInt("PLANT_ID", plant.id) // Chú ý: dùng plant.id (Primary Key)
                    }
                }

                // Thay thế Fragment hiện tại bằng TimelineFragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment_activity_main, timelineFragment) // ID của container chính trong Activity
                    .addToBackStack(null) // Để user bấm Back sẽ quay lại danh sách cây
                    .commit()
            }
        )

        binding.rvMyPlants.adapter = adapter
        binding.rvMyPlants.layoutManager = LinearLayoutManager(requireContext())

        viewModel.allUserPlants.observe(viewLifecycleOwner) { plants ->
            adapter.submitList(plants)

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

        binding.btnTestAddDebug.setOnClickListener {
            showAddPlantBottomSheet()
        }
    }

    // --- CÁC HÀM RIÊNG CỦA FRAGMENT (Đặt ở đây là đúng) ---

    private fun showAddPlantBottomSheet() {
        val bottomSheet = AddPlantBottomSheet(
            onTakePhotoClick = {
                try {
                    findNavController().navigate(R.id.navigation_camera)
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

    // Hàm hiển thị Bottom Sheet tùy chọn (Sửa/Xóa)
    private fun showPlantOptionsBottomSheet(plant: UserPlant) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_plant_options, null)
        dialog.setContentView(view)

        // Xử lý nút Sửa tên
        view.findViewById<View>(R.id.option_edit_name).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(plant)
        }

        // Xử lý nút Xóa
        view.findViewById<View>(R.id.option_delete_plant).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(plant)
        }

        dialog.show()
    }

    // Dialog đổi tên
    private fun showRenameDialog(plant: UserPlant) {
        val input = EditText(requireContext())
        input.setText(plant.nickname)

        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 50; params.rightMargin = 50
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Plant Name")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.updatePlantName(plant, newName)
                    Toast.makeText(context, "Updated name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dialog xóa
    private fun showDeleteConfirmation(plant: UserPlant) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Plant")
            .setMessage("Are you sure you want to delete '${plant.nickname}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete(plant)
                Toast.makeText(context, "Deleted ${plant.nickname}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}