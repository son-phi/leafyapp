package com.example.leafyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.leafyapp.databinding.ActivityMainBinding
import androidx.core.view.isVisible

import android.view.View
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.navOptions
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_garden,
                R.id.navigation_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.navView.isVisible = dest.id != R.id.navigation_camera

            if (dest.id != R.id.navigation_camera) {
                binding.navView.menu.findItem(dest.id)?.isChecked = true
            }
        }

        // Intercept click menu: Camera là modal → KHÔNG popUpTo
        binding.navView.setOnItemSelectedListener { item ->
            return@setOnItemSelectedListener when (item.itemId) {
                R.id.navigation_camera -> {
                    navController.navigate(
                        R.id.navigation_camera,
                        null,
                        navOptions {
                            launchSingleTop = true
                            // Không popUpTo => giữ fragment hiện tại trong back stack
                        }
                    )
                    true
                }

                else -> {
                    // Các tab còn lại dùng handler chuẩn
                    NavigationUI.onNavDestinationSelected(item, navController)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

}
