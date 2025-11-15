package com.example.leafyapp.ui.camera

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.leafyapp.R
import com.example.leafyapp.api.ApiClient
import com.example.leafyapp.api.PredictionResponse
import com.example.leafyapp.ui.information.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class LoadingActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView

    private lateinit var tvAnalyzing: TextView
    private lateinit var tvDetecting: TextView
    private lateinit var tvIdentifying: TextView

    private lateinit var loading1: ProgressBar
    private lateinit var loading2: ProgressBar
    private lateinit var loading3: ProgressBar

    private lateinit var check1: ImageView
    private lateinit var check2: ImageView
    private lateinit var check3: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        hideStatusBar()
        initViews()
        applyAnimations()

        val photoPath = intent.getStringExtra("PHOTO_PATH")
        val scanMode = intent.getStringExtra("SCAN_MODE")

        if (photoPath.isNullOrEmpty() || scanMode.isNullOrEmpty()) {
            Log.e("LoadingActivity", "Missing PHOTO_PATH or SCAN_MODE")
            finish()
            return
        }

        loadImage(photoPath)
        startRecognition(photoPath, scanMode)
    }

    private fun hideStatusBar() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = Color.BLACK
    }

    private fun initViews() {
        imageView = findViewById(R.id.imageViewScan)

        tvAnalyzing = findViewById(R.id.tvAnalyzing)
        tvDetecting = findViewById(R.id.tvDetecting)
        tvIdentifying = findViewById(R.id.tvIdentifying)

        loading1 = findViewById(R.id.loading1)
        loading2 = findViewById(R.id.loading2)
        loading3 = findViewById(R.id.loading3)

        check1 = findViewById(R.id.check1)
        check2 = findViewById(R.id.check2)
        check3 = findViewById(R.id.check3)
    }

    private fun applyAnimations() {
        val fade = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)

        imageView.startAnimation(fade)
        tvAnalyzing.startAnimation(fade)
        tvDetecting.startAnimation(fade)
        tvIdentifying.startAnimation(fade)

        loading1.startAnimation(pulse)
        loading2.startAnimation(pulse)
        loading3.startAnimation(pulse)
    }

    private fun loadImage(photoPath: String) {

        val finalFile = if (photoPath.startsWith("content://")) {
            // Convert URI → File tạm
            val inputStream = contentResolver.openInputStream(Uri.parse(photoPath))
            val temp = File(cacheDir, "preview_${System.currentTimeMillis()}.jpg")
            temp.outputStream().use { out -> inputStream?.copyTo(out) }
            temp
        } else File(photoPath)

        Glide.with(this)
            .load(finalFile)
            .centerCrop()
            .transform(RoundedCorners(40))
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(imageView)
    }



    private fun startRecognition(photoPath: String, mode: String) {

        updateStep(0, false)
        updateStep(1, false)
        updateStep(2, false)

        lifecycleScope.launch {
            var result: PredictionResponse? = null

            try {
                updateStep(0, true)

                result = runRecognition(photoPath, mode)

                updateStep(1, true)
                updateStep(2, true)

            } catch (e: Exception) {
                Log.e("LoadingActivity", "API ERROR", e)
                updateStep(2, true)
            }

            navigateToResultScreen(result, mode)
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

            val req = file.asRequestBody("image/*".toMediaTypeOrNull())
            val multipart = MultipartBody.Part.createFormData("file", file.name, req)

            if (mode == "Plant") ApiClient.instance.predictPlant(multipart)
            else ApiClient.instance.predictDisease(multipart)
        }
    }

    private fun updateStep(index: Int, completed: Boolean) {
        val loadingList = listOf(loading1, loading2, loading3)
        val checkList = listOf(check1, check2, check3)
        val textList = listOf(tvAnalyzing, tvDetecting, tvIdentifying)

        if (completed) {
            loadingList[index].clearAnimation()
            loadingList[index].visibility = View.GONE
            checkList[index].visibility = View.VISIBLE

            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            checkList[index].startAnimation(fadeIn)

            textList[index].setTextColor(Color.parseColor("#FFC107"))
        } else {
            loadingList[index].visibility = View.VISIBLE
            checkList[index].visibility = View.GONE
            textList[index].setTextColor(Color.WHITE)
        }
    }

    private fun navigateToResultScreen(result: PredictionResponse?, mode: String) {
        if (result == null) return

        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("RESULT_ID", result.id)
        intent.putExtra("RESULT_LABEL", result.label)
        intent.putExtra("RESULT_CONF", result.confidence)
        intent.putExtra("RESULT_MODE", mode)
        startActivity(intent)
        finish()
    }
}
