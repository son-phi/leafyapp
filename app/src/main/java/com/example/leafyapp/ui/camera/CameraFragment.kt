package com.example.leafyapp.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.ImageButton
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.leafyapp.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.fragment.findNavController

class CameraFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var btnDiseaseMode: LinearLayout
    private lateinit var btnPlantMode: LinearLayout
    private var currentMode = "Disease" // 🌿 mặc định nhận diện bệnh
    private val outputDirectory: File by lazy { createOutputDirectory() }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        // Liên kết view
        viewFinder = view.findViewById(R.id.viewFinder)
        btnDiseaseMode = view.findViewById(R.id.btnDiseaseMode)
        btnPlantMode = view.findViewById(R.id.btnPlantMode)
        val btnCapture = view.findViewById<ImageButton>(R.id.btnCapture)

        viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER

        // Gán sự kiện
        btnDiseaseMode.setOnClickListener { setMode("Disease") }
        btnPlantMode.setOnClickListener { setMode("Plant") }
        btnCapture.setOnClickListener { takePhoto() }

        // Set mode mặc định
        setMode("Disease")

        // Kiểm tra quyền camera
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        return view
    }

    private fun setMode(mode: String) {
        currentMode = mode
        when (mode) {
            "Disease" -> {
                // Disease active (màu đỏ), Plant inactive (màu xám)
                btnDiseaseMode.setBackgroundResource(R.drawable.btn_disease_bg)
                btnPlantMode.setBackgroundResource(R.drawable.btn_plant_bg)
            }
            "Plant" -> {
                // Plant active (màu đỏ), Disease inactive (màu xám)
                btnPlantMode.setBackgroundResource(R.drawable.btn_plant_active_bg)
                btnDiseaseMode.setBackgroundResource(R.drawable.btn_plant_bg)
            }
        }
        Log.d("CameraFragment", "Mode set to: $currentMode")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(viewFinder.display.rotation)
                .build()
                .also {
                    it.surfaceProvider = viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(viewFinder.display.rotation)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                Log.d("CameraFragment", "✅ Camera started successfully")
            } catch (exc: Exception) {
                Log.e("CameraFragment", "❌ Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "❌ Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraFragment", "✅ Photo saved: ${photoFile.absolutePath}")
                    when (currentMode) {
                        "Disease" -> recognizeDisease(photoFile)
                        "Plant" -> recognizePlant(photoFile)
                    }
                }
            }
        )
    }

    private fun recognizeDisease(file: File) {
        Log.i("CameraFragment", "Running disease recognition on: ${file.name}")
        // TODO: Gọi model nhận diện bệnh
    }

    private fun recognizePlant(file: File) {
        Log.i("CameraFragment", "Running plant recognition on: ${file.name}")
        // TODO: Gọi model nhận diện cây
    }

    private fun createOutputDirectory(): File {
        val appContext = requireContext().applicationContext
        val mediaDir = appContext.getExternalFilesDir(null)?.let {
            File(it, "LeafyApp").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Log.e("CameraFragment", "❌ Permissions not granted by the user.")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}