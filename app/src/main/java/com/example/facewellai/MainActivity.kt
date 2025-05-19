package com.example.facewellai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var handler: Handler
    private lateinit var descriptionText: TextView
    private lateinit var cameraHint: TextView
    private lateinit var imageUri: Uri
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraProvider: ProcessCameraProvider
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val imageList = listOf(
        R.drawable.eye_img,
        R.drawable.skin_img,
        R.drawable.stress_img
    )

    private val descriptions = listOf(
        "Scan Eye for Diseases",
        "Detect Skin Issues",
        "Analyze Mental Stress"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.darkHeader)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        viewPager = findViewById(R.id.viewPager)
        descriptionText = findViewById(R.id.slideDescription)
        cameraHint = findViewById(R.id.cameraHint)

        viewPager.adapter = ImageSliderAdapter(imageList)

        handler = Handler(Looper.getMainLooper())
        autoScroll()

        animateTextView(descriptionText)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                descriptionText.text = descriptions[position]
                animateTextView(descriptionText)
            }
        })

        findViewById<FloatingActionButton>(R.id.btnCamera).setOnClickListener {
            showCameraOptions()
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                openPreviewActivity(imageUri)
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedImageUri: Uri? = result.data?.data
                selectedImageUri?.let {
                    openPreviewActivity(it)
                }
            }
        }
    }

    private fun autoScroll() {
        val runnable = object : Runnable {
            override fun run() {
                val next = (viewPager.currentItem + 1) % imageList.size
                viewPager.setCurrentItem(next, true)
                handler.postDelayed(this, 3000)
            }
        }
        handler.postDelayed(runnable, 3000)
    }

    private fun showCameraOptions() {
        val options = arrayOf("Live Face Detection", "System Camera", "Select from Gallery")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose Option")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    // ✅ Live Face Detection
                    val intent = Intent(this, LiveFaceCaptureActivity::class.java)
                    startActivity(intent)
                }
                1 -> {
                    // ✅ System Camera
                    val photoFile = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
                    imageUri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                    cameraLauncher.launch(intent)
                }
                2 -> {
                    // ✅ Select from Gallery
                    openGallery()
                }
            }
        }
        builder.show()
    }


    private fun openLiveCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        val intent = Intent(this, LiveFaceCaptureActivity::class.java)
        startActivity(intent)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }

        val photoFile = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        imageUri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
    }

    private fun openPreviewActivity(uri: Uri) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        intent.putExtra("image_uri", uri.toString())
        startActivity(intent)
    }

    private fun animateTextView(textView: TextView) {
        val translate = TranslateAnimation(0f, 0f, 100f, 0f).apply {
            duration = 400
            fillAfter = true
        }
        val fade = AlphaAnimation(0f, 1f).apply {
            duration = 400
            fillAfter = true
        }

        val animationSet = android.view.animation.AnimationSet(true).apply {
            addAnimation(translate)
            addAnimation(fade)
        }

        textView.startAnimation(animationSet)
    }
}
