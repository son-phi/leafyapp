package com.example.leafyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.navOptions
import com.example.leafyapp.databinding.ActivityMainBinding
// --- QUAN TRỌNG: PHẢI CÓ CÁC DÒNG IMPORT NÀY ---
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✔ COPY DB — KHÔNG TRUYỀN PATH, CHỈ COPY THEO TÊN FILE DUY NHẤT
        DatabaseCopier.copyDatabase(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

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

        // XIN QUYỀN THÔNG BÁO (Cho Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
