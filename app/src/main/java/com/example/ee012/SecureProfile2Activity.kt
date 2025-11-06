package com.example.ee012

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.storage.FirebaseStorage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import android.content.Intent
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SecureProfile2Activity : ComponentActivity() {

    // Views from your XML
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var faceOutline: ImageView
    private lateinit var captureButton: LinearLayout
    private lateinit var backArrow: ImageView

    // Camera & ML
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var faceDetector: FaceDetector? = null

    // Firebase
    private lateinit var storage: FirebaseStorage // --- ADDITION 1: Firebase Storage instance ---
    private var userUid: String? = null // --- ADDITION 2: To store the user's UID ---

    // State Management Flags
    @Volatile private var isImageCaptured = false
    @Volatile private var isAnalyzingBlinks = false

    // Result
    private var currentPhotoUri: Uri? = null

    // Permissions
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val TAG = "SecureProfile2Activity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val BLINK_THRESHOLD = 0.4
        private const val AUTO_PROCEED_DELAY_MS = 1500L // 1.5 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.secure_profile_2)

        // --- ADDITION 3: Retrieve User UID and initialize Storage ---
        userUid = intent.getStringExtra("USER_UID")
        if (userUid == null) {
            Toast.makeText(this, "Error: User ID not found.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "User UID is null, finishing activity.")
            finish()
            return
        }
        storage = FirebaseStorage.getInstance()

        // Initialize Views using IDs from your XML
        cameraPreviewView = findViewById(R.id.cameraPreviewView)
        capturedImageView = findViewById(R.id.capturedImageView)
        faceOutline = findViewById(R.id.face_outline)
        captureButton = findViewById(R.id.CaptureButton)
        backArrow = findViewById(R.id.BackArrow2)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupInteractions()
        checkAndRequestCameraPermissions()
    }

    private fun setupInteractions() {
        backArrow.setOnClickListener { finish() }

        captureButton.setOnClickListener {
            if (!isImageCaptured) {
                isAnalyzingBlinks = true
                Toast.makeText(this, "Blink to capture", Toast.LENGTH_SHORT).show()
                it.isEnabled = false
                it.alpha = 0.5f
            }
        }
    }

    private fun checkAndRequestCameraPermissions() {
        requestMultiplePermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.all { it.value }) {
                    startCamera()
                } else {
                    Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

        if (REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            requestMultiplePermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = cameraPreviewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BlinkDetectionAnalyzer {
                        if (isAnalyzingBlinks) {
                            isAnalyzingBlinks = false
                            isImageCaptured = true
                            runOnUiThread { Toast.makeText(this, "Blink detected! Capturing...", Toast.LENGTH_SHORT).show() }
                            takePhoto()
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture, imageAnalyzer)
                runOnUiThread {
                    Toast.makeText(this, "Press the Capture button to begin", Toast.LENGTH_LONG).show()
                    captureButton.visibility = View.VISIBLE
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = try { createImageFile() } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file", ex); return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                isImageCaptured = false
                isAnalyzingBlinks = false
                runOnUiThread {
                    Toast.makeText(this@SecureProfile2Activity, "Capture Failed. Try Again.", Toast.LENGTH_LONG).show()
                    captureButton.isEnabled = true
                    captureButton.alpha = 1.0f
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                currentPhotoUri = Uri.fromFile(photoFile)
                Log.d(TAG, "Photo capture succeeded: $currentPhotoUri")
                ProcessCameraProvider.getInstance(this@SecureProfile2Activity).get().unbindAll()

                // Update UI
                capturedImageView.setImageURI(currentPhotoUri)
                capturedImageView.visibility = View.VISIBLE
                cameraPreviewView.visibility = View.GONE
                faceOutline.visibility = View.GONE
                captureButton.visibility = View.GONE

                Toast.makeText(baseContext, "Face Captured! Uploading...", Toast.LENGTH_LONG).show()

                // --- MODIFICATION: Upload image to Firebase Storage ---
                uploadImageToStorage(userUid!!, currentPhotoUri!!)
            }
        })
    }

    // --- ADDITION 4: Function to upload the image to Firebase Storage ---
    private fun uploadImageToStorage(uid: String, imageUri: Uri) {
        val storageRef = storage.reference
        // **FIX:** Change the path to the new "userID-face.jpg" format
        val faceImageRef = storageRef.child("face_images/${uid}-face.jpg")

        faceImageRef.putFile(imageUri)
            .addOnSuccessListener {
                Log.d(TAG, "Image uploaded successfully to Firebase Storage.")
                Toast.makeText(baseContext, "Face capture complete! Proceeding to palm capture...", Toast.LENGTH_SHORT).show()

                // Navigate to SecureProfile3Activity instead of finishing
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, SecureProfile3Activity::class.java).apply {
                        // Pass the user's UID to the next activity
                        putExtra("USER_UID", uid)
                    }
                    startActivity(intent)
                    finish() // Finish this activity so the user can't go back to it
                }, AUTO_PROCEED_DELAY_MS)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Image upload failed", e)
                Toast.makeText(baseContext, "Upload failed. Please try again.", Toast.LENGTH_LONG).show()
                resetToCaptureState()
            }
    }


    private fun resetToCaptureState() {
        isImageCaptured = false
        isAnalyzingBlinks = false
        currentPhotoUri = null

        // Restore UI on the main thread
        runOnUiThread {
            capturedImageView.visibility = View.GONE
            cameraPreviewView.visibility = View.VISIBLE
            faceOutline.visibility = View.VISIBLE
            captureButton.visibility = View.VISIBLE
            captureButton.isEnabled = true
            captureButton.alpha = 1.0f
            // Restart the camera in case it was unbound
            checkAndRequestCameraPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector?.close()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(null) ?: filesDir
        return File.createTempFile("FACE_JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private inner class BlinkDetectionAnalyzer(private val onBlink: () -> Unit) : ImageAnalysis.Analyzer {
        init {
            val highAccuracyOpts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            faceDetector = FaceDetection.getClient(highAccuracyOpts)
        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            if (!isAnalyzingBlinks) {
                imageProxy.close()
                return
            }
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                faceDetector?.process(image)
                    ?.addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            detectBlink(faces[0])
                        }
                    }
                    ?.addOnFailureListener { e -> Log.e(TAG, "Face detection failed", e) }
                    ?.addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }

        private fun detectBlink(face: Face) {
            val leftEyeOpenProb = face.leftEyeOpenProbability
            val rightEyeOpenProb = face.rightEyeOpenProbability
            if (leftEyeOpenProb == null || rightEyeOpenProb == null) return

            if (leftEyeOpenProb < BLINK_THRESHOLD && rightEyeOpenProb < BLINK_THRESHOLD) {
                onBlink()
            }
        }
    }
}
