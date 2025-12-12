package com.example.leafyapp.ui.information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // 1. Nhận dữ liệu (Hỗ trợ cả Key cũ "RESULT_ID" và Key mới "ID")
        var rawId = intent.getIntExtra("ID", -1)
        if (rawId == -1) {
            rawId = intent.getIntExtra("RESULT_ID", -1)
        }

        val label = intent.getStringExtra("RESULT_LABEL") ?: "Unknown"
        val confidence = intent.getFloatExtra("RESULT_CONF", 0f)
        val mode = intent.getStringExtra("RESULT_MODE") ?: "Plant"

        val isFromDb = intent.getBooleanExtra("IS_FROM_DB", false)

        // [THÊM] Ngưỡng confidence < 50% thì đi NoInfo (chỉ áp dụng cho AI)
        val THRESHOLD = 0.5f
        val shouldShowNoInfo = (!isFromDb) && (confidence < THRESHOLD)

        // 2. Xử lý ID cuối cùng (GIỮ NGUYÊN LOGIC CŨ)
        val finalId = if (rawId != -1) {
            if (mode == "Plant") {
                if (isFromDb) rawId else rawId + 1
            } else {
                rawId
            }
        } else {
            -1
        }

        // 3. Chọn Fragment phù hợp (GIỮ NGUYÊN + THÊM NoInfo)
        val fragment = if (shouldShowNoInfo) {
            // GỌI ĐÚNG SIGNATURE bạn đang có: (label, confidence, mode)
            NoInfoFragment.newInstance(label, confidence, mode)
        } else {
            if (mode == "Plant") {
                PlantFragment.newInstance(finalId, label, confidence)
            } else {
                DiseaseFragment.newInstance(finalId, label, confidence)
            }
        }

        // 4. Load Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.resultContainer, fragment)
                .commit()
        }
    }
}
