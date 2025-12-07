package com.example.leafyapp.ui.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.databinding.ActivityLightMeterBinding

class LightMeterActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityLightMeterBinding
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLightMeterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ẩn Action Bar nếu có
        supportActionBar?.hide()

        // 1. Khởi tạo Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Kiểm tra xem máy có cảm biến không
        if (lightSensor == null) {
            Toast.makeText(this, "Thiết bị này không có cảm biến ánh sáng!", Toast.LENGTH_LONG).show()
            binding.tvStatusTitle.text = "Không hỗ trợ"
            binding.tvStatusDesc.text = "Điện thoại của bạn không có phần cứng để đo ánh sáng."
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Đăng ký lắng nghe khi App chạy
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Hủy đăng ký khi App ẩn (để tiết kiệm pin)
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val luxValue = event.values[0]
            updateUI(luxValue)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Không cần xử lý
    }

    private fun updateUI(lux: Float) {
        // Cập nhật số hiển thị
        binding.tvLuxValue.text = lux.toInt().toString()

        // Cập nhật thanh Progress (Max 50000 lux cho ánh nắng gắt)
        // Dùng animation mượt mà cho progress
        binding.progressLight.setProgressCompat(lux.toInt(), true)

        // Đánh giá mức độ sáng
        val (title, desc, color) = when {
            lux < 50 -> Triple(
                "Rất tối",
                "Quá tối cho hầu hết các loại cây. Chỉ phù hợp nấm hoặc cây ngủ đông.",
                "#757575" // Xám
            )
            lux in 50.0..500.0 -> Triple(
                "Ánh sáng yếu",
                "Phù hợp cây chịu bóng: Lưỡi Hổ, Kim Tiền, Trầu Bà.",
                "#8BC34A" // Xanh nhạt
            )
            lux in 500.0..2000.0 -> Triple(
                "Ánh sáng trung bình",
                "Tốt cho cây trong nhà: Lan Ý, Dương Xỉ, Thu Hải Đường.",
                "#4CAF50" // Xanh chuẩn
            )
            lux in 2000.0..10000.0 -> Triple(
                "Ánh sáng mạnh",
                "Rất tốt! Phù hợp hầu hết cây cảnh, cây có hoa, cây ăn quả nhỏ.",
                "#FFB300" // Vàng cam
            )
            else -> Triple(
                "Nắng trực tiếp",
                "Cực sáng! Tốt cho Xương Rồng, Sen Đá, Cây ăn quả lớn.",
                "#FF6F00" // Cam đậm
            )
        }

        binding.tvStatusTitle.text = title
        binding.tvStatusTitle.setTextColor(android.graphics.Color.parseColor(color))
        binding.tvStatusDesc.text = desc

        // Đổi màu icon mặt trời và thanh progress theo mức độ
        binding.ivSunIcon.setColorFilter(android.graphics.Color.parseColor(color))
        binding.progressLight.setIndicatorColor(android.graphics.Color.parseColor(color))
    }
}