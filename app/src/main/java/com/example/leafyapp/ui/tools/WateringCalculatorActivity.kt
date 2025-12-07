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
    private var isCm = true
    private var isCelsius = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWateringCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupSliders()
        setupSelections()
        setupUnitToggle()
        setupTempToggle()
    }

    private fun setupTempToggle() {
        binding.toggleUnitTemp.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    com.example.leafyapp.R.id.btn_c -> {
                        if (!isCelsius) { // Đang là °F -> Chuyển sang °C
                            isCelsius = true

                            // 1. Lấy giá trị hiện tại (°F)
                            val currentF = binding.sliderTemp.value

                            // 2. Tính toán sang °C: (F - 32) / 1.8
                            val newC = (currentF - 32) / 1.8f

                            // 3. Cập nhật Slider (Range độ C: 0 -> 50)
                            binding.sliderTemp.valueFrom = 0f
                            binding.sliderTemp.valueTo = 50f
                            binding.sliderTemp.value = newC.coerceIn(0f, 50f)

                            // 4. Cập nhật Text
                            binding.tvTempValue.text = "${newC.toInt()}°C"
                        }
                    }
                    com.example.leafyapp.R.id.btn_f -> {
                        if (isCelsius) { // Đang là °C -> Chuyển sang °F
                            isCelsius = false

                            // 1. Lấy giá trị hiện tại (°C)
                            val currentC = binding.sliderTemp.value

                            // 2. Tính toán sang °F: (C * 1.8) + 32
                            val newF = (currentC * 1.8f) + 32

                            // 3. Cập nhật Slider (Range độ F: 32 -> 122)
                            // 0°C = 32°F, 50°C = 122°F
                            binding.sliderTemp.valueFrom = 32f
                            binding.sliderTemp.valueTo = 122f
                            binding.sliderTemp.value = newF.coerceIn(32f, 122f)

                            // 4. Cập nhật Text
                            binding.tvTempValue.text = "${newF.toInt()}°F"
                        }
                    }
                }
            }
        }
    }
    private fun setupUnitToggle() {
        binding.toggleUnitSize.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    com.example.leafyapp.R.id.btn_cm -> {
                        if (!isCm) { // Đang là IN, chuyển sang CM
                            isCm = true
                            val currentVal = binding.sliderSize.value
                            val newVal = currentVal * 2.54f

                            // Cập nhật Slider
                            binding.sliderSize.valueFrom = 0f
                            binding.sliderSize.valueTo = 100f
                            binding.sliderSize.value = newVal.coerceIn(0f, 100f)

                            binding.tvSizeValue.text = "${newVal.toInt()}cm"
                        }
                    }
                    com.example.leafyapp.R.id.btn_in -> {
                        if (isCm) { // Đang là CM, chuyển sang IN
                            isCm = false
                            val currentVal = binding.sliderSize.value
                            val newVal = currentVal / 2.54f

                            // Cập nhật Slider (Max của Inch nhỏ hơn)
                            binding.sliderSize.valueFrom = 0f
                            binding.sliderSize.valueTo = 40f
                            binding.sliderSize.value = newVal.coerceIn(0f, 40f)

                            binding.tvSizeValue.text = "${newVal.toInt()}in"
                        }
                    }
                }
            }
        }
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

            // KIỂM TRA ĐƠN VỊ ĐỂ HIỂN THỊ ĐÚNG
            val unitText = if (isCm) "cm" else "in"
            binding.tvSizeValue.text = "$potSize$unitText"
        }

        // Slider Temp
        binding.sliderTemp.addOnChangeListener { _, value, _ ->
            temp = value.toInt()

            // KIỂM TRA ĐƠN VỊ NHIỆT ĐỘ
            val unitText = if (isCelsius) "°C" else "°F"
            binding.tvTempValue.text = "$temp$unitText"
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

        // 1. Nhiệt độ: Luôn quy đổi về độ C để tính toán
        // Nếu đang là độ F thì đổi về C, nếu đang là C thì giữ nguyên
        val tempInCelsius = if (isCelsius) temp.toDouble() else (temp - 32) / 1.8

        // 2. Kích thước chậu: Luôn quy đổi về CM để tính toán
        // Nếu đang là Inch thì đổi về Cm
        val sizeInCm = if (isCm) potSize.toDouble() else potSize * 2.54

        // --- CÔNG THỨC TÍNH TOÁN ---

        // Dùng sizeInCm thay vì potSize
        var water = sizeInCm * 20.0

        // ...

        // Dùng tempInCelsius thay vì temp
        val tempFactor = 1.0 + (tempInCelsius - 20) * 0.02
        water *= tempFactor
        // 2. Hệ số vị trí
        if (!isIndoor) water *= 1.5 // Ngoài trời cần nhiều nước hơn


        // 4. Hệ số độ ẩm (Khô hơn = Tăng nước)
        // Lấy mốc 50%. Cứ giảm 1% độ ẩm thì thêm 1% nước
        val humidityFactor = 1.0 + (50 - humidity) * 0.01
        water *= humidityFactor

        // Hiển thị kết quả
        binding.tvFinalAmount.text = "${water.toInt()}ml"
    }
}