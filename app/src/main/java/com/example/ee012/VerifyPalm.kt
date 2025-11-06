package com.example.ee012

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import android.Manifest
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
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VerifyPalm : AppCompatActivity() {

    // (All your existing variables for Views, Camera, etc. remain the same)
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var captureButton: LinearLayout
    private lateinit var backArrow: ImageView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private var finalPalmAccuracy: Double = 0.0
    private var faceScoreFromPreviousActivity: Double = 0.0
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var userUid: String? = null
    private var referencePalmFile: File? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val TAG = "VerifyPalm"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.secure_profile_3)

        // (All your existing onCreate logic remains the same)
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        userUid = intent.getStringExtra("USER_UID")
        faceScoreFromPreviousActivity = intent.getDoubleExtra("FACE_SCORE", 0.0)
        if (userUid == null) {
            Toast.makeText(this, "Error: User ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        cameraPreviewView = findViewById(R.id.cameraPreviewView)
        capturedImageView = findViewById(R.id.capturedImageView)
        captureButton = findViewById(R.id.CaptureButton)
        backArrow = findViewById(R.id.BackArrow3)
        progressBar = findViewById(R.id.progressBar)
        val childView = captureButton.getChildAt(0)
        statusText = if (childView is TextView) childView else {
            Toast.makeText(this, "Layout Error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        progressBar.visibility = View.GONE
        findViewById<LinearLayout>(R.id.FinishButton).visibility = View.GONE
        captureButton.isEnabled = false
        captureButton.alpha = 0.5f
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupInteractions()
        initializeBiometricPrompt()
        downloadReferencePalm()
    }

    // <<< THIS FUNCTION IS NOW MUCH SIMPLER >>>
    private fun downloadReferenceVoiceAndProceed() {
        if (userUid == null) {
            showError("Cannot proceed without User ID.")
            resetToCaptureState()
            return
        }

        showLoading("Preparing voice verification...")

        // 1. Download the reference .wav file.
        val voiceStorageRef = Firebase.storage.reference.child("voice_recordings/$userUid-voice.wav")
        val localVoiceFile = File.createTempFile("reference_voice", ".wav", cacheDir)

        voiceStorageRef.getFile(localVoiceFile).addOnSuccessListener {
            Log.d(TAG, "Reference .wav downloaded to: ${localVoiceFile.absolutePath}")
            hideLoading()

            // 2. Immediately start the next activity, passing the path to the downloaded .wav file.
            val intent = Intent(this@VerifyPalm, VoiceVerification::class.java).apply {
                putExtra("USER_UID", userUid)
                putExtra("REFERENCE_WAV_PATH", localVoiceFile.absolutePath) // Pass WAV path
                putExtra("FACE_SCORE", faceScoreFromPreviousActivity)
                putExtra("PALM_SCORE", finalPalmAccuracy)
            }
            startActivity(intent)
            finish()

        }.addOnFailureListener { exception ->
            hideLoading()
            Log.e(TAG, "Failed to download reference voice", exception)
            showError("Error: Could not download voice profile.")
            resetToCaptureState()
        }
    }

    // (All other functions in VerifyPalm.kt remain exactly the same as your original code)
    private fun downloadReferencePalm() {
        showLoading("Downloading Profile...")
        val storageRef = Firebase.storage.reference.child("palm_images/$userUid-palm.jpg")
        try {
            val localFile = File.createTempFile("reference_palm", ".jpg", cacheDir)
            storageRef.getFile(localFile).addOnSuccessListener {
                referencePalmFile = localFile
                Log.d(TAG, "Reference palm downloaded to: ${localFile.absolutePath}")
                hideLoading()
                runOnUiThread {
                    captureButton.isEnabled = true
                    captureButton.alpha = 1.0f
                    statusText.text = "Press to Begin"
                }
                checkAndRequestCameraPermissions()
            }.addOnFailureListener {
                showError("Error: Profile download failed.")
                runOnUiThread { statusText.text = "Download Failed" }
            }
        } catch (e: IOException) {
            showError("Could not create temp file for reference palm.")
        }
    }
    private fun setupInteractions() {
        backArrow.setOnClickListener { finish() }
        captureButton.setOnClickListener {
            if (referencePalmFile != null) {
                takePhoto()
            } else {
                Toast.makeText(this, "Please wait, profile not downloaded yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        showLoading("Capturing...")
        val photoFile = try { createImageFile() } catch (ex: IOException) {
            showError("Could not create image file.")
            resetToCaptureState()
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                showError("Capture failed. Try again.")
                resetToCaptureState()
            }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                ProcessCameraProvider.getInstance(this@VerifyPalm).get().unbindAll()
                try {
                    val rotatedBitmap = getCorrectlyOrientedBitmap(photoFile)
                    photoFile.outputStream().use { out ->
                        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    capturedImageView.setImageBitmap(rotatedBitmap)
                } catch(e: IOException) {
                    showError("Failed to save rotated image.")
                    resetToCaptureState()
                    return
                }
                capturedImageView.visibility = View.VISIBLE
                cameraPreviewView.visibility = View.GONE
                captureButton.visibility = View.GONE
                comparePalmsWithChaquopy(referencePalmFile!!, photoFile)
            }
        })
    }
    private fun comparePalmsWithChaquopy(refFile: File, newFile: File) {
        showLoading("Verifying Palm on Device...")
        Thread {
            try {
                val python = Python.getInstance()
                val chaquopyApi = python.getModule("chaquopy_api")
                Log.d("Chaquopy", "Calling Python with: ${refFile.absolutePath} and ${newFile.absolutePath}")
                val score = chaquopyApi.callAttr("compare_palms", newFile.absolutePath, refFile.absolutePath).toDouble()
                runOnUiThread { handleVerificationResult(score) }
            } catch (e: Exception) {
                Log.e("Chaquopy", "Error calling Python function", e)
                runOnUiThread {
                    showError("Verification failed due to a Python error.")
                    resetToCaptureState()
                }
            }
        }.start()
    }
    private fun rescaleScore(rawScore: Double): Double {
        val originalMin = 0.39
        val originalMax = 1.0
        val newMin = 0.80
        val newMax = 1.0
        if (rawScore < originalMin) return 0.0
        if (originalMin >= originalMax) return newMin
        val scaledScore = newMin + (rawScore - originalMin) * (newMax - newMin) / (originalMax - originalMin)
        return minOf(scaledScore, newMax)
    }
    private fun handleVerificationResult(rawScore: Double) {
        hideLoading()
        val newAcceptanceThreshold = 0.75
        val rescaledScore = rescaleScore(rawScore)
        finalPalmAccuracy = rescaledScore * 100
        val isVerified = rescaledScore >= newAcceptanceThreshold
        val message = if (isVerified) "Palm Verified! Score: ${"%.1f".format(finalPalmAccuracy)}%"
        else "Verification Failed. (Original Score: ${"%.1f".format(rawScore * 100)}%)"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (isVerified) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            resetToCaptureState()
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
            val preview = Preview.Builder().build().also { it.surfaceProvider = cameraPreviewView.surfaceProvider }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                showError("Could not start camera.")
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun showLoading(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
    private fun initializeBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Toast.makeText(applicationContext, "Fingerprint Verified!", Toast.LENGTH_SHORT).show()
                downloadReferenceVoiceAndProceed()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }
                resetToCaptureState()
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(applicationContext, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        })
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for your app")
            .setSubtitle("Confirm your identity to continue")
            .setNegativeButtonText("Cancel")
            .build()
    }
    private fun resetToCaptureState() {
        runOnUiThread {
            checkAndRequestCameraPermissions()
            cameraPreviewView.visibility = View.VISIBLE
            captureButton.visibility = View.VISIBLE
            captureButton.isEnabled = true
            captureButton.alpha = 1.0f
            statusText.text = "Press to Begin"
            capturedImageView.visibility = View.GONE
        }
    }
    private fun getCorrectlyOrientedBitmap(photoFile: File): Bitmap {
        val sourceBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        return rotateImage(sourceBitmap, 90f)
    }
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile("VERIFY_PALM_${timeStamp}_", ".jpg", cacheDir)
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
