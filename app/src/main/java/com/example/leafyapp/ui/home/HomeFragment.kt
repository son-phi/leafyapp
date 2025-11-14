package com.example.leafyapp.ui.home

import android.content.pm.PackageManager
import android.health.connect.datatypes.ExerciseRoute
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.leafyapp.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged

import android.Manifest
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.fragment.app.viewModels
import java.util.*


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vm = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Bắt đầu lấy vị trí
        getCurrentLocation(vm)
        // Quan sát location LiveData
        vm.location.observe(viewLifecycleOwner) { binding.tvLocation.text = it }


        // Clicks cho 4 tool
        binding.cardToolPlant.setOnClickListener {
            Toast.makeText(requireContext(), "Plant Identifier", Toast.LENGTH_SHORT).show()
            // TODO: điều hướng tới màn nhận dạng cây
        }
        binding.cardToolDisease.setOnClickListener {
            Toast.makeText(requireContext(), "Disease Identifier", Toast.LENGTH_SHORT).show()
        }
        binding.cardToolLight.setOnClickListener {
            Toast.makeText(requireContext(), "Light Meter", Toast.LENGTH_SHORT).show()
        }
        binding.cardToolWater.setOnClickListener {
            Toast.makeText(requireContext(), "Water Meter", Toast.LENGTH_SHORT).show()
        }


//        binding.tvTemp.text = "30°C"
//        binding.tvTempRange.text = "(30°C · 30°C)"
        viewModel.weatherData.observe(viewLifecycleOwner) { weather ->
            binding.tvTemp.text = "${weather.main.temp}°C"
//            binding.tvWeather.text = weather.weather[0].main
        }
        binding.etSearch.doAfterTextChanged {
            binding.btnClear.isVisible = !it.isNullOrEmpty()
        }
        binding.btnClear.setOnClickListener { binding.etSearch.text?.clear() }

        return binding.root
    }

    private fun getCurrentLocation(vm: HomeViewModel) {
        // Kiểm tra quyền
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Nếu chưa có quyền -> yêu cầu người dùng cấp
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            return
        }

        // Nếu có quyền -> lấy vị trí
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->   // 🔹 Dùng android.location.Location
                location?.let {
                    val lat = it.latitude
                    val lon = it.longitude
                    val cityName = getAddressFromLocation(lat, lon)
                    vm.setLocation(cityName)
                    viewModel.fetchWeather(cityName, "0914df7fe34c620e59216869738dddc0")
                } ?: run {
                    vm.setLocation("Không xác định được vị trí")
                }
            }
    }

    private fun getAddressFromLocation(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            // in ra terminal hoặc j đó để xem cụ thể  addresses

            // 🔹 In ra logcat để xem toàn bộ dữ liệu trả về
            Log.d("GeocoderDebug", "Kết quả từ geocoder: $addresses")

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                Log.d("GeocoderDebug", """
                Địa chỉ chi tiết:
                - countryName: ${addr.countryName}
                - adminArea: ${addr.adminArea}
                - subAdminArea: ${addr.subAdminArea}
                - locality: ${addr.locality}
                - subLocality: ${addr.subLocality}
                - thoroughfare: ${addr.thoroughfare}
                - featureName: ${addr.featureName}
                - postalCode: ${addr.postalCode}
            """.trimIndent())

                addr.locality ?: addr.adminArea ?: addr.subAdminArea ?: "Không xác định"
            } else {
                "Không xác định"
            }

            if (!addresses.isNullOrEmpty()) {
                addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: "Không xác định"
            } else {
                "Không xác định"
            }
        } catch (e: Exception) {
            "Không xác định"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            val vm = ViewModelProvider(this).get(HomeViewModel::class.java)
            getCurrentLocation(vm)
        } else {
            Toast.makeText(requireContext(), "Ứng dụng cần quyền vị trí để hiển thị địa điểm", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
