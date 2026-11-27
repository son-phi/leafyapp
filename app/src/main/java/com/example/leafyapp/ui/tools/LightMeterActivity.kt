package com.example.leafyapp.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.leafyapp.data.model.Plant
import com.example.leafyapp.databinding.ActivityLightMeterBinding
import com.example.leafyapp.ui.home.TrendingAdapter
import com.example.leafyapp.ui.information.ResultActivity
import com.google.firebase.firestore.FirebaseFirestore

class LightMeterActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityLightMeterBinding
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    // Biến lưu giá trị ánh sáng hiện tại
    private var currentLux: Float = 0f

    // Adapter hiển thị kết quả gợi ý
    private lateinit var plantsAdapter: TrendingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLightMeterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ẩn Action Bar cho đẹp
        supportActionBar?.hide()

        // 1. Cấu hình Cảm biến (Sensor)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            Toast.makeText(this, "Thiết bị này không có cảm biến ánh sáng!", Toast.LENGTH_LONG).show()
            binding.tvStatusTitle.text = "Không hỗ trợ"
            binding.btnSuggest.isEnabled = false
        }

        // 2. Cấu hình danh sách hiển thị (RecyclerView)
        setupRecyclerView()

        // 3. Sự kiện Click nút "Gợi ý cây trồng"
        binding.btnSuggest.setOnClickListener {
            // Hiển thị loading, ẩn kết quả cũ
            binding.layoutSuggestion.visibility = View.VISIBLE
            binding.rvSuggestedPlants.visibility = View.GONE
            binding.tvNoResult.visibility = View.GONE
            binding.progressLoading.visibility = View.VISIBLE

            // Gọi hàm tìm kiếm trên Firestore
            fetchPlantsByLight(currentLux)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        // Khởi tạo Adapter với sự kiện Click vào cây
        plantsAdapter = TrendingAdapter { plant ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                // [QUAN TRỌNG] Truyền ID số (Int) lấy từ Firestore
                // Yêu cầu: Trong Firestore, mỗi cây phải có trường "id" là số (ví dụ: 1, 2, 5...)
                putExtra("RESULT_ID", plant.id)

                // Truyền tên để hiển thị tiêu đề
                putExtra("RESULT_LABEL", plant.name)

                // Chế độ xem cây
                putExtra("RESULT_MODE", "Plant")

                // [CỜ QUAN TRỌNG] Báo cho ResultActivity biết đây là dữ liệu lấy từ DB
                // Để nó bỏ qua bước kiểm tra độ tin cậy (Confidence Check)
                putExtra("IS_FROM_DB", true)
            }
            startActivity(intent)
        }

        binding.rvSuggestedPlants.apply {
            layoutManager = LinearLayoutManager(this@LightMeterActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = plantsAdapter
        }
    }

    // --- CÁC HÀM CỦA SENSOR ---
    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            currentLux = event.values[0]
            updateUI(currentLux)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    // --- CẬP NHẬT GIAO DIỆN ĐỒNG HỒ ĐO ---
    private fun updateUI(lux: Float) {
        binding.tvLuxValue.text = lux.toInt().toString()
        binding.progressLight.setProgressCompat(lux.toInt(), true)

        // Logic phân loại ánh sáng và màu sắc
        val (title, desc, color) = when {
            lux < 500 -> Triple(
                "Ánh sáng yếu",
                "Chỉ phù hợp cây chịu bóng cực tốt (Lưỡi Hổ, Kim Tiền).",
                "#757575" // Xám
            )
            lux in 500.0..1500.0 -> Triple(
                "Ánh sáng gián tiếp",
                "Ánh sáng nhẹ, mát mẻ. Tốt cho Lan Ý, Vạn Niên Thanh.",
                "#8BC34A" // Xanh lá mạ
            )
            lux in 1500.0..4000.0 -> Triple(
                "Ánh sáng tán xạ",
                "Khu vực sáng sủa, gần cửa sổ. Tốt cho Monstera, Trầu Bà.",
                "#4CAF50" // Xanh lá chuẩn
            )
            lux in 4000.0..20000.0 -> Triple(
                "Ánh sáng mạnh",
                "Nắng sáng rực rỡ. Tốt cho cây có hoa, cây ăn quả nhỏ.",
                "#FFB300" // Vàng cam
            )
            else -> Triple(
                "Nắng trực tiếp",
                "Cực gắt! Chỉ dành cho Sen Đá, Xương Rồng, Cây ngoài trời.",
                "#FF6F00" // Cam đậm
            )
        }

        binding.tvStatusTitle.text = title
        binding.tvStatusTitle.setTextColor(Color.parseColor(color))
        binding.tvStatusDesc.text = desc
        binding.ivSunIcon.setColorFilter(Color.parseColor(color))
        binding.progressLight.setIndicatorColor(Color.parseColor(color))
    }

    // --- LOGIC TÌM CÂY TRÊN FIRESTORE ---
    /** Gợi ý cây hợp với mức sáng đo được: lux nằm trong ngưỡng ánh sáng của loài (`species.threshold`). */
    private fun fetchPlantsByLight(lux: Float) {
        lifecycleScope.launch {
            val list = try {
                val repo = com.example.leafyapp.data.repository.SpeciesRepository
                repo.getAll().filter { lux >= it.threshold.lightMin && lux <= it.threshold.lightMax }
                    .take(10).map { repo.asPlant(it) }
            } catch (e: Exception) { emptyList() }
            binding.progressLoading.visibility = View.GONE
            if (list.isEmpty()) {
                binding.tvNoResult.visibility = View.VISIBLE
                binding.rvSuggestedPlants.visibility = View.GONE
            } else {
                binding.tvNoResult.visibility = View.GONE
                binding.rvSuggestedPlants.visibility = View.VISIBLE
                plantsAdapter.submitList(list)
            }
        }
    }
}