package com.example.facewellai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.content.res.AssetFileDescriptor

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var originalImageView: ImageView
    private lateinit var leftEyeImageView: ImageView
    private lateinit var rightEyeImageView: ImageView
    private lateinit var leftEyeLabel: TextView
    private lateinit var rightEyeLabel: TextView
    private lateinit var leftEyeResult: TextView
    private lateinit var rightEyeResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.darkHeader)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        originalImageView = findViewById(R.id.previewImage)
        leftEyeImageView = findViewById(R.id.leftEyeImage)
        rightEyeImageView = findViewById(R.id.rightEyeImage)
        leftEyeLabel = findViewById(R.id.leftEyeLabel)
        rightEyeLabel = findViewById(R.id.rightEyeLabel)
        leftEyeResult = findViewById(R.id.leftEyeResult)
        rightEyeResult = findViewById(R.id.rightEyeResult)

        val uriString = intent.getStringExtra("image_uri")
        val uri = Uri.parse(uriString)
        val bitmap = uriToBitmap(uri)
        originalImageView.setImageBitmap(bitmap)

        detectEyes(bitmap)

        findViewById<ImageView>(R.id.btnRetake).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btnConfirm).setOnClickListener {
            Toast.makeText(this, "Confirmed. Proceeding to next step...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } else {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
    }

    private fun detectEyes(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val face = faces[0]
                val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
                val faceBox = face.boundingBox

                if (leftEye == null || rightEye == null) {
                    Toast.makeText(this, "Eyes not clearly detected", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val boxWidth = (faceBox.width() * 0.35).toInt()
                val boxHeight = (faceBox.height() * 0.35).toInt()

                val leftEyeRect = Rect(
                    (leftEye.x - boxWidth / 2).toInt().coerceAtLeast(0),
                    (leftEye.y - boxHeight / 2).toInt().coerceAtLeast(0),
                    (leftEye.x + boxWidth / 2).toInt().coerceAtMost(bitmap.width),
                    (leftEye.y + boxHeight / 2).toInt().coerceAtMost(bitmap.height)
                )

                val rightEyeRect = Rect(
                    (rightEye.x - boxWidth / 2).toInt().coerceAtLeast(0),
                    (rightEye.y - boxHeight / 2).toInt().coerceAtLeast(0),
                    (rightEye.x + boxWidth / 2).toInt().coerceAtMost(bitmap.width),
                    (rightEye.y + boxHeight / 2).toInt().coerceAtMost(bitmap.height)
                )

                val leftCrop = Bitmap.createBitmap(bitmap, leftEyeRect.left, leftEyeRect.top, leftEyeRect.width(), leftEyeRect.height())
                val rightCrop = Bitmap.createBitmap(bitmap, rightEyeRect.left, rightEyeRect.top, rightEyeRect.width(), rightEyeRect.height())

                leftEyeImageView.setImageBitmap(leftCrop)
                rightEyeImageView.setImageBitmap(rightCrop)

                runModel(leftCrop, rightCrop)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Face detection failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun runModel(leftCrop: Bitmap, rightCrop: Bitmap) {
        val interpreter = loadModel()
        val leftInput = processImage(leftCrop)
        val rightInput = processImage(rightCrop)

        val leftOutput = Array(1) { FloatArray(5) }
        val rightOutput = Array(1) { FloatArray(5) }

        interpreter.run(leftInput, leftOutput)
        interpreter.run(rightInput, rightOutput)

        val classLabels = listOf("blepharitis", "cataracts", "conjunctivitis", "darkcircles", "normal")

        val leftIndex = leftOutput[0].withIndex().maxByOrNull { it.value }?.index ?: -1
        val rightIndex = rightOutput[0].withIndex().maxByOrNull { it.value }?.index ?: -1

        val leftConfidence = if (leftIndex != -1) leftOutput[0][leftIndex] else 0f
        val rightConfidence = if (rightIndex != -1) rightOutput[0][rightIndex] else 0f

        val leftPrediction = if (leftIndex != -1) classLabels[leftIndex] else "Unknown"
        val rightPrediction = if (rightIndex != -1) classLabels[rightIndex] else "Unknown"

        leftEyeLabel.text = "Left Eye: $leftPrediction"
        rightEyeLabel.text = "Right Eye: $rightPrediction"

        leftEyeResult.text = "Confidence: ${(leftConfidence * 100).toInt()}%"
        rightEyeResult.text = "Confidence: ${(rightConfidence * 100).toInt()}%"
    }

    private fun loadModel(): Interpreter {
        val fileDescriptor: AssetFileDescriptor = assets.openFd("eye_disease_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val modelBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        return Interpreter(modelBuffer)
    }

    private fun processImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }

        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized.getPixel(x, y)
                input[0][y][x][0] = ((pixel shr 16 and 0xFF) / 255.0f)
                input[0][y][x][1] = ((pixel shr 8 and 0xFF) / 255.0f)
                input[0][y][x][2] = ((pixel and 0xFF) / 255.0f)
            }
        }

        return input
    }
}
