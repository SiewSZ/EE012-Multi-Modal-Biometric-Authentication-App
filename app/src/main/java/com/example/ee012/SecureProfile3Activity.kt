package com.example.ee012

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SecureProfile3Activity : ComponentActivity() {

    // Views from your XML
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var palmOutline: ImageView
    private lateinit var captureButton: LinearLayout
    private lateinit var finishButton: LinearLayout // Added this
    private lateinit var backArrow: ImageView

    // Camera
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // Firebase
    private lateinit var storage: FirebaseStorage
    private var userUid: String? = null

    // State
    private var currentPhotoUri: Uri? = null

    // Permissions
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val TAG = "SecureProfile3Activity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.secure_profile_3)

        userUid = intent.getStringExtra("USER_UID")
        if (userUid == null) {
            Toast.makeText(this, "Error: User ID not found.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "User UID is null, finishing activity.")
            finish()
            return
        }
        storage = FirebaseStorage.getInstance()

        // Initialize Views using IDs from your layout
        cameraPreviewView = findViewById(R.id.cameraPreviewView)
        capturedImageView = findViewById(R.id.capturedImageView)
        palmOutline = findViewById(R.id.palm_outline)
        captureButton = findViewById(R.id.CaptureButton)
        finishButton = findViewById(R.id.FinishButton) // Initialize the finish button
        backArrow = findViewById(R.id.BackArrow3)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupInteractions()
        checkAndRequestCameraPermissions()
    }

    private fun setupInteractions() {
        backArrow.setOnClickListener { finish() }
        captureButton.setOnClickListener { takePhoto() }

        // **FIX:** Change the navigation to go to SecureProfile4Activity
        finishButton.setOnClickListener {
            // Create an intent for the new activity
            val intent = Intent(this, SecureProfile4Activity::class.java).apply {
                // Pass the user's UID to the next activity
                putExtra("USER_UID", userUid)
            }
            startActivity(intent)
            // Finish this activity so the user cannot navigate back to it
            finish()
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

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        captureButton.isClickable = false
        captureButton.alpha = 0.5f

        val photoFile = try { createImageFile() } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file", ex)
            resetButton()
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                runOnUiThread {
                    Toast.makeText(this@SecureProfile3Activity, "Capture Failed. Try Again.", Toast.LENGTH_LONG).show()
                    resetButton()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                currentPhotoUri = Uri.fromFile(photoFile)
                Log.d(TAG, "Photo capture succeeded: $currentPhotoUri")

                // Show the captured image and hide the preview
                capturedImageView.setImageURI(currentPhotoUri)
                capturedImageView.visibility = View.VISIBLE
                cameraPreviewView.visibility = View.GONE

                // Stop the camera
                ProcessCameraProvider.getInstance(this@SecureProfile3Activity).get().unbindAll()

                // Hide the capture button
                captureButton.visibility = View.GONE

                Toast.makeText(baseContext, "Palm Captured! Uploading...", Toast.LENGTH_LONG).show()
                uploadImageToStorage(userUid!!, currentPhotoUri!!)
            }
        })
    }

    private fun uploadImageToStorage(uid: String, imageUri: Uri) {
        val storageRef = storage.reference
        // This path is correct and matches your Firebase rules.
        val palmImageRef = storageRef.child("palm_images/${uid}-palm.jpg")

        palmImageRef.putFile(imageUri)
            .addOnSuccessListener {
                Log.d(TAG, "Palm image uploaded successfully.")
                Toast.makeText(baseContext, "Registration Complete! Please wait...", Toast.LENGTH_SHORT).show()

                // **FIX: Automatically navigate to SecureProfile4Activity after a delay**
                // Hide the button since we are navigating automatically
                finishButton.visibility = View.GONE

                // Add a small delay so the user can read the toast message
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, SecureProfile4Activity::class.java).apply {
                        // Pass the user's UID to the next activity
                        putExtra("USER_UID", uid)
                    }
                    startActivity(intent)
                    // Finish this activity so the user cannot navigate back to it
                    finish()
                }, 2000L) // 2-second delay before switching
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Palm image upload failed", e)
                Toast.makeText(baseContext, "Upload failed. Please try again.", Toast.LENGTH_LONG).show()
                resetToCaptureState()
            }
    }

    private fun resetToCaptureState() {
        currentPhotoUri = null
        runOnUiThread {
            capturedImageView.visibility = View.GONE
            cameraPreviewView.visibility = View.VISIBLE
            captureButton.visibility = View.VISIBLE
            finishButton.visibility = View.GONE // Hide finish button on retry
            resetButton()
            startCamera() // Restart the camera
        }
    }

    private fun resetButton() {
        captureButton.isClickable = true
        captureButton.alpha = 1.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(null) ?: filesDir
        return File.createTempFile("PALM_JPEG_${timeStamp}_", ".jpg", storageDir)
    }
}
