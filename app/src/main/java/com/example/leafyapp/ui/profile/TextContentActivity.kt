package com.example.leafyapp.ui.profile // Sửa lại package cho đúng với project của bạn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.databinding.ActivityTextContentBinding

class TextContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy dữ liệu được truyền sang
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Info"
        val content = intent.getStringExtra("EXTRA_CONTENT") ?: ""

        // Gán vào giao diện
        binding.tvHeaderTitle.text = title
        binding.tvContent.text = content

        // Xử lý nút Back
        binding.btnBack.setOnClickListener {
            finish() // Đóng màn hình quay lại
        }
    }
}