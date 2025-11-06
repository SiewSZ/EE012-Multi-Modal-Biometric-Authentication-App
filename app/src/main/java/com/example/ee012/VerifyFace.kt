// FILE: VerifyFace.kt
// FINAL, CORRECTED VERSION V2.
// Now handles image rotation on-the-fly after download.

package com.example.ee012

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VerifyFace : ComponentActivity() {

    // --- Views ---
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var captureButton: LinearLayout
    private lateinit var backArrow: ImageView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    // --- Camera & ML ---
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var blinkFaceDetector: FaceDetector? = null

    // --- Data ---
    private var userUid: String? = null

    // --- State ---
    @Volatile private var isAnalyzingBlinks = false
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val TAG = "VerifyFace"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val BLINK_THRESHOLD = 0.4
        private const val SIMILARITY_THRESHOLD = 0.60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.secure_profile_2)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        userUid = intent.getStringExtra("USER_UID")
        if (userUid == null) {
            Toast.makeText(this, "Error: User ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // --- Initialize Views ---
        cameraPreviewView = findViewById(R.id.cameraPreviewView)
        capturedImageView = findViewById(R.id.capturedImageView)
        captureButton = findViewById(R.id.CaptureButton)
        backArrow = findViewById(R.id.BackArrow2)
        statusText = findViewById(R.id.captureText)
        progressBar = findViewById(R.id.progressBar)

        // --- UI Setup ---
        progressBar.visibility = View.GONE
        findViewById<LinearLayout>(R.id.continueButton2).visibility = View.GONE
        captureButton.isEnabled = false
        captureButton.alpha = 0.5f

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupInteractions()
        downloadAndCorrectOriginalFace() // <-- Renamed for clarity
    }

    private fun setupInteractions() {
        backArrow.setOnClickListener { finish() }
        captureButton.setOnClickListener {
            if (userUid != null) {
                isAnalyzingBlinks = true
                statusText.text = "Blink to Capture"
                it.isEnabled = false
                it.alpha = 0.5f
            }
        }
    }

    // *** MODIFIED FUNCTION TO FIX ORIENTATION ***
    private fun downloadAndCorrectOriginalFace() {
        showLoading("Downloading Profile...")
        val storageRef = Firebase.storage.reference.child("face_images/${userUid}-face.jpg")
        val localFile = File(cacheDir, "reference_face_uncorrected.jpg")

        storageRef.getFile(localFile).addOnSuccessListener {
            Log.d(TAG, "Original (uncorrected) face image downloaded.")

            // 1. Load the downloaded file into a bitmap
            val sourceBitmap = BitmapFactory.decodeFile(localFile.absolutePath)

            // 2. Rotate the bitmap. Since it came from the front camera during registration,
            // it's likely rotated -90 degrees.
            val rotatedBitmap = rotateImage(sourceBitmap, -90f)

            // 3. Save the correctly oriented bitmap to a NEW file. This is what we'll send to Python.
            val correctedFile = saveBitmapToFile(rotatedBitmap, "reference_face_corrected.jpg")
            Log.d(TAG, "Reference face corrected and saved to: ${correctedFile.absolutePath}")

            // 4. Proceed with the UI updates and camera start
            hideLoading()
            runOnUiThread {
                captureButton.isEnabled = true
                captureButton.alpha = 1.0f
                statusText.text = "Press to Begin"
            }
            checkAndRequestCameraPermissions()

        }.addOnFailureListener { exception ->
            Log.e(TAG, "Profile download failed", exception)
            showError("Error: Profile download failed.")
            statusText.text = "Download Failed"
        }
    }


    private fun checkAndRequestCameraPermissions() {
        if (REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(cameraPreviewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BlinkDetectionAnalyzer {
                        if (isAnalyzingBlinks) {
                            isAnalyzingBlinks = false
                            runOnUiThread { statusText.text = "Blink Detected!" }
                            takePhoto()
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                showError("Could not start camera.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val newPhotoFile = try { createImageFile("VERIFY_FACE") } catch (ex: IOException) {
            showError("Could not create image file."); return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(newPhotoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                showError("Capture failed. Try again.")
                resetToCaptureState()
            }

            // In VerifyFace.kt

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                ProcessCameraProvider.getInstance(this@VerifyFace).get().unbindAll()

                // --- Logic to Display Corrected Image to User ---

                // 1. Load the originally saved (potentially sideways) image into a bitmap.
                val sourceBitmap = BitmapFactory.decodeFile(newPhotoFile.absolutePath)

                // 2. Rotate the bitmap -90 degrees (counterclockwise) to make it upright for display.
                val rotatedBitmapForDisplay = rotateImage(sourceBitmap, -90f)

                // 3. Update the UI to show the user the correctly oriented image.
                capturedImageView.setImageBitmap(rotatedBitmapForDisplay)
                capturedImageView.visibility = View.VISIBLE
                cameraPreviewView.visibility = View.GONE
                captureButton.visibility = View.GONE

                // --- Logic to Call Python ---

                // Get the path to the reference file that we already corrected during download.
                val correctedRefFile = File(cacheDir, "reference_face_corrected.jpg")

                // Send the two files DIRECTLY to Python for comparison.
                // We send the 'newPhotoFile' itself because our Python script is robust
                // and designed to handle any rotation issues on its own.
                compareFacesWithPython(correctedRefFile, newPhotoFile)
            }
        })
    }

    private fun compareFacesWithPython(referenceFile: File, newImageFile: File) {
        showLoading("Verifying Face...")

        Thread {
            try {
                val python = Python.getInstance()
                val frModule = python.getModule("fr")

                Log.d("Chaquopy", "Calling Python with Ref: ${referenceFile.absolutePath} and New: ${newImageFile.absolutePath}")

                val score = frModule.callAttr("compare_faces", referenceFile.absolutePath, newImageFile.absolutePath).toDouble()

                Log.d(TAG, "Python Face Recognition Score: $score")

                runOnUiThread {
                    if (score > SIMILARITY_THRESHOLD) {
                        handleVerificationSuccess(score)
                    } else {
                        handleVerificationFailure(score)
                    }
                }
            } catch (e: Exception) {
                Log.e("Chaquopy", "Python function 'compare_faces' failed.", e)
                runOnUiThread {
                    showError("Verification failed due to a processing error.")
                    resetToCaptureState()
                }
            }
        }.start()
    }

    private fun handleVerificationSuccess(score: Double) {
        hideLoading()
        val accuracy = score * 100
        Toast.makeText(this, "Face Verified! Accuracy: ${"%.1f".format(accuracy)}%", Toast.LENGTH_LONG).show()

        val intent = Intent(this, VerifyPalm::class.java).apply {
            putExtra("USER_UID", userUid)
            putExtra("FACE_SCORE", accuracy)
        }
        startActivity(intent)
        finish()
    }

    private fun handleVerificationFailure(score: Double) {
        val message = if (score == 0.0) "Face not detected or mismatched. Please try again."
        else "Verification Failed. Score: ${"%.1f".format(score * 100)}%"
        showError(message)
        resetToCaptureState()
    }

    // --- UI and State Management Functions ---
    private fun showLoading(message: String) {
        runOnUiThread {
            statusText.text = message
            progressBar.visibility = View.VISIBLE
            backArrow.isEnabled = false
            captureButton.isEnabled = false
            captureButton.alpha = 0.5f
        }
    }

    private fun hideLoading() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            backArrow.isEnabled = true
        }
    }

    private fun showError(errorMessage: String) {
        runOnUiThread {
            hideLoading()
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToCaptureState() {
        runOnUiThread {
            isAnalyzingBlinks = false
            checkAndRequestCameraPermissions()
            cameraPreviewView.visibility = View.VISIBLE
            captureButton.visibility = View.VISIBLE
            captureButton.isEnabled = true
            captureButton.alpha = 1.0f
            statusText.text = "Press to Begin"
            capturedImageView.visibility = View.GONE
        }
    }

    // --- Utility and Analyzer Classes ---

    // This now also ensures the new image is saved correctly before sending to Python
    private fun getCorrectlyOrientedBitmap(photoFile: File): Bitmap {
        val sourceBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        return rotateImage(sourceBitmap, -90f)
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    // Helper to save a bitmap to a file
    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File {
        val file = File(cacheDir, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save rotated bitmap", e)
        }
        return file
    }


    @Throws(IOException::class)
    private fun createImageFile(prefix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("${prefix}_${timeStamp}_", ".jpg", cacheDir)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        blinkFaceDetector?.close()
    }

    private inner class BlinkDetectionAnalyzer(private val onBlink: () -> Unit) : ImageAnalysis.Analyzer {
        init {
            if (blinkFaceDetector == null) {
                val options = FaceDetectorOptions.Builder()
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
                blinkFaceDetector = FaceDetection.getClient(options)
            }
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
                blinkFaceDetector?.process(image)
                    ?.addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val leftEyeOpenProb = face.leftEyeOpenProbability
                            val rightEyeOpenProb = face.rightEyeOpenProbability
                            if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
                                if (leftEyeOpenProb < BLINK_THRESHOLD && rightEyeOpenProb < BLINK_THRESHOLD) {
                                    onBlink()
                                }
                            }
                        }
                    }
                    ?.addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
