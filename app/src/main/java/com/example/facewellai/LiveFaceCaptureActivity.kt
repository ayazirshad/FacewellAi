package com.example.facewellai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@androidx.camera.core.ExperimentalGetImage
class LiveFaceCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var distanceTextView: TextView
    private lateinit var btnCapture: FloatingActionButton
    private lateinit var btnSwitchCamera: FloatingActionButton
    private lateinit var cameraExecutor: ExecutorService

    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private var imageCapture: ImageCapture? = null
    private var isInPerfectDistance: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_face_capture)

        previewView = findViewById(R.id.previewView)
        distanceTextView = findViewById(R.id.distanceTextView)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            startCamera()
        }

        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }

        btnCapture.setOnClickListener {
            previewView.bitmap?.let { bitmap ->
                val uri = getImageUriFromBitmap(bitmap)
                val intent = Intent(this, ImagePreviewActivity::class.java)
                intent.putExtra("image_uri", uri.toString())
                startActivity(intent)
                finish()
            } ?: run {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val screenHeight = getScreenHeight()

            val detectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build()

            val detector = FaceDetection.getClient(detectorOptions)

            val analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val face = faces[0]
                                val faceHeight = face.boundingBox.height()
                                val facePercentage = (faceHeight.toFloat() / screenHeight) * 100

                                val message = when {
                                    facePercentage < 10 -> {
                                        isInPerfectDistance = false
                                        "Move closer"
                                    }
                                    facePercentage > 12 -> {
                                        isInPerfectDistance = false
                                        "Move farther"
                                    }
                                    else -> {
                                        isInPerfectDistance = true
                                        "Perfect distance"
                                    }
                                }

                                runOnUiThread {
                                    btnCapture.isEnabled = isInPerfectDistance
                                    distanceTextView.text = "Face: ${"%.1f".format(facePercentage)}%\n$message"
                                    distanceTextView.setTextColor(
                                        if (isInPerfectDistance) ContextCompat.getColor(this, android.R.color.holo_green_light)
                                        else ContextCompat.getColor(this, android.R.color.white)
                                    )

                                }
                            } else {
                                runOnUiThread {
                                    isInPerfectDistance = false
                                    btnCapture.isEnabled = false
                                    distanceTextView.text = "No face detected"
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e("LiveFaceCapture", "Face detection failed", it)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    analysisUseCase
                )
            } catch (exc: Exception) {
                Log.e("LiveFaceCapture", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "CapturedFace", null)
        return Uri.parse(path)
    }

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
            val bounds = windowMetrics.bounds
            bounds.height() - insets.top - insets.bottom
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.heightPixels
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()  // 🔁 Start camera again if permission granted
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

}
