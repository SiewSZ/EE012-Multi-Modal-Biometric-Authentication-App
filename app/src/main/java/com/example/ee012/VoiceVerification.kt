package com.example.ee012

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import kotlin.random.Random

class VoiceVerification : AppCompatActivity() {

    // --- State and Managers ---
    private lateinit var voiceVerificationManager: VoiceVerificationManager
    private var rawPcmFile: File? = null
    private var newAudioFile: File? = null
    private var referenceWavPath: String? = null // This now holds the path to the reference .wav file
    private var pythonModule: PyObject? = null

    // --- Views ---
    private lateinit var recordButton: FrameLayout
    private lateinit var recordButtonText: TextView
    private lateinit var statusText: TextView
    private lateinit var numbersToSayText: TextView

    private var lastCalculatedVoiceScore: Float = 0.0f
    private var currentRandomSequence: String = ""

    // --- Permission Handling ---
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted. Please tap 'Record' again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Microphone permission is required for voice verification.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.secure_profile_4)

        initializeViewsAndAdaptUI()

        // --- Start Python ---
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
            pythonModule = Python.getInstance().getModule("voice_auth")
        } catch (e: Exception) {
            showError("Fatal Error: Could not initialize Python environment.")
            recordButton.isEnabled = false
            return
        }

        // --- Get the reference .wav path from the previous activity ---
        referenceWavPath = intent.getStringExtra("REFERENCE_WAV_PATH")
        if (referenceWavPath == null) {
            showError("Fatal Error: Reference voice recording not found.")
            recordButton.isEnabled = false
            return
        }

        voiceVerificationManager = VoiceVerificationManager(this)
        setupButton()
    }

    private fun initializeViewsAndAdaptUI() {
        statusText = findViewById(R.id.instructionText)
        recordButton = findViewById(R.id.voiceActionButton)
        recordButtonText = findViewById(R.id.voiceActionButtonText)
        numbersToSayText = findViewById(R.id.numbersToSayText)

        findViewById<ImageView>(R.id.BackArrow4).visibility = View.GONE
        findViewById<FrameLayout>(R.id.recordFinalVoiceButton).visibility = View.GONE

        generateNewSequence()

        statusText.text = "Press the button and say the numbers below for verification."
        recordButtonText.text = "Record"
    }

    private fun generateNewSequence() {
        currentRandomSequence = (1..4).map { Random.nextInt(0, 10) }.joinToString("")
        numbersToSayText.text = currentRandomSequence.chunked(1).joinToString(" - ")
    }

    private fun setupButton() {
        recordButton.setOnClickListener {
            if (voiceVerificationManager.isRecording()) {
                stopRecordingAndVerify()
            } else {
                checkPermissionAndStartRecording()
            }
        }
    }

    private fun checkPermissionAndStartRecording() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                rawPcmFile = voiceVerificationManager.startRecording()
                if (rawPcmFile != null) {
                    statusText.text = "Recording... Speak the numbers now."
                    recordButtonText.text = "Stop and Verify"
                } else {
                    showError("Failed to initialize recorder.")
                }
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun stopRecordingAndVerify() {
        recordButton.isEnabled = false
        recordButtonText.text = "Verifying..."
        statusText.text = "Processing audio..."

        val tempRawFile = rawPcmFile
        if (tempRawFile == null) {
            showError("Error: Raw audio file was not created.")
            resetState()
            return
        }

        newAudioFile = voiceVerificationManager.stopRecording(tempRawFile)
        val audioToVerify = newAudioFile
        val refWavPath = referenceWavPath // Use the correct variable

        if (audioToVerify == null || refWavPath == null) {
            showError("Error: Missing audio file for verification.")
            resetState()
            return
        }

        // --- Run Python "on-the-fly" verification on a background thread ---
        Thread {
            try {
                // Call the single, unified Python function
                val score = pythonModule?.callAttr(
                    "verify_voice_on_the_fly",
                    refWavPath, // Pass the reference .wav path
                    audioToVerify.absolutePath // Pass the new .wav path
                )?.toFloat() ?: 0.0f

                lastCalculatedVoiceScore = score
                val isVerified = score > 0.75 // A good starting threshold for GMM scores

                runOnUiThread {
                    val scorePercentage = score * 100
                    val message = "Voice Uniqueness Score: ${String.format("%.1f", scorePercentage)}%"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    goToSummaryScreen(isVerified)
                }

            } catch (e: Exception) {
                Log.e("VoiceVerification", "Python script execution failed", e)
                runOnUiThread {
                    showError("Python script execution failed: ${e.message}")
                    goToSummaryScreen(false)
                }
            }
        }.start()
    }

    private fun resetState() {
        generateNewSequence()
        statusText.text = "Press the button and say the numbers below for verification."
        recordButtonText.text = "Record"
        recordButton.isEnabled = true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        resetState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceVerificationManager.isInitialized) {
            voiceVerificationManager.release()
        }
    }

    private fun goToSummaryScreen(isVerified: Boolean) {
        val faceScore = intent.getDoubleExtra("FACE_SCORE", 0.0)
        val palmScore = intent.getDoubleExtra("PALM_SCORE", 0.0)
        val voiceScorePercentage = (lastCalculatedVoiceScore * 100).toDouble()

        val intent = Intent(this, SummaryActivity::class.java).apply {
            putExtra("FACE_SCORE", faceScore)
            putExtra("PALM_SCORE", palmScore)
            putExtra("VOICE_SCORE", voiceScorePercentage)
            putExtra("IS_VERIFIED", isVerified)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
