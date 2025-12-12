package com.example.leafyapp.ui.garden

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Dùng activityViewModels để share data
import com.example.leafyapp.data.model.Garden
import com.example.leafyapp.databinding.FragmentGardenBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GardenFragment : Fragment() {

    private var _binding: FragmentGardenBinding? = null
    private val binding get() = _binding!!

    // Sử dụng activityViewModels để giữ trạng thái khi xoay màn hình hoặc chuyển tab
    private val viewModel: GardenViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGardenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupSwitch()
        setupButtons()
        observeViewModel()

        // Kiểm tra xem Activity có yêu cầu mở tab Family không (từ PlantFragment gửi sang)
        checkIntentArguments()
    }

    private fun checkIntentArguments() {
        val shouldOpenFamily = requireActivity().intent.getBooleanExtra("OPEN_FAMILY_MODE", false)
        if (shouldOpenFamily) {
            binding.switchFamilyMode.isChecked = true
            // Xóa cờ để không bị mở lại lần sau
            requireActivity().intent.removeExtra("OPEN_FAMILY_MODE")
        }
    }

    private fun setupViewPager() {
        val fragmentList = listOf(
            MyPlantsFragment(),
            TasksFragment()
        )
        val adapter = GardenPagerAdapter(this, fragmentList)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Your Plants"  // <-- Đã đổi lại tiếng Anh
                1 -> "Tasks"        // <-- Đã đổi lại tiếng Anh
                else -> ""
            }
        }.attach()
    }

    private fun setupSwitch() {
        binding.switchFamilyMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // BẬT CHẾ ĐỘ GIA ĐÌNH (Đã xóa icon ngôi nhà)
                binding.tvModeTitle.text = "Family Garden"
                checkIfUserHasGarden()
            } else {
                // TẮT CHẾ ĐỘ (Đã xóa icon người)
                binding.tvModeTitle.text = "My Personal Garden"
                viewModel.setGardenMode(null)
                updateUI(isFamilyMode = false, hasGarden = false)
            }
        }
    }

    private fun observeViewModel() {
        // Lắng nghe thay đổi vườn hiện tại để cập nhật UI
        viewModel.currentGarden.observe(viewLifecycleOwner) { garden ->
            if (garden != null) {
                // Đang ở trong một vườn cụ thể
                binding.switchFamilyMode.isChecked = true // Đảm bảo switch bật
                updateUI(isFamilyMode = true, hasGarden = true)

                // Hiển thị thông tin
                binding.tvInviteCode.text = garden.inviteCode
                binding.tvMemberCount.text = "Thành viên: ${garden.members.size}"
            }
        }
    }

    private fun updateUI(isFamilyMode: Boolean, hasGarden: Boolean) {
        if (!isFamilyMode) {
            // Mode Riêng tư: Ẩn hết dashboard
            binding.layoutNoGarden.visibility = View.GONE
            binding.layoutGardenInfo.visibility = View.GONE
        } else {
            // Mode Gia đình
            if (hasGarden) {
                binding.layoutNoGarden.visibility = View.GONE
                binding.layoutGardenInfo.visibility = View.VISIBLE
            } else {
                binding.layoutNoGarden.visibility = View.VISIBLE
                binding.layoutGardenInfo.visibility = View.GONE
            }
        }
    }

    // --- LOGIC TỰ ĐỘNG TÌM VƯỜN ---
    private fun checkIfUserHasGarden() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Tìm vườn có chứa UID này
                val snapshot = db.collection("gardens")
                    .whereArrayContains("members", uid)
                    .limit(1)
                    .get()
                    .await()

                withContext(Dispatchers.Main) {
                    if (!snapshot.isEmpty) {
                        // TÌM THẤY VƯỜN -> Set vào ViewModel
                        val doc = snapshot.documents[0]
                        val garden = doc.toObject(Garden::class.java)?.apply { id = doc.id }
                        viewModel.setGardenMode(garden)
                    } else {
                        // KHÔNG THẤY -> Hiện nút Tạo/Nhập
                        // Lúc này viewModel.currentGarden vẫn là null, nhưng switch đang bật
                        updateUI(isFamilyMode = true, hasGarden = false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupButtons() {
        // Nút Tạo vườn mới
        binding.btnCreateGarden.setOnClickListener {
            showCreateGardenDialog()
        }

        // Nút Nhập mã
        binding.btnJoinGarden.setOnClickListener {
            showJoinGardenDialog()
        }

        // Nút Copy mã
        binding.btnCopyCode.setOnClickListener {
            val code = binding.tvInviteCode.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Invite Code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Đã copy mã: $code", Toast.LENGTH_SHORT).show()
        }

        // Nút Rời Vườn
        binding.btnLeaveGarden.setOnClickListener {
            showLeaveConfirmationDialog()
        }
    }

    private fun showLeaveConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Leave Garden?")
            .setMessage("Are you sure you want to leave this family garden? You will need an invite code to join again.")
            .setPositiveButton("Leave") { _, _ ->
                viewModel.leaveCurrentGarden(
                    onSuccess = {
                        Toast.makeText(context, "You left the garden.", Toast.LENGTH_SHORT).show()
                        // Tự động update UI về trạng thái chưa có vườn
                        updateUI(isFamilyMode = true, hasGarden = false)
                    },
                    onError = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- DIALOG TẠO VƯỜN ---
    private fun showCreateGardenDialog() {
        val input = EditText(requireContext())
        input.hint = "Đặt tên cho khu vườn (VD: Nhà Hạnh Phúc)"

        // Padding cho đẹp
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(50, 20, 50, 20)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Tạo Vườn Mới")
            .setView(container)
            .setPositiveButton("Tạo ngay") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    createGardenOnFirebase(name)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun createGardenOnFirebase(name: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // --- SỬA LẠI LOGIC SINH MÃ (FIX CRASH) ---
        val charPool = ('A'..'Z') + ('0'..'9')
        val inviteCode = (1..6)
            .map { charPool.random() }
            .joinToString("") // Ghép 6 ký tự lại thành chuỗi
        // ------------------------------------------

        val newGarden = Garden(
            name = name,
            ownerId = uid,
            inviteCode = inviteCode,
            members = listOf(uid)
        )

        db.collection("gardens")
            .add(newGarden)
            .addOnSuccessListener { docRef ->
                newGarden.id = docRef.id
                viewModel.setGardenMode(newGarden)
                Toast.makeText(context, "Garden Created! Code: $inviteCode", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- DIALOG NHẬP MÃ ---
    private fun showJoinGardenDialog() {
        val input = EditText(requireContext())
        input.hint = "Nhập mã mời 6 ký tự"

        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(50, 20, 50, 20)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Tham gia Vườn")
            .setMessage("Nhập mã code từ người thân:")
            .setView(container)
            .setPositiveButton("Tham gia") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    // Gọi hàm trong ViewModel
                    viewModel.joinGardenByCode(code,
                        onSuccess = {
                            Toast.makeText(context, "Chào mừng về nhà!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}