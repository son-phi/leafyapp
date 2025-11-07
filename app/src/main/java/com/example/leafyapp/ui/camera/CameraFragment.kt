package com.example.leafyapp.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
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
    private lateinit var btnDiseaseMode: LinearLayout
    private lateinit var btnPlantMode: LinearLayout
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnGallery: ImageButton

    private var imageCapture: ImageCapture? = null
    private var currentMode = "Disease"
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val outputDirectory by lazy { requireContext().getExternalFilesDir("LeafyApp")!! }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private const val REQUEST_GALLERY = 2001
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        viewFinder = view.findViewById(R.id.viewFinder)
        btnDiseaseMode = view.findViewById(R.id.btnDiseaseMode)
        btnPlantMode = view.findViewById(R.id.btnPlantMode)
        btnSwitchCamera = view.findViewById(R.id.btnSwitchCamera)
        btnGallery = view.findViewById(R.id.btnGallery)
        val btnCapture = view.findViewById<ImageButton>(R.id.btnCapture)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        btnDiseaseMode.setOnClickListener { setMode("Disease") }
        btnPlantMode.setOnClickListener { setMode("Plant") }
        btnCapture.setOnClickListener { takePhoto() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnGallery.setOnClickListener { openGallery() }
        btnClose.setOnClickListener { findNavController().popBackStack() }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        return view
    }

    private fun setMode(mode: String) {
        currentMode = mode
        btnDiseaseMode.setBackgroundResource(if (mode == "Disease") R.drawable.btn_disease_bg else R.drawable.btn_plant_bg)
        btnPlantMode.setBackgroundResource(if (mode == "Plant") R.drawable.btn_plant_active_bg else R.drawable.btn_plant_bg)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .build()
                .also { it.surfaceProvider = viewFinder.surfaceProvider }

            imageCapture = ImageCapture.Builder().build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val file = File(outputDirectory, "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(output, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    navigateToLoading(file.absolutePath)
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Capture failed", exc)
                }
            })
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_GALLERY && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                val intent = Intent(requireContext(), LoadingActivity::class.java)
                intent.putExtra("PHOTO_PATH", imageUri.toString()) // ✅ gửi URI
                intent.putExtra("SCAN_MODE", currentMode)
                startActivity(intent)
            }
        }
    }


    private fun getRealPathFromURI(uri: Uri): String? {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.moveToFirst()
        val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        val path = cursor.getString(index)
        cursor.close()
        return path
    }

    private fun navigateToLoading(path: String) {
        val intent = Intent(requireContext(), LoadingActivity::class.java)
        intent.putExtra("PHOTO_PATH", path)
        intent.putExtra("SCAN_MODE", currentMode)
        startActivity(intent)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }
}
