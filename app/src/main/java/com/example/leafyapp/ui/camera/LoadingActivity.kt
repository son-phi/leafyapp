package com.example.leafyapp.ui.camera

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.leafyapp.R
import com.example.leafyapp.api.ApiClient
import com.example.leafyapp.api.PredictionResponse
import com.example.leafyapp.databinding.ActivityLoadingBinding
import com.example.leafyapp.ui.information.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class LoadingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoadingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup ViewBinding
        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideStatusBar()
        applyAnimations()

        val photoPath = intent.getStringExtra("PHOTO_PATH")
        // Lấy chế độ (Ưu tiên key SCAN_MODE, dự phòng key MODE)
        val scanMode = intent.getStringExtra("SCAN_MODE") ?: intent.getStringExtra("MODE")

        // 1. CHECK NULL TRƯỚC (Fail fast)
        if (photoPath.isNullOrEmpty() || scanMode.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing data!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. SET TEXT TIẾNG ANH (Chỉ 1 lần duy nhất ở đây)
        if (scanMode == "Disease") {
            // Chế độ BỆNH (Disease)
            binding.tvTitle.text = "Diagnosing Disease..."
            binding.tvAnalyzing.text = "Analyzing symptoms"
            binding.tvDetecting.text = "Scanning infection area"
            binding.tvIdentifying.text = "Formulating diagnosis"
        } else {
            // Chế độ CÂY (Plant)
            binding.tvTitle.text = "Identifying Plant..."
            binding.tvAnalyzing.text = "Analyzing leaf structure"
            binding.tvDetecting.text = "Matching botanical data"
            binding.tvIdentifying.text = "Retrieving species info"
        }

        // 3. CHẠY LOGIC
        loadImage(photoPath)
        startRecognition(photoPath, scanMode!!)
    }

    private fun hideStatusBar() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = Color.BLACK
    }

    private fun applyAnimations() {
        val fade = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)

        binding.tvAnalyzing.startAnimation(fade)
        binding.tvDetecting.startAnimation(fade)
        binding.tvIdentifying.startAnimation(fade)

        binding.loading1.startAnimation(pulse)
        binding.loading2.startAnimation(pulse)
        binding.loading3.startAnimation(pulse)
    }

    private fun loadImage(photoPath: String) {
        // Chạy logic đọc file ở background để tránh lag UI
        lifecycleScope.launch(Dispatchers.IO) {
            val finalFile = if (photoPath.startsWith("content://")) {
                try {
                    val inputStream = contentResolver.openInputStream(Uri.parse(photoPath))
                    val temp = File(cacheDir, "preview_${System.currentTimeMillis()}.jpg")
                    temp.outputStream().use { out -> inputStream?.copyTo(out) }
                    temp
                } catch (e: Exception) {
                    Log.e("LoadingActivity", "Error loading content URI", e)
                    null
                }
            } else File(photoPath)

            // Quay lại Main Thread để hiển thị ảnh
            withContext(Dispatchers.Main) {
                if (finalFile != null) {
                    Glide.with(this@LoadingActivity)
                        .load(finalFile)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(binding.imageViewScan)
                }
            }
        }
    }

    private fun startRecognition(photoPath: String, mode: String) {
        updateStep(0, false)
        updateStep(1, false)
        updateStep(2, false)

        lifecycleScope.launch {
            var result: PredictionResponse? = null
            var errorMsg: String? = null

            try {
                // Bước 1: Giả lập phân tích ảnh (để UI đẹp hơn)
                delay(800)
                updateStep(0, true)

                // Bước 2: Gọi API
                result = runRecognition(photoPath, mode)

                updateStep(1, true)

                // Bước 3: Hoàn tất
                delay(500)
                updateStep(2, true)

            } catch (e: Exception) {
                Log.e("LoadingActivity", "API ERROR", e)
                errorMsg = "Lỗi kết nối: ${e.message}"
            }

            // Chuyển màn hình
            navigateToResultScreen(result, mode, errorMsg)
        }
    }

    private suspend fun runRecognition(imagePath: String, mode: String): PredictionResponse {
        return withContext(Dispatchers.IO) {
            val file = if (imagePath.startsWith("content://")) {
                val inputStream = contentResolver.openInputStream(Uri.parse(imagePath))
                    ?: throw Exception("Cannot read image")
                val tmp = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                tmp.outputStream().use { out -> inputStream.copyTo(out) }
                tmp
            } else File(imagePath)

            // Lưu ý: "file" là tên field server backend yêu cầu.
            // Nếu server php/python của bạn yêu cầu tên khác (ví dụ "image") thì sửa ở đây.
            val req = file.asRequestBody("image/*".toMediaTypeOrNull())
            val multipart = MultipartBody.Part.createFormData("file", file.name, req)

            if (mode == "Plant") ApiClient.instance.predictPlant(multipart)
            else ApiClient.instance.predictDisease(multipart)
        }
    }

    private fun updateStep(index: Int, completed: Boolean) {
        // Dùng binding để truy cập view
        val loadingList = listOf(binding.loading1, binding.loading2, binding.loading3)
        val checkList = listOf(binding.check1, binding.check2, binding.check3)
        val textList = listOf(binding.tvAnalyzing, binding.tvDetecting, binding.tvIdentifying)

        if (completed) {
            loadingList[index].clearAnimation()
            loadingList[index].visibility = View.GONE
            checkList[index].visibility = View.VISIBLE

            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            checkList[index].startAnimation(fadeIn)

            textList[index].setTextColor(Color.parseColor("#4CAF50")) // Màu xanh thành công
        } else {
            loadingList[index].visibility = View.VISIBLE
            checkList[index].visibility = View.GONE
            textList[index].setTextColor(Color.WHITE)
        }
    }

    private fun navigateToResultScreen(result: PredictionResponse?, mode: String, errorMsg: String?) {
        if (result == null) {
            Toast.makeText(this, errorMsg ?: "Không nhận diện được", Toast.LENGTH_LONG).show()
            finish() // Đóng loading quay về camera
            return
        }

        val intent = Intent(this, ResultActivity::class.java)

        // Truyền ID gốc từ Model (0, 1, 2...)
        // ResultActivity sẽ lo việc cộng thêm 1 nếu là Plant
        intent.putExtra("RESULT_ID", result.id)

        intent.putExtra("RESULT_LABEL", result.label)
        intent.putExtra("RESULT_CONF", result.confidence)

        // Truyền lại MODE để ResultActivity biết đường xử lý ID
        intent.putExtra("RESULT_MODE", mode)

        startActivity(intent)
        finish()
    }
}