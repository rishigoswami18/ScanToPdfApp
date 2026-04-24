package com.hrishipvt.scantopdf.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hrishipvt.scantopdf.databinding.ActivityCameraBinding
import com.hrishipvt.scantopdf.utils.ScanSession
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import java.io.File
import kotlin.math.sqrt

class CameraActivity : VoiceEnabledActivity(), SensorEventListener {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var textRecognizer: TextRecognizer
    private var lastExtractedText: String = ""
    private var isProcessingFrame = false

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var cameraControl: CameraControl? = null

    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var acceleration = 0f
    private var isShaking = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null && !intent.getBooleanExtra("GET_IMAGE_ONLY", false)) {
            ScanSession.clear()
        }

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        setupVoiceAssistant()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupSensors()
        setupListeners()
        updatePageCount()
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lightLevel = event.values[0]
                if (lightLevel < 10.0f) {
                    cameraControl?.enableTorch(true)
                } else if (lightLevel > 20.0f) {
                    cameraControl?.enableTorch(false)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                lastAcceleration = currentAcceleration
                currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = currentAcceleration - lastAcceleration
                acceleration = acceleration * 0.9f + delta

                isShaking = acceleration > 2.5f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun voiceCommandHelp(): String {
        return "Try saying capture, extract text, done, page count, back, or home."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        return when {
            normalizedCommand.contains("capture") || normalizedCommand.contains("take") || normalizedCommand.contains("photo") -> {
                speak("Capturing image.")
                binding.btnCapture.performClick()
                true
            }

            normalizedCommand.contains("done") || normalizedCommand.contains("finish") || normalizedCommand.contains("next") -> {
                speak("Opening preview.")
                binding.btnDone.performClick()
                true
            }

            normalizedCommand.contains("text") || normalizedCommand.contains("extract") || normalizedCommand.contains("summary") -> {
                speak("Preparing text extraction.")
                binding.btnExtractText.performClick()
                true
            }

            normalizedCommand.contains("page count") || normalizedCommand.contains("status") -> {
                speak("You have captured ${ScanSession.bitmaps.size} pages.")
                true
            }

            else -> false
        }
    }

    private fun setupListeners() {
        binding.btnCapture.setOnClickListener { captureImage() }

        binding.btnExtractText.setOnClickListener {
            if (lastExtractedText.isNotBlank()) {
                val intent = Intent(this, AiSummaryActivity::class.java)
                intent.putExtra("EXTRA_OCR_TEXT", lastExtractedText)
                startActivity(intent)
            } else {
                Toast.makeText(this, "No text detected", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDone.setOnClickListener {
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

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis)
                cameraControl = camera.cameraControl
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessingFrame) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                lastExtractedText = visionText.text
            }
            .addOnCompleteListener {
                isProcessingFrame = false
                imageProxy.close()
            }
    }

    private fun captureImage() {
        if (!::imageCapture.isInitialized) {
            Toast.makeText(this, "Camera is still starting", Toast.LENGTH_SHORT).show()
            return
        }

        if (isShaking) {
            Toast.makeText(this, "Hold steady to capture!", Toast.LENGTH_SHORT).show()
            speak("Please hold the device steady.")
            return
        }

        val file = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(file)

                    if (intent.getBooleanExtra("GET_IMAGE_ONLY", false)) {
                        val resultIntent = Intent()
                        resultIntent.putExtra("captured_image_uri", savedUri)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        ScanSession.bitmaps.add(bitmap)
                        updatePageCount()
                        Toast.makeText(this@CameraActivity, "Page captured", Toast.LENGTH_SHORT).show()
                        speak("Page captured. ${ScanSession.bitmaps.size} pages ready.")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updatePageCount() {
        binding.txtPageCount.text = "${ScanSession.bitmaps.size} Pages"
    }

    override fun onDestroy() {
        textRecognizer.close()
        super.onDestroy()
    }
}
