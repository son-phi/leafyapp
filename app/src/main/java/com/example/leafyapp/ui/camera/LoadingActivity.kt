package com.example.leafyapp.ui.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.ui.information.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LoadingActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvAnalyzing: TextView
    private lateinit var tvDetecting: TextView
    private lateinit var tvIdentifying: TextView

    // TFLite model names
    private val plantModel = "plant.tflite"
    private val diseaseModel = "disease.tflite"
    private val plantLabels = "plant_labels.txt"
    private val diseaseLabels = "disease_labels.txt"

    // Danh sách các bước loading thực tế (dùng cho updateStepText)
    private val loadingSteps = listOf("Analyzing image", "Detecting relevant area", "Identifying result")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        // Setup UI
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = Color.BLACK

        // Ánh xạ View
        imageView = findViewById(R.id.imageViewScan)
        tvAnalyzing = findViewById(R.id.tvAnalyzing)
        tvDetecting = findViewById(R.id.tvDetecting)
        tvIdentifying = findViewById(R.id.tvIdentifying)

        // Lấy dữ liệu từ Intent
        val photoPath = intent.getStringExtra("PHOTO_PATH") // Có thể là File Path HOẶC Uri String
        val scanMode = intent.getStringExtra("SCAN_MODE")

        if (photoPath == null || scanMode == null) {
            Log.e("LoadingActivity", "Photo path or Scan mode is null!")
            finish()
            return
        }

        // Tải ảnh vào ImageView: Hỗ trợ cả Uri (Gallery) và File Path (Camera)
        if (photoPath.startsWith("content://")) {
            Glide.with(this).load(Uri.parse(photoPath)).centerCrop().into(imageView)
        } else {
            Glide.with(this).load(File(photoPath)).centerCrop().into(imageView)
        }

        // 🚀 Bắt đầu quá trình nhận diện thực tế
        startRecognition(photoPath, scanMode)
    }

    private fun startRecognition(imagePath: String, mode: String) {
        // Khởi tạo trạng thái ban đầu
        updateStep(0, false)
        updateStep(1, false)
        updateStep(2, false)

        // ✅ Dùng Coroutine để chạy model trên luồng nền
        lifecycleScope.launch {
            var recognitionResult: String? = null
            var bitmap: Bitmap? = null

            try {
                // Bước 1: Analyzing image
                updateStep(0, true)

                // Bước 2: Detecting relevant area (Đang xử lý)
                updateStep(1, false)

                // Chạy model trong luồng I/O
                recognitionResult = withContext(Dispatchers.IO) {
                    // Đọc ảnh thành Bitmap (Hỗ trợ cả File Path và Uri)
                    bitmap = readBitmapFromPath(imagePath)

                    if (bitmap == null) {
                        Log.e("LoadingActivity", "Failed to decode bitmap from path: $imagePath")
                        return@withContext "Error: Failed to load image."
                    }

                    // Chạy nhận diện TFLite
                    runRecognition(bitmap!!, mode)
                }

                // Bước 2 & 3: Hoàn thành Detecting và Identifying
                updateStep(1, true)
                updateStep(2, true)

            } catch (e: Exception) {
                Log.e("LoadingActivity", "AI processing failed unexpectedly", e)
                recognitionResult = "Error: ${e.message}"
                updateStep(2, true)
            }

            // 4. Điều hướng đến màn hình kết quả sau khi xử lý xong
            navigateToResultScreen(recognitionResult ?: "Error: Unknown failure.", mode)
        }
    }

    private fun readBitmapFromPath(imagePath: String): Bitmap? {
        return try {
            if (imagePath.startsWith("content://")) {
                // Hỗ trợ Gallery URI
                contentResolver.openInputStream(Uri.parse(imagePath))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                // Hỗ trợ File Path (CameraX)
                BitmapFactory.decodeFile(imagePath)
            }
        } catch (e: Exception) {
            Log.e("LoadingActivity", "Error reading bitmap from path/uri: $imagePath", e)
            null
        }
    }

    private fun runRecognition(bitmap: Bitmap, mode: String): String {
        val modelName = if (mode == "Plant") plantModel else diseaseModel
        val labelsFile = if (mode == "Plant") plantLabels else diseaseLabels

        val labels = assets.open(labelsFile).bufferedReader().readLines()

        // 1. Tải Model
        val modelBytes = assets.open(modelName).readBytes()
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        modelBuffer.put(modelBytes).rewind()

        // Sử dụng khối try-use để đảm bảo Interpreter được đóng
        Interpreter(modelBuffer).use { interpreter ->

            // 2. Tiền xử lý ảnh
            val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

            // Kích thước Input: [1, 224, 224, 3] với Float (4 bytes/float)
            val input = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())

            // Chuẩn hóa [-1, 1]
            for (y in 0 until 224)
                for (x in 0 until 224) {
                    val px = resized.getPixel(x, y)
                    input.putFloat((Color.red(px) - 127.5f) / 127.5f)
                    input.putFloat((Color.green(px) - 127.5f) / 127.5f)
                    input.putFloat((Color.blue(px) - 127.5f) / 127.5f)
                }

            // 3. Khởi tạo Output
            val output = ByteBuffer.allocateDirect(labels.size * 4).order(ByteOrder.nativeOrder())

            // 4. Chạy nhận diện (Inference)
            interpreter.run(input, output)

            val scores = FloatArray(labels.size)
            output.rewind()
            output.asFloatBuffer().get(scores)

            // 5. Xử lý kết quả
            val idx = scores.indices.maxByOrNull { scores[it] } ?: 0
            val confidence = String.format("%.2f", scores[idx] * 100)
            return "${labels[idx]} (Confidence: $confidence%)"
        }
    }

    private fun updateStep(index: Int, completed: Boolean) {
        runOnUiThread {
            val textView = when (index) {
                0 -> tvAnalyzing
                1 -> tvDetecting
                2 -> tvIdentifying
                else -> return@runOnUiThread
            }

            val prefix = if (completed) "✅" else "⚪"
            val color = if (completed) Color.parseColor("#FFC107") else Color.WHITE

            textView.text = "$prefix ${loadingSteps[index]}"
            textView.setTextColor(color)
        }
    }

    // Hàm điều hướng thực tế
    private fun navigateToResultScreen(result: String, mode: String) {
        Log.i("LoadingActivity", "Recognition Complete! Result: $result")

        // Điều hướng sang ResultActivity
        val intent = Intent(this@LoadingActivity, ResultActivity::class.java)
        intent.putExtra("RESULT", result)
        intent.putExtra("MODE", mode)
        startActivity(intent)
        finish()
    }
}