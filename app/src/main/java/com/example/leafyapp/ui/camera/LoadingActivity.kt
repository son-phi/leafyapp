package com.example.leafyapp.ui.loading

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.leafyapp.R

class LoadingActivity : AppCompatActivity() {

    private lateinit var previewImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var analyzingText: TextView
    private lateinit var detectingText: TextView
    private lateinit var identifyingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        previewImage = findViewById(R.id.previewImage)
        progressBar = findViewById(R.id.progressBar)
        analyzingText = findViewById(R.id.analyzingText)
        detectingText = findViewById(R.id.detectingText)
        identifyingText = findViewById(R.id.identifyingText)

        val photoPath = intent.getStringExtra("photoPath")
        if (photoPath != null) {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            previewImage.setImageBitmap(bitmap)
        }

        simulateRecognitionProcess()
    }

    private fun simulateRecognitionProcess() {
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed({
            analyzingText.text = "✓ Analyzing image"
        }, 1500)

        handler.postDelayed({
            detectingText.text = "✓ Detecting leaves"
        }, 3000)

        handler.postDelayed({
            identifyingText.text = "⟳ Identifying plant..."
        }, 4500)

        // Giả lập hoàn tất sau 6 giây (mở sang kết quả)
        handler.postDelayed({
            identifyingText.text = "✓ Identifying plant"
            // TODO: mở sang màn hình kết quả
        }, 6000)
    }
}
