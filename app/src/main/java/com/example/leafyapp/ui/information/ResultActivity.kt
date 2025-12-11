package com.example.leafyapp.ui.information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // 1. Nhận dữ liệu từ Model gửi sang
        val modelId = intent.getIntExtra("RESULT_ID", -1)
        val label = intent.getStringExtra("RESULT_LABEL") ?: "Unknown"
        val confidence = intent.getFloatExtra("RESULT_CONF", 0f)
        val mode = intent.getStringExtra("RESULT_MODE") ?: "Plant"

        // 2. Xử lý ID để khớp với Database
        val finalId = if (modelId != -1) {
            if (mode == "Plant") {
                // CÂY: Model (0,1,2...) -> DB (1,2,3...) => Cần cộng 1
                modelId + 1
            } else {
                // BỆNH: Model (0,1,2...) -> DB (0,1,2...) => Giữ nguyên
                // (Dựa theo ảnh bảng 'diseases' em gửi, id bắt đầu từ 0)
                modelId
            }
        } else {
            -1
        }

        // 3. Chọn Fragment phù hợp để hiển thị
        val fragment = if (mode == "Plant") {
            // Truyền ID đã xử lý vào PlantFragment
            PlantFragment.newInstance(finalId, label, confidence)
        } else {
            // Truyền ID đã xử lý vào DiseaseFragment
            DiseaseFragment.newInstance(finalId, label, confidence)
        }

        // 4. Load Fragment lên màn hình
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.resultContainer, fragment)
                .commit()
        }
    }
}