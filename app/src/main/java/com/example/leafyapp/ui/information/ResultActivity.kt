package com.example.leafyapp.ui.information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // Nhận dữ liệu từ LoadingActivity
        val id = intent.getIntExtra("RESULT_ID", -1)
        val label = intent.getStringExtra("RESULT_LABEL") ?: "Unknown"
        val confidence = intent.getFloatExtra("RESULT_CONF", 0f)
        val mode = intent.getStringExtra("RESULT_MODE") ?: "Plant"

        // Tạo fragment tùy theo Plant / Disease
        val fragment = if (mode == "Plant") {
            PlantFragment.newInstance(id, label, confidence)
        } else {
            DiseaseFragment.newInstance(id, label, confidence)
        }

        // Gắn Fragment lên giao diện
        supportFragmentManager.beginTransaction()
            .replace(R.id.resultContainer, fragment)
            .commit()
    }
}
