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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.leafyapp.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.view.animation.TranslateAnimation

class CameraFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var selectorView: View
    private lateinit var tvDisease: TextView
    private lateinit var tvPlant: TextView

    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnInfo: ImageButton

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

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

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

        // Switch mode
        tvDisease.setOnClickListener { switchToDisease() }
        tvPlant.setOnClickListener { switchToPlant() }

        // Switch camera
        btnSwitchCamera.setOnClickListener { switchCamera() }

        // Open gallery
        btnGallery.setOnClickListener { openGallery() }

        // Popup
        btnInfo.setOnClickListener {
            CaptureTipsDialog().show(parentFragmentManager, "CaptureTipsDialog")
        }
    }

    // ---------------------------
    //   MODE SWITCH + ANIMATION
    // ---------------------------

    private fun switchToDisease() {
        if (currentMode == "Disease") return
        currentMode = "Disease"
        animateSelector(toRight = false)   // chạy về trái
    }

    private fun switchToPlant() {
        if (currentMode == "Plant") return
        currentMode = "Plant"
        animateSelector(toRight = true)    // chạy sang phải
    }

    private fun animateSelector(toRight: Boolean) {

        // Padding đúng hướng — ngoài 4dp, giữa = 0dp
        if (toRight) {
            // Sang PLANT -> padding bên phải
            selectorView.setPadding(0, 0, 4, 0)
        } else {
            // Sang DISEASE -> padding bên trái
            selectorView.setPadding(4, 0, 0, 0)
        }

        val parent = selectorView.parent as ConstraintLayout
        val halfWidth = parent.width / 2f

        val fromX = if (toRight) 0f else halfWidth
        val toX   = if (toRight) halfWidth else 0f

        val anim = TranslateAnimation(fromX, toX, 0f, 0f)
        anim.duration = 220
        anim.fillAfter = true

        selectorView.startAnimation(anim)
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

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

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
        intent.putExtra("SCAN_MODE", currentMode)
        startActivity(intent)
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }
}
