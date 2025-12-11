package com.example.leafyapp.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.leafyapp.databinding.FragmentHomeBinding
import com.example.leafyapp.ui.information.ResultActivity
import com.example.leafyapp.ui.search.SearchActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sử dụng viewModel này cho toàn bộ class (Không cần tạo lại biến vm ở onCreateView nữa)
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var trendingAdapter: TrendingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Bắt đầu lấy vị trí
        getCurrentLocation()

        // Quan sát location LiveData
        viewModel.location.observe(viewLifecycleOwner) { binding.sectionHeader.tvLocation.text = it }

        // Clicks cho 4 tool
        binding.sectionTools.cardToolPlant.setOnClickListener {
            val args = bundleOf("CAMERA_MODE" to "Plant")
            findNavController().navigate(com.example.leafyapp.R.id.navigation_camera, args)
        }
        binding.sectionTools.cardToolDisease.setOnClickListener {
            val args = bundleOf("CAMERA_MODE" to "Disease")
            findNavController().navigate(com.example.leafyapp.R.id.navigation_camera, args)
        }
        binding.sectionTools.cardToolLight.setOnClickListener {
            val intent = Intent(requireContext(), com.example.leafyapp.ui.tools.LightMeterActivity::class.java)
            startActivity(intent)
        }
        binding.sectionTools.cardToolWater.setOnClickListener {
            val intent = Intent(requireContext(), com.example.leafyapp.ui.tools.WateringCalculatorActivity::class.java)
            startActivity(intent)
        }

        // Observer Weather cho Header
        viewModel.weatherData.observe(viewLifecycleOwner) { weather ->
            binding.sectionHeader.tvTemp.text = "${weather.main.temp}°C"
        }

        binding.cardSearch.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Setup RecyclerView Trending
        setupTrendingRecyclerView()

        // 3. Quan sát dữ liệu
        setupObservers()

        // Gọi hàm tải dữ liệu Trending
        viewModel.fetchTrendingPlants()

        setupUserGreeting()
    }

    private fun setupUserGreeting() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val greetingTextView = binding.sectionHeader.tvTitle

        if (user != null && !user.isAnonymous && !user.displayName.isNullOrEmpty()) {
            val firstName = user.displayName?.split(" ")?.lastOrNull() ?: user.displayName
            greetingTextView.text = "Hello, $firstName! \uD83D\uDC4B"
        } else {
            greetingTextView.text = "Hello, Gardener!"
        }
    }

    private fun setupTrendingRecyclerView() {
        // Khởi tạo Adapter và xử lý sự kiện Click vào 1 cây
        trendingAdapter = TrendingAdapter { plant ->
            val intent = Intent(requireContext(), ResultActivity::class.java).apply {
                // [QUAN TRỌNG 1] Đổi Key "RESULT_ID" thành "ID" cho khớp với ResultActivity
                putExtra("ID", plant.id)

                putExtra("RESULT_LABEL", plant.name)
                putExtra("RESULT_MODE", "Plant")

                // [QUAN TRỌNG 2] Thêm cờ này để ResultActivity biết KHÔNG ĐƯỢC CỘNG 1
                putExtra("IS_FROM_DB", true)
            }
            startActivity(intent)
        }

        binding.sectionTrending.rvTrending.apply {
            adapter = trendingAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
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
            binding.sectionHeader.tvTemp.text = "${weather.main.temp.toInt()}°C"
            val iconCode = weather.weather.firstOrNull()?.icon
            if (!iconCode.isNullOrEmpty()) {
                val iconUrl = "https://openweathermap.org/img/wn/$iconCode@2x.png"
                Glide.with(this)
                    .load(iconUrl)
                    .error(com.example.leafyapp.R.drawable.cloud_solid_full)
                    .into(binding.sectionHeader.ivWeather)
            }
        }

        // Observer Plants (Trending)
        viewModel.trendingPlants.observe(viewLifecycleOwner) { list ->
            trendingAdapter.submitList(list)
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val lat = it.latitude
                    val lon = it.longitude
                    val cityName = getAddressFromLocation(lat, lon)
                    viewModel.setLocation(cityName)
                    // Lưu ý: API Key này nên đưa vào file local.properties hoặc BuildConfig để bảo mật
                    viewModel.fetchWeather(cityName, "0914df7fe34c620e59216869738dddc0")
                } ?: run {
                    viewModel.setLocation("Không xác định được vị trí")
                }
            }
    }

    private fun getAddressFromLocation(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                // Ưu tiên lấy Quận/Huyện -> Tỉnh/Thành -> Quốc gia
                addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Không xác định"
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
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Cần quyền vị trí để lấy thời tiết", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}