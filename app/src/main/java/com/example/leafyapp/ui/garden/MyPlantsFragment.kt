package com.example.leafyapp.ui.garden

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.R
import com.example.leafyapp.databinding.FragmentMyPlantsBinding
import com.example.leafyapp.ui.camera.LoadingActivity
import com.example.leafyapp.ui.information.ResultActivity // Import Activity kết quả

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

        // Setup RecyclerView
        val adapter = UserPlantAdapter(
            onDeleteClick = { plant ->
                viewModel.delete(plant)
                Toast.makeText(context, "Đã xóa ${plant.nickname}", Toast.LENGTH_SHORT).show()
            },
            onItemClick = { plant ->
                // --- SỬA ĐỔI: Mở màn hình thông tin cây khi click ---
                val intent = Intent(requireContext(), ResultActivity::class.java)

                // Lưu ý: PlantFragment của bạn đang dùng logic (id + 1) để lấy cây từ DB
                // Do đó ở đây ta truyền (plantId - 1) để khi vào trong nó cộng 1 là vừa khớp
                // Hoặc nếu bạn đã sửa PlantFragment thì truyền thẳng plantId.
                // Giả định logic cũ: ID từ AI trả về (0-based) -> DB (1-based)
                intent.putExtra("RESULT_ID", plant.plantId - 1)

                intent.putExtra("RESULT_LABEL", plant.nickname)
                intent.putExtra("RESULT_MODE", "Plant")
                // Không cần PHOTO_PATH vì PlantFragment đã sửa để load từ DB

                startActivity(intent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}