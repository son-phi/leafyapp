package com.example.leafyapp.ui.tools

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.databinding.ActivityWateringCalculatorBinding

class WateringCalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWateringCalculatorBinding

    // Các biến lưu giá trị
    private var isIndoor = true
    private var potSize = 28
    private var temp = 25
    private var humidity = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWateringCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupSliders()
        setupSelections()
    }

    private fun setupNavigation() {
        binding.btnClose.setOnClickListener { finish() }

        // --- XỬ LÝ NÚT BACK (Trượt ngược lại) ---
        binding.btnBack.setOnClickListener {
            if (binding.viewFlipper.displayedChild > 0) {
                // Set Animation: Vào từ Trái -> Ra bên Phải
                binding.viewFlipper.setInAnimation(this, com.example.leafyapp.R.anim.slide_in_left)
                binding.viewFlipper.setOutAnimation(this, com.example.leafyapp.R.anim.slide_out_right)

                binding.viewFlipper.showPrevious()
                updateBackButton()
            }
        }

        // --- XỬ LÝ NÚT NEXT (Trượt tới) ---
        val nextListener = View.OnClickListener {
            // Set Animation: Vào từ Phải -> Ra bên Trái
            binding.viewFlipper.setInAnimation(this, com.example.leafyapp.R.anim.slide_in_right)
            binding.viewFlipper.setOutAnimation(this, com.example.leafyapp.R.anim.slide_out_left)

            binding.viewFlipper.showNext()
            updateBackButton()
        }

        binding.btnStart.setOnClickListener(nextListener)
        binding.btnNext1.setOnClickListener(nextListener)
        binding.btnNext2.setOnClickListener(nextListener)
        binding.btnNext3.setOnClickListener(nextListener)

        // Nút tính toán cuối cùng (Cũng trượt tới)
        binding.btnCalculate.setOnClickListener {
            calculateWater()

            // Set Animation Next
            binding.viewFlipper.setInAnimation(this, com.example.leafyapp.R.anim.slide_in_right)
            binding.viewFlipper.setOutAnimation(this, com.example.leafyapp.R.anim.slide_out_left)

            binding.viewFlipper.showNext()
            updateBackButton()
        }

        binding.btnFinish.setOnClickListener { finish() }
    }

    private fun updateBackButton() {
        // Chỉ hiện nút Back từ màn hình thứ 2 trở đi (index > 0)
        binding.btnBack.visibility = if (binding.viewFlipper.displayedChild > 0) View.VISIBLE else View.GONE
    }

    private fun setupSliders() {
        // Slider Size
        binding.sliderSize.addOnChangeListener { _, value, _ ->
            potSize = value.toInt()
            binding.tvSizeValue.text = "${potSize}cm"
        }

        // Slider Temp
        binding.sliderTemp.addOnChangeListener { _, value, _ ->
            temp = value.toInt()
            binding.tvTempValue.text = "${temp}°C"
        }

        // Slider Humidity
        binding.sliderHumidity.addOnChangeListener { _, value, _ ->
            humidity = value.toInt()
            binding.tvHumidityValue.text = "${humidity}%"
        }
    }

    private fun setupSelections() {
        // Mặc định ban đầu: Indoor được chọn
        updateSelectionUI(true)

        binding.cardIndoor.setOnClickListener {
            isIndoor = true
            updateSelectionUI(true)
        }

        binding.cardOutdoor.setOnClickListener {
            isIndoor = false
            updateSelectionUI(false)
        }
    }


    private fun updateSelectionUI(isIndoorSelected: Boolean) {
        // Màu sắc
        val colorBlue = android.graphics.Color.parseColor("#2979FF")
        val colorWhite = android.graphics.Color.WHITE
        val colorBlack = android.graphics.Color.parseColor("#1A1C1E")
        val colorGray = android.graphics.Color.parseColor("#757575")
        val colorLightGray = android.graphics.Color.parseColor("#E0E0E0") // Màu chữ phụ trên nền xanh

        // 1. Cập nhật INDOOR Card
        if (isIndoorSelected) {
            // Trạng thái ĐƯỢC CHỌN (Nền Xanh - Chữ Trắng)
            binding.cardIndoor.setCardBackgroundColor(colorBlue)
            binding.cardIndoor.strokeWidth = 0
            binding.cardIndoor.cardElevation = 8f // Tăng độ nổi

            binding.ivIndoor.setColorFilter(colorWhite)
            binding.tvTitleIndoor.setTextColor(colorWhite)
            binding.tvDescIndoor.setTextColor(colorLightGray)
        } else {
            // Trạng thái KHÔNG CHỌN (Nền Trắng - Chữ Đen)
            binding.cardIndoor.setCardBackgroundColor(colorWhite)
            binding.cardIndoor.strokeWidth = 2 // Thêm viền
            binding.cardIndoor.strokeColor = colorLightGray
            binding.cardIndoor.cardElevation = 0f

            binding.ivIndoor.setColorFilter(colorBlack)
            binding.tvTitleIndoor.setTextColor(colorBlack)
            binding.tvDescIndoor.setTextColor(colorGray)
        }

        // 2. Cập nhật OUTDOOR Card (Ngược lại)
        if (!isIndoorSelected) {
            // Outdoor ĐƯỢC CHỌN
            binding.cardOutdoor.setCardBackgroundColor(colorBlue)
            binding.cardOutdoor.strokeWidth = 0
            binding.cardOutdoor.cardElevation = 8f

            binding.ivOutdoor.setColorFilter(colorWhite)
            binding.tvTitleOutdoor.setTextColor(colorWhite)
            binding.tvDescOutdoor.setTextColor(colorLightGray)
        } else {
            // Outdoor KHÔNG CHỌN
            binding.cardOutdoor.setCardBackgroundColor(colorWhite)
            binding.cardOutdoor.strokeWidth = 2
            binding.cardOutdoor.strokeColor = colorLightGray
            binding.cardOutdoor.cardElevation = 0f

            binding.ivOutdoor.setColorFilter(colorBlack)
            binding.tvTitleOutdoor.setTextColor(colorBlack)
            binding.tvDescOutdoor.setTextColor(colorGray)
        }
    }

    private fun calculateWater() {
        // --- CÔNG THỨC TÍNH TOÁN GIẢ LẬP ---
        // 1. Lượng nước cơ bản theo kích thước chậu (Ví dụ: 1cm = 20ml)
        var water = potSize * 20.0

        // 2. Hệ số vị trí
        if (!isIndoor) water *= 1.5 // Ngoài trời cần nhiều nước hơn

        // 3. Hệ số nhiệt độ (Nóng hơn = Tăng nước)
        // Lấy mốc 20 độ. Cứ tăng 1 độ thì thêm 2% nước
        val tempFactor = 1.0 + (temp - 20) * 0.02
        water *= tempFactor

        // 4. Hệ số độ ẩm (Khô hơn = Tăng nước)
        // Lấy mốc 50%. Cứ giảm 1% độ ẩm thì thêm 1% nước
        val humidityFactor = 1.0 + (50 - humidity) * 0.01
        water *= humidityFactor

        // Hiển thị kết quả
        binding.tvFinalAmount.text = "${water.toInt()}ml"
    }
}