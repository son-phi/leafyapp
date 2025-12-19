package com.example.leafyapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gardenViewModel: GardenViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Bạn cần cấp quyền để nhận thông báo chăm sóc cây!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseCopier.copyDatabase(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // 1. Khởi tạo kênh thông báo (Đảm bảo ID khớp 100% với NotificationHelper)
        createNotificationChannel()

        // 2. Setup Navigation
        setupNavigation()

        // 3. Xử lý điều hướng khi nhấn vào thông báo
        handleIntent(intent)

        // 4. Xin quyền thông báo (Android 13+)
        askNotificationPermission()

        // 5. [HOÀN THIỆN] Tự động đăng ký nhận tin cho tất cả các vườn
        subscribeToUserTopics()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Cập nhật intent mới để handleIntent lấy dữ liệu mới nhất
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val screen = it.getStringExtra("screen")
            val gardenId = it.getStringExtra("TARGET_GARDEN_ID")
            val isFamily = it.getBooleanExtra("IS_FAMILY_MODE", false)
            val openGarden = it.getBooleanExtra("OPEN_GARDEN", false) || screen != null

            if (openGarden) {
                // Chuyển sang Tab Garden
                binding.navView.selectedItemId = R.id.navigation_garden

                // Nếu là thông báo từ vườn, yêu cầu ViewModel tải đúng vườn đó
                if (!gardenId.isNullOrEmpty()) {
                    val targetGarden = Garden(id = gardenId)
                    gardenViewModel.setGardenMode(if (isFamily) targetGarden else null)
                    Log.d("FCM_NAV", "Navigating to Garden: $gardenId, Mode: $screen")
                }

                // Xóa dữ liệu Intent sau khi dùng để tránh nhảy tab khi xoay màn hình
                it.removeExtra("screen")
                it.removeExtra("TARGET_GARDEN_ID")
                it.removeExtra("OPEN_GARDEN")
            }
        }
    }

    /**
     * Tự động đăng ký (Subscribe) các kênh thông báo:
     * 1. Kênh cá nhân (user_UID) để nhận báo thức cho cây riêng từ Server.
     * 2. Toàn bộ các kênh gia đình (garden_ID) mà người dùng tham gia.
     */
    private fun subscribeToUserTopics() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Đăng ký kênh cá nhân
        FirebaseMessaging.getInstance().subscribeToTopic("user_$uid")
            .addOnCompleteListener { Log.d("FCM_LEAFY", "Subscribed to personal topic: user_$uid") }

        // 2. Quét Firestore tìm các vườn chung và đăng ký
        db.collection("gardens")
            .whereArrayContains("members", uid) // Khớp với cấu trúc mảng members của bạn
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val gardenId = doc.id
                    FirebaseMessaging.getInstance().subscribeToTopic("garden_$gardenId")
                        .addOnCompleteListener { Log.d("FCM_LEAFY", "Subscribed to garden: garden_$gardenId") }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FCM_LEAFY", "Error fetching gardens for subscription: ${e.message}")
            }
    }

    private fun setupNavigation() {
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
                    navController.navigate(R.id.navigation_camera, null, navOptions { launchSingleTop = true })
                    true
                }
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "leafy_garden_channel", // Khớp với NotificationHelper và Cloud Functions
                "Garden Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nhắc nhở chăm sóc cây"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}