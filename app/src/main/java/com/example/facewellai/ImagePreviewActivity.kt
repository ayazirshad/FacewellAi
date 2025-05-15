package com.example.facewellai

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class ImagePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val imageView: ImageView = findViewById(R.id.previewImage)
        val uriString = intent.getStringExtra("image_uri")
        imageView.setImageURI(Uri.parse(uriString))
    }
}
