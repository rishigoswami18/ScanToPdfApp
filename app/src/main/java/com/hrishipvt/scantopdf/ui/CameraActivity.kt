package com.hrishipvt.scantopdf.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.utils.ScanSession
import java.io.File

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var textRecognizer: TextRecognizer
    private var lastExtractedText: String = ""

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val btnCapture = findViewById<MaterialButton>(R.id.btnCapture)
        val btnDone = findViewById<MaterialButton>(R.id.btnDone)
        val btnExtractText = findViewById<MaterialButton>(R.id.btnExtractText)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnCapture.setOnClickListener { captureImage() }

        // OCR Action: Sends current frame's text to Preview
        btnExtractText.setOnClickListener {
            if (lastExtractedText.isNotBlank()) {
                // Inside btnExtractText.setOnClickListener
                val intent = Intent(this, AiSummaryActivity::class.java)
                intent.putExtra("EXTRA_OCR_TEXT", lastExtractedText) // Key must be EXACT
                startActivity(intent)
            } else {
                Toast.makeText(this, "No text detected yet", Toast.LENGTH_SHORT).show()
            }
        }

        btnDone.setOnClickListener {
            if (ScanSession.bitmaps.isEmpty()) {
                Toast.makeText(this, "Capture at least one page", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, PreviewActivity::class.java))
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. Capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // 3. Live OCR Analysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    lastExtractedText = visionText.text
                }
                .addOnCompleteListener {
                    imageProxy.close() // Close the frame to get the next one
                }
        }
    }

    private fun captureImage() {
        val file = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 1. Get the Uri of the saved file
                    val savedUri = Uri.fromFile(file)

                    // 2. Add the logic for the Chat Activity result
                    if (intent.getBooleanExtra("GET_IMAGE_ONLY", false)) {
                        // If we came from the AI Chat, return the image and close
                        val resultIntent = Intent()
                        resultIntent.putExtra("captured_image_uri", savedUri)
                        setResult(RESULT_OK, resultIntent)
                        finish() // This takes the user back to AiChatActivity automatically
                    } else {
                        // Normal behavior: Save to session for PDF scanning
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        ScanSession.bitmaps.add(bitmap)
                        Toast.makeText(this@CameraActivity, "Page captured (${ScanSession.bitmaps.size})", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}