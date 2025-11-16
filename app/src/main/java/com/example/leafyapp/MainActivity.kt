package com.example.leafyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.navOptions
import com.example.leafyapp.databinding.ActivityMainBinding

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
    }
}
