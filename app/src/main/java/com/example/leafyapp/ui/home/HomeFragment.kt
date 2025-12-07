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
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.fragment.app.viewModels
import com.example.leafyapp.ui.search.SearchActivity
import java.util.*
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.ui.information.ResultActivity
import com.bumptech.glide.Glide


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val viewModel: HomeViewModel by viewModels()

    // Khai báo Adapter
    private lateinit var trendingAdapter: TrendingAdapter

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
        vm.location.observe(viewLifecycleOwner) { binding.sectionHeader.tvLocation.text = it }


        // Clicks cho 4 tool
        binding.sectionTools.cardToolPlant.setOnClickListener {
            // Tạo gói dữ liệu: Key là "CAMERA_MODE", Value là "Plant"
            val args = bundleOf("CAMERA_MODE" to "Plant")

            // TODO: điều hướng tới màn nhận dạng cây
            findNavController().navigate(com.example.leafyapp.R.id.navigation_camera, args)

        }
        binding.sectionTools.cardToolDisease.setOnClickListener {
            val args = bundleOf("CAMERA_MODE" to "Disease")
            findNavController().navigate(com.example.leafyapp.R.id.navigation_camera, args)
        }
        binding.sectionTools.cardToolLight.setOnClickListener {
            // Mở màn hình đo sáng
            val intent = Intent(requireContext(), com.example.leafyapp.ui.tools.LightMeterActivity::class.java)
            startActivity(intent)
        }
        binding.sectionTools.cardToolWater.setOnClickListener {
            val intent = Intent(requireContext(), com.example.leafyapp.ui.tools.WateringCalculatorActivity::class.java)
            startActivity(intent)
        }


//        binding.tvTemp.text = "30°C"
//        binding.tvTempRange.text = "(30°C · 30°C)"
        viewModel.weatherData.observe(viewLifecycleOwner) { weather ->
            binding.sectionHeader.tvTemp.text = "${weather.main.temp}°C"
//            binding.tvWeather.text = weather.weather[0].main
        }

        binding.cardSearch.isClickable = true
        binding.cardSearch.isFocusable = true


        binding.cardSearch.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // 2. Setup RecyclerView Trending (MỚI THÊM)
        setupTrendingRecyclerView()

        // 3. Quan sát dữ liệu (Location, Weather, Plants)
        setupObservers()
    }

    private fun setupTrendingRecyclerView() {
        // Khởi tạo Adapter và xử lý sự kiện Click vào 1 cây
        trendingAdapter = TrendingAdapter { plant ->
            // Mở màn hình chi tiết khi click vào thẻ Trending
            val intent = Intent(requireContext(), ResultActivity::class.java).apply {
                putExtra("RESULT_ID", plant.id)
                putExtra("RESULT_LABEL", plant.name)
                putExtra("RESULT_MODE", "Plant")
            }
            startActivity(intent)
        }

        // Cấu hình RecyclerView trong file layout
        // binding.sectionTrending là ID của thẻ <include>
        // rvTrending là ID của RecyclerView bên trong file layout_home_trending.xml
        binding.sectionTrending.rvTrending.apply {
            adapter = trendingAdapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        // Observer Location
        viewModel.location.observe(viewLifecycleOwner) {
            binding.sectionHeader.tvLocation.text = it
        }

        // Observer Weather
        viewModel.weatherData.observe(viewLifecycleOwner) { weather ->
            // 1. Hiển thị nhiệt độ
            binding.sectionHeader.tvTemp.text = "${weather.main.temp.toInt()}°C"

            // 2. Lấy mã icon (ví dụ: "10d")
            val iconCode = weather.weather.firstOrNull()?.icon

            if (!iconCode.isNullOrEmpty()) {
                // 3. Tạo URL ảnh icon chuẩn của OpenWeatherMap
                // @2x để lấy ảnh sắc nét hơn
                val iconUrl = "https://openweathermap.org/img/wn/$iconCode@2x.png"

                // 4. Dùng Glide tải ảnh vào ImageView (iv_weather)
                Glide.with(this)
                    .load(iconUrl)
                    .error(com.example.leafyapp.R.drawable.cloud_solid_full)       // Ảnh nếu lỗi mạng
                    .into(binding.sectionHeader.ivWeather)
            }
        }

        // Observer Plants (Dữ liệu cây lấy từ Firebase) -> Cập nhật vào Trending
        viewModel.plants.observe(viewLifecycleOwner) { list ->
            // Giả lập "Trending" bằng cách lấy 5 cây đầu tiên trong danh sách
            // Hoặc bạn có thể dùng list.shuffled().take(5) để lấy ngẫu nhiên
            val trendingList = list.take(5)
            trendingAdapter.submitList(trendingList)
        }
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
