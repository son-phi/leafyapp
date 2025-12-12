package com.example.leafyapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.navOptions
import com.example.leafyapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // [CÁCH MỚI] Khai báo biến xử lý kết quả xin quyền
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Người dùng đồng ý -> Tốt!
        } else {
            Toast.makeText(this, "Bạn cần cấp quyền để nhận thông báo chăm sóc cây!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseCopier.copyDatabase(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // [QUAN TRỌNG] Tạo kênh thông báo ngay khi mở App
        createNotificationChannel()

        // Setup Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        binding.navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.navView.isVisible = dest.id != R.id.navigation_camera
        }

        binding.navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_camera -> {
                    navController.navigate(
                        R.id.navigation_camera,
                        null,
                        navOptions { launchSingleTop = true }
                    )
                    true
                }
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        // Xử lý khi bấm vào thông báo để mở App
        handleIntent(intent)

        // [CÁCH MỚI] Xin quyền Notification (Android 13+)
        askNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Xử lý logic khi bấm vào thông báo FCM
        // Nếu Server gửi kèm data "screen" = "TasksFragment" -> Chuyển tab
        val screen = intent?.getStringExtra("screen")
        if (screen == "TasksFragment" || intent?.getBooleanExtra("OPEN_MY_GARDEN", false) == true) {
            binding.navView.selectedItemId = R.id.navigation_garden
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Đã có quyền
            } else {
                // Chưa có quyền -> Hiện bảng xin phép
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        // Chỉ cần cho Android 8.0 (Oreo) trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Garden Notifications"
            val descriptionText = "Thông báo nhắc nhở chăm sóc cây"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channelId = "leafy_garden_channel" // ID này phải trùng với trong MyFirebaseMessagingService

            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}