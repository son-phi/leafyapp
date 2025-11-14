package com.example.leafyapp.ui.camera

import com.example.leafyapp.api.ApiClient
import com.example.leafyapp.api.PredictionResponse
import android.content.Intent
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
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class LoadingActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvAnalyzing: TextView
    private lateinit var tvDetecting: TextView
    private lateinit var tvIdentifying: TextView

    private val loadingSteps = listOf(
        "Analyzing image",
        "Detecting relevant area",
        "Identifying result"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = Color.BLACK

        imageView = findViewById(R.id.imageViewScan)
        tvAnalyzing = findViewById(R.id.tvAnalyzing)
        tvDetecting = findViewById(R.id.tvDetecting)
        tvIdentifying = findViewById(R.id.tvIdentifying)

        val photoPath = intent.getStringExtra("PHOTO_PATH")
        val scanMode = intent.getStringExtra("SCAN_MODE")

        if (photoPath.isNullOrEmpty() || scanMode.isNullOrEmpty()) {
            Log.e("LoadingActivity", "PHOTO_PATH or SCAN_MODE is null!")
            finish()
            return
        }

        if (photoPath.startsWith("content://")) {
            Glide.with(this).load(Uri.parse(photoPath)).centerCrop().into(imageView)
        } else {
            Glide.with(this).load(File(photoPath)).centerCrop().into(imageView)
        }

        startRecognition(photoPath, scanMode)
    }

    private fun startRecognition(imagePath: String, mode: String) {

        updateStep(0, false)
        updateStep(1, false)
        updateStep(2, false)

        lifecycleScope.launch {

            var finalResponse: PredictionResponse? = null

            try {
                updateStep(0, true)
                updateStep(1, true)

                finalResponse = runRecognition(imagePath, mode)

                updateStep(2, true)

            } catch (e: Exception) {
                Log.e("LoadingActivity", "Error in processing", e)
                updateStep(2, true)
            }

            navigateToResultScreen(finalResponse, mode)
        }
    }

    private suspend fun runRecognition(imagePath: String, mode: String): PredictionResponse {
        return withContext(Dispatchers.IO) {

            val file = if (imagePath.startsWith("content://")) {
                val inputStream = contentResolver.openInputStream(Uri.parse(imagePath))
                    ?: throw Exception("Cannot open image input stream")
                val temp = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                temp.outputStream().use { out -> inputStream.copyTo(out) }
                temp.deleteOnExit()
                temp
            } else File(imagePath)

            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val multiPart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            if (mode == "Plant")
                ApiClient.instance.predictPlant(multiPart)
            else
                ApiClient.instance.predictDisease(multiPart)
        }
    }

    private fun updateStep(index: Int, completed: Boolean) {
        runOnUiThread {
            val tv = when (index) {
                0 -> tvAnalyzing
                1 -> tvDetecting
                2 -> tvIdentifying
                else -> return@runOnUiThread
            }

            val prefix = if (completed) "✅" else "⚪"
            val color = if (completed) Color.parseColor("#FFC107") else Color.WHITE

            tv.text = "$prefix ${loadingSteps[index]}"
            tv.setTextColor(color)
        }
    }

    private fun navigateToResultScreen(result: PredictionResponse?, mode: String) {
        if (result == null) {
            Log.e("LoadingActivity", "Result null, cannot navigate!")
            return
        }

        val intent = Intent(this, ResultActivity::class.java)

        intent.putExtra("RESULT_ID", result.id)
        intent.putExtra("RESULT_LABEL", result.label)
        intent.putExtra("RESULT_CONF", result.confidence)
        intent.putExtra("RESULT_MODE", mode)

        startActivity(intent)
        finish()
    }
}
