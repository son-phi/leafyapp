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
import com.example.leafyapp.ui.camera.LoadingActivity // Đảm bảo import đúng đường dẫn LoadingActivity

class MyPlantsFragment : Fragment() {

    private var _binding: FragmentMyPlantsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GardenViewModel by viewModels()

    // 1. Launcher để chọn ảnh từ thư viện
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    // Chọn ảnh xong -> Mở màn hình Loading để nhận diện
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
                Toast.makeText(context, "Clicked: ${plant.nickname}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvMyPlants.adapter = adapter
        binding.rvMyPlants.layoutManager = LinearLayoutManager(requireContext())

        // Quan sát dữ liệu
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

        // Sự kiện nút Add
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
                // CHUYỂN SANG CAMERA FRAGMENT
                // Lưu ý: ID này phải khớp với id trong file navigation (nav_graph.xml) của bạn.
                // Thường là R.id.navigation_camera hoặc R.id.cameraFragment
                try {
                    findNavController().navigate(R.id.navigation_camera)
                } catch (e: Exception) {
                    // Nếu lỗi ID, thông báo để bạn sửa lại ID cho đúng
                    Toast.makeText(requireContext(), "Lỗi: Chưa tìm thấy ID navigation_camera", Toast.LENGTH_SHORT).show()
                }
            },
            onGalleryClick = {
                // MỞ THƯ VIỆN ẢNH
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                galleryLauncher.launch(intent)
            }
        )
        bottomSheet.show(parentFragmentManager, "AddPlantBottomSheet")
    }

    // Hàm chuyển sang LoadingActivity để nhận diện cây
    private fun openLoadingActivity(path: String) {
        val intent = Intent(requireContext(), LoadingActivity::class.java)
        intent.putExtra("PHOTO_PATH", path)
        intent.putExtra("SCAN_MODE", "Plant") // Bắt buộc set mode là Plant vì đang ở trong Garden
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}