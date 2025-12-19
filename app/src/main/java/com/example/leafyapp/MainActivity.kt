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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.navOptions
import com.example.leafyapp.databinding.ActivityMainBinding
import com.example.leafyapp.ui.garden.GardenViewModel
import com.example.leafyapp.data.model.Garden

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gardenViewModel: GardenViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Quyền thông báo đã được cấp
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

        // Tạo kênh thông báo
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

        handleIntent(intent)
        askNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val screen = it.getStringExtra("screen")
            val openGarden = it.getBooleanExtra("OPEN_GARDEN", false) ||
                    it.getBooleanExtra("OPEN_MY_GARDEN", false) ||
                    it.getBooleanExtra("NAVIGATE_TO_GARDEN", false) ||
                    screen == "TasksFragment"

            if (openGarden) {
                // 1. Chuyển sang Tab Garden
                binding.navView.selectedItemId = R.id.navigation_garden

                // 2. Xử lý logic Garden Mode (nếu có)
                if (it.hasExtra("IS_FAMILY_MODE")) {
                    val isFamily = it.getBooleanExtra("IS_FAMILY_MODE", false)
                    val gardenId = it.getStringExtra("TARGET_GARDEN_ID")

                    if (isFamily && gardenId != null) {
                        val targetGarden = Garden(id = gardenId)
                        gardenViewModel.setGardenMode(targetGarden)
                    } else {
                        gardenViewModel.setGardenMode(null)
                    }
                }

                // Xóa các extra sau khi đã xử lý để tránh nhảy tab khi xoay màn hình
                it.removeExtra("OPEN_GARDEN")
                it.removeExtra("OPEN_MY_GARDEN")
                it.removeExtra("NAVIGATE_TO_GARDEN")
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Đã có quyền
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Garden Notifications"
            val descriptionText = "Thông báo nhắc nhở chăm sóc cây"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channelId = "leafy_garden_channel"

            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}