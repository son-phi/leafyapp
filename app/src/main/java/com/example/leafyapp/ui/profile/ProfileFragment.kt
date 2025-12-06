package com.example.leafyapp.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.leafyapp.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClicks()
    }

    private fun setupClicks() {
        // 1. Xử lý click nút LOCATION (Quan trọng)
        binding.rowLocation.setOnClickListener {
            openAppSettings()
        }

        // Các nút khác
        binding.rowRestore.setOnClickListener {
            Toast.makeText(context, "Restore Clicked", Toast.LENGTH_SHORT).show()
        }

        binding.rowNotifications.setOnClickListener {
            // Mở cài đặt thông báo (nếu muốn)
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }
    }

    // Hàm mở Cài đặt của ứng dụng
    private fun openAppSettings() {
        try {
            // Intent mở màn hình "App Info" của ứng dụng hiện tại
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

            // Truyền package name để máy biết mở cài đặt của app nào
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            intent.data = uri

            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Phòng trường hợp máy không hỗ trợ thì mở cài đặt chung
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}