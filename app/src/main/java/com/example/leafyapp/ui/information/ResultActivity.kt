package com.example.leafyapp.ui.information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // Nhận dữ liệu kết quả từ LoadingActivity
        val id = intent.getIntExtra("RESULT_ID", -1)
        val label = intent.getStringExtra("RESULT_LABEL") ?: "Unknown"
        val confidence = intent.getFloatExtra("RESULT_CONF", 0f)
        val mode = intent.getStringExtra("RESULT_MODE") ?: "Plant"

        // Không cần lấy PHOTO_PATH nữa

        // Tạo fragment
        val fragment = if (mode == "Plant") {
            // Chỉ truyền ID, Label, Confidence. Không truyền ảnh chụp nữa.
            PlantFragment.newInstance(id, label, confidence)
        } else {
            DiseaseFragment.newInstance(id, label, confidence)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.resultContainer, fragment)
                .commit()
        }
    }
}