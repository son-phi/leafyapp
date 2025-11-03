package com.example.leafyapp.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
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

class CameraFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var btnDisease: Button
    private lateinit var btnPlant: Button
    private var currentMode = "Disease" // 🌿 mặc định nhận diện bệnh
    private val outputDirectory: File by lazy { createOutputDirectory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        // Liên kết view
        viewFinder = view.findViewById(R.id.viewFinder)
        btnDisease = view.findViewById(R.id.btnDisease)
        btnPlant = view.findViewById(R.id.btnPlant)
        val btnCapture = view.findViewById<ImageButton>(R.id.btnCapture)

        // Gán sự kiện
        btnDisease.setOnClickListener { setMode("Disease") }
        btnPlant.setOnClickListener { setMode("Plant") }

        btnCapture.setOnClickListener { takePhoto() }

        startCamera()
        return view
    }

    private fun setMode(mode: String) {
        currentMode = mode
        when (mode) {
            "Disease" -> {
                btnDisease.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
                )
                btnPlant.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.white)
                )
            }
            "Plant" -> {
                btnPlant.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
                )
                btnDisease.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.white)
                )
            }
        }
        Log.d("CameraFragment", "Mode set to: $currentMode")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewFinder.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
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
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraFragment", "Photo saved: ${photoFile.absolutePath}")
                    Log.d("CameraFragment", "Current mode: $currentMode")

                    // Tùy chế độ mà xử lý khác nhau
                    when (currentMode) {
                        "Disease" -> recognizeDisease(photoFile)
                        "Plant" -> recognizePlant(photoFile)
                    }
                }
            }
        )
    }

    private fun recognizeDisease(file: File) {
        // TODO: Gọi model nhận diện bệnh
        Log.i("CameraFragment", "Running disease recognition on: ${file.name}")
    }

    private fun recognizePlant(file: File) {
        // TODO: Gọi model nhận diện cây
        Log.i("CameraFragment", "Running plant recognition on: ${file.name}")
    }

    private fun createOutputDirectory(): File {
        val appContext = requireContext().applicationContext
        val mediaDir = appContext.getExternalFilesDir(null)?.let {
            File(it, "LeafyApp").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }


    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }
}
