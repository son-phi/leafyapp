package com.example.leafyapp.ui.information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // 1. Nhận dữ liệu (Hỗ trợ cả Key cũ "RESULT_ID" và Key mới "ID")
        // Ưu tiên lấy "ID" trước (từ Home/Search), nếu không có thì lấy "RESULT_ID" (từ Camera cũ)
        var rawId = intent.getIntExtra("ID", -1)
        if (rawId == -1) {
            rawId = intent.getIntExtra("RESULT_ID", -1)
        }

        val label = intent.getStringExtra("RESULT_LABEL") ?: "Unknown"
        val confidence = intent.getFloatExtra("RESULT_CONF", 0f)
        val mode = intent.getStringExtra("RESULT_MODE") ?: "Plant"

        // [QUAN TRỌNG] Kiểm tra cờ hiệu "IS_FROM_DB"
        // - true: Dữ liệu từ Database (Home/Search) -> ID đã chuẩn (1, 2, 3...)
        // - false (mặc định): Dữ liệu từ AI Camera -> ID bị lệch (0, 1, 2...)
        val isFromDb = intent.getBooleanExtra("IS_FROM_DB", false)

        // 2. Xử lý ID cuối cùng
        val finalId = if (rawId != -1) {
            if (mode == "Plant") {
                if (isFromDb) {
                    // Trường hợp 1: Từ Home/Search -> Giữ nguyên
                    rawId
                } else {
                    // Trường hợp 2: Từ Camera AI -> Cộng 1
                    rawId + 1
                }
            } else {
                // BỆNH: Logic cũ của bạn (AI và DB khớp nhau -> Giữ nguyên)
                rawId
            }
        } else {
            -1
        }

        // 3. Chọn Fragment phù hợp để hiển thị
        val fragment = if (mode == "Plant") {
            PlantFragment.newInstance(finalId, label, confidence)
        } else {
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