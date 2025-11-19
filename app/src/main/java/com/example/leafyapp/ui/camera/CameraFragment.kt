package com.example.leafyapp.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.leafyapp.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var selectorView: View
    private lateinit var tvDisease: TextView
    private lateinit var tvPlant: TextView

    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnInfo: ImageButton

    // Mặc định ban đầu là Disease
    private var currentMode = "Disease"

    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val outputDirectory by lazy {
        requireContext().getExternalFilesDir("LeafyApp")!!
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) openLoadingActivity(uri.toString())
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_camera, container, false)

        bindViews(v)
        setupClicks()

        // Set trạng thái màu sắc ban đầu (Disease được chọn)
        updateModeUI(isDisease = true, animate = false)

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(
            requireActivity(),
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )

        return v
    }

    private fun bindViews(v: View) {
        viewFinder = v.findViewById(R.id.viewFinder)

        selectorView = v.findViewById(R.id.selectorView)
        tvDisease = v.findViewById(R.id.tvDisease)
        tvPlant = v.findViewById(R.id.tvPlant)

        btnSwitchCamera = v.findViewById(R.id.btnSwitchCamera)
        btnGallery = v.findViewById(R.id.btnGallery)
        btnInfo = v.findViewById(R.id.btnInfo)

        // Close button
        v.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            findNavController().popBackStack()
        }

        // Capture button
        v.findViewById<ImageButton>(R.id.btnCapture).setOnClickListener {
            takePhoto()
        }
    }

    private fun setupClicks() {
        // Bấm vào chữ Disease
        tvDisease.setOnClickListener {
            if (currentMode != "Disease") {
                currentMode = "Disease"
                updateModeUI(isDisease = true, animate = true)
            }
        }

        // Bấm vào chữ Plant
        tvPlant.setOnClickListener {
            if (currentMode != "Plant") {
                currentMode = "Plant"
                updateModeUI(isDisease = false, animate = true)
            }
        }

        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnGallery.setOnClickListener { openGallery() }
        btnInfo.setOnClickListener {
            CaptureTipsDialog().show(parentFragmentManager, "CaptureTipsDialog")
        }
    }

    // ---------------------------
    //   LOGIC ĐỔI MÀU & ANIMATION
    // ---------------------------

    private fun updateModeUI(isDisease: Boolean, animate: Boolean) {
        // 1. Tính toán vị trí dịch chuyển (TranslationX)
        // Nếu Disease (trái) -> x = 0
        // Nếu Plant (phải) -> x = chiều rộng của tvDisease (để nhảy sang ô bên cạnh)
        val targetX = if (isDisease) 0f else tvDisease.width.toFloat()

        // 2. Animation cục trắng
        if (animate) {
            selectorView.animate()
                .translationX(targetX)
                .setDuration(200) // Tốc độ 200ms
                .start()
        } else {
            selectorView.translationX = targetX
        }

        // 3. Đổi màu chữ (Quan trọng: Tương phản với nền)
        if (isDisease) {
            // Chọn Disease: Disease Đen (nền trắng), Plant Trắng (nền đen)
            tvDisease.setTextColor(Color.BLACK)
            tvPlant.setTextColor(Color.WHITE)
        } else {
            // Chọn Plant: Disease Trắng (nền đen), Plant Đen (nền trắng)
            tvDisease.setTextColor(Color.WHITE)
            tvPlant.setTextColor(Color.BLACK)
        }
    }

    // ---------------------------
    //   CAMERA FUNCTIONS
    // ---------------------------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())

        future.addListener({
            val cameraProvider = future.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = viewFinder.surfaceProvider }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraFragment", "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun switchCamera() {
        cameraSelector =
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

        startCamera()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(outputDirectory, fileName)

        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Chụp xong chuyển sang LoadingActivity
                    openLoadingActivity(file.absolutePath)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Capture failed", exc)
                }
            }
        )
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun openLoadingActivity(path: String) {
        val intent = Intent(requireContext(), LoadingActivity::class.java)
        intent.putExtra("PHOTO_PATH", path)
        intent.putExtra("SCAN_MODE", currentMode) // Truyền mode (Disease/Plant) sang màn hình sau
        startActivity(intent)
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }
}