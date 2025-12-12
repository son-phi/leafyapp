package com.example.leafyapp.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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

    // Biến lưu giá trị hiện tại
    private var currentLux: Float = 0f

    // Adapter hiển thị kết quả
    private lateinit var plantsAdapter: TrendingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLightMeterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // 1. Setup Sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            Toast.makeText(this, "Thiết bị này không có cảm biến ánh sáng!", Toast.LENGTH_LONG).show()
            binding.tvStatusTitle.text = "Không hỗ trợ"
            binding.btnSuggest.isEnabled = false // Khóa nút nếu không đo được
        }

        // 2. Setup RecyclerView (Dùng lại TrendingAdapter)
        setupRecyclerView()

        // 3. Sự kiện Click Nút Gợi Ý
        binding.btnSuggest.setOnClickListener {
            // Hiển thị khu vực kết quả
            binding.layoutSuggestion.visibility = View.VISIBLE
            binding.rvSuggestedPlants.visibility = View.GONE
            binding.tvNoResult.visibility = View.GONE
            binding.progressLoading.visibility = View.VISIBLE

            // Gọi hàm tìm kiếm
            fetchPlantsByLight(currentLux)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        plantsAdapter = TrendingAdapter { plant ->
            // Click vào cây thì mở chi tiết
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("RESULT_ID", plant.id)
                putExtra("RESULT_LABEL", plant.name)
                putExtra("RESULT_MODE", "Plant")
            }
            startActivity(intent)
        }

        binding.rvSuggestedPlants.apply {
            layoutManager = LinearLayoutManager(this@LightMeterActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = plantsAdapter
        }
    }

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

    private fun updateUI(lux: Float) {
        binding.tvLuxValue.text = lux.toInt().toString()
        binding.progressLight.setProgressCompat(lux.toInt(), true)

        val (title, desc, color) = when {
            lux < 500 -> Triple(
                "Ánh sáng yếu",
                "Chỉ phù hợp cây chịu bóng cực tốt (Lưỡi Hổ, Kim Tiền).",
                "#757575" // Xám
            )
            lux in 500.0..1500.0 -> Triple(
                "Ánh sáng gián tiếp", // MỚI
                "Ánh sáng nhẹ, mát mẻ. Tốt cho Lan Ý, Vạn Niên Thanh.",
                "#8BC34A" // Xanh lá mạ (Light Green)
            )
            lux in 1500.0..4000.0 -> Triple(
                "Ánh sáng tán xạ",
                "Khu vực sáng sủa, gần cửa sổ. Tốt cho Monstera, Trầu Bà.",
                "#4CAF50" // Xanh lá chuẩn (Green)
            )
            lux in 4000.0..20000.0 -> Triple(
                "Ánh sáng mạnh",
                "Nắng sáng rực rỡ. Tốt cho cây có hoa, cây ăn quả nhỏ.",
                "#FFB300" // Vàng cam (Amber)
            )
            else -> Triple(
                "Nắng trực tiếp",
                "Cực gắt! Chỉ dành cho Sen Đá, Xương Rồng, Cây ngoài trời.",
                "#FF6F00" // Cam đậm (Deep Orange)
            )
        }

        binding.tvStatusTitle.text = title
        binding.tvStatusTitle.setTextColor(Color.parseColor(color))
        binding.tvStatusDesc.text = desc
        binding.ivSunIcon.setColorFilter(Color.parseColor(color))
        binding.progressLight.setIndicatorColor(Color.parseColor(color))
    }

    // --- LOGIC GỢI Ý CÂY ---
    private fun fetchPlantsByLight(lux: Float) {
        val db = FirebaseFirestore.getInstance()

        // Xác định từ khóa dựa trên Lux đo được (Logic Mapping mới)
        val targetLightCategories = when {
            // Nhóm Yếu
            lux < 500 -> listOf(
                "Ánh sáng mạnh hoặc yếu đều được."
            )

            // Nhóm Gián tiếp (500 - 1500) -> Tìm các từ khóa "Gián tiếp"
            lux in 500.0..1500.0 -> listOf(
                "Ánh sáng gián tiếp.",
                "Ánh sáng gián tiếp, tránh nắng gắt.",
                "Ánh sáng mạnh hoặc yếu đều được."
            )

            // Nhóm Tán xạ (1500 - 4000) -> Tìm các từ khóa "Tán xạ"
            lux in 1500.0..4000.0 -> listOf(
                "Ánh sáng tán xạ.",
                "Ánh sáng tán xạ, tránh nắng gắt.",
                "Ánh sáng gián tiếp.",
                "Ánh sáng nhẹ, gần cửa sổ."
            )

            // Nhóm Mạnh & Trực tiếp (> 4000)
            else -> listOf(
                "Ánh sáng mạnh.",
                "Ánh sáng đầy đủ.",
                "Ánh sáng mạnh, gần cửa sổ.",
                "Ánh sáng mạnh hoặc yếu đều được.",
                "Ánh sáng mạnh, có thể trồng ngoài trời.",
                "Ánh sáng mạnh, 4h nắng/ngày."
            )
        }

        // 2. Query Firestore
        db.collection("plants")
            .whereIn("light", targetLightCategories)
            .limit(10) // Lấy tối đa 10 cây gợi ý
            .get()
            .addOnSuccessListener { documents ->
                val list = ArrayList<Plant>()
                for (doc in documents) {
                    try {
                        val plant = doc.toObject(Plant::class.java)
                        list.add(plant)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 3. Cập nhật UI
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
            .addOnFailureListener {
                binding.progressLoading.visibility = View.GONE
                Toast.makeText(this, "Lỗi tải dữ liệu: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}