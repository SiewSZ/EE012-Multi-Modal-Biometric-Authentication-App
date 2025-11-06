// File: SecureProfile4Activity.kt

package com.example.ee012

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.util.Locale

class SecureProfile4Activity : ComponentActivity(), RecognitionListener {

    // Views
    private lateinit var backArrow: ImageView
    private lateinit var numbersToSayText: TextView
    private lateinit var instructionText: TextView
    private lateinit var validationButton: FrameLayout
    private lateinit var validationButtonText: TextView
    private lateinit var recordFinalVoiceButton: FrameLayout
    private lateinit var recordFinalVoiceButtonText: TextView

    // State
    private var userUid: String? = null
    private lateinit var numbersToRead: List<String>
    private var isListeningForValidation = false

    // Functional Modules
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var voiceUploadManager: VoiceUploadManager

    // Permission launcher for handling microphone permission requests.
    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // This block will be executed after the user grants permission.
                // We'll primarily use this for the initial validation step.
                // The user might need to tap the button again.
                Toast.makeText(this, "Permission granted. Please tap the button again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Microphone permission is required to continue.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.secure_profile_4)

        userUid = intent.getStringExtra("USER_UID")
        if (userUid == null) {
            Toast.makeText(this, "Fatal Error: User ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Correctly initialize VoiceUploadManager with just the context.
        voiceUploadManager = VoiceUploadManager(this)

        initializeViews()
        setupInteractions()
        initializeSpeechRecognizer()
        resetToInitialState()
    }

    private fun initializeViews() {
        backArrow = findViewById(R.id.BackArrow4)
        numbersToSayText = findViewById(R.id.numbersToSayText)
        instructionText = findViewById(R.id.instructionText)
        validationButton = findViewById(R.id.voiceActionButton)
        validationButtonText = findViewById(R.id.voiceActionButtonText)
        recordFinalVoiceButton = findViewById(R.id.recordFinalVoiceButton)
        recordFinalVoiceButtonText = findViewById(R.id.recordFinalVoiceButtonText)
    }

    private fun setupInteractions() {
        backArrow.setOnClickListener { finish() }

        // Handles the initial speech-to-text validation.
        validationButton.setOnClickListener {
            if (isListeningForValidation) {
                speechRecognizer?.stopListening()
            } else {
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                        startSpeechValidation()
                    }
                    else -> {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }

        // Handles the final voice pass-phrase recording and upload.
        recordFinalVoiceButton.setOnClickListener {
            if (voiceUploadManager.isRecording()) {
                // --- STOPPING RECORDING ---
                recordFinalVoiceButton.isEnabled = false
                recordFinalVoiceButtonText.text = "Processing..."
                instructionText.text = "Finalizing recording..."

                voiceUploadManager.stopRecordingAndUpload(
                    userUid = userUid!!,
                    onSuccess = {
                        Toast.makeText(this, "Registration complete!", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }, 2000L)
                    },
                    onFailure = { errorMessage ->
                        showUploadFailedDialog(errorMessage)
                    }
                )
            } else {
                // --- STARTING RECORDING ---
                speechRecognizer?.destroy() // Ensure speech recognizer is off
                speechRecognizer = null

                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                        // Permission is available, start the final recording.
                        if (voiceUploadManager.startRecording()) {
                            instructionText.text = "Now recording your voice pass-phrase..."
                            recordFinalVoiceButtonText.text = "Recording... Tap to Stop"
                        }
                    }
                    else -> {
                        // Request permission. The user will have to tap the button again.
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }

    private fun startSpeechValidation() {
        if (speechRecognizer == null) initializeSpeechRecognizer()
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Please say the numbers")
        }
        speechRecognizer?.startListening(speechIntent)
    }

    private fun onValidationSuccess() {
        instructionText.text = "Success! Now, use the green button to record your voice pass-phrase."
        validationButton.visibility = View.GONE
        recordFinalVoiceButton.visibility = View.VISIBLE
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun showUploadFailedDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Upload Failed")
            .setMessage(message)
            .setPositiveButton("Try Again") { dialog, _ ->
                dialog.dismiss()
                recordFinalVoiceButton.isEnabled = true
                recordFinalVoiceButtonText.text = "Record Voice"
                instructionText.text = "Failed to process audio. Please try recording your pass-phrase again."
            }
            .setCancelable(false)
            .show()
    }

    private fun resetToInitialState() {
        instructionText.text = "First, say all the numbers to validate your voice."
        numbersToRead = (0..9).map { it.toString() }
        numbersToSayText.text = numbersToRead.joinToString(" - ")
        numbersToSayText.visibility = View.VISIBLE
        isListeningForValidation = false
        validationButton.visibility = View.VISIBLE
        validationButton.isEnabled = true
        validationButtonText.text = "Push to Talk"
        recordFinalVoiceButton.visibility = View.GONE
        recordFinalVoiceButton.isEnabled = true
        recordFinalVoiceButtonText.text = "Record Voice"
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available.", Toast.LENGTH_LONG).show()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@SecureProfile4Activity)
        }
    }

    private fun showValidationFailedDialog(heardText: String, missing: List<String>) {
        val missingNumbersText = missing.joinToString(", ")
        val message = "Here are the results of the microphone test:\n\n" +
                "What the microphone heard:\n\"$heardText\"\n\n" +
                "Numbers that were missed:\n$missingNumbersText"

        AlertDialog.Builder(this)
            .setTitle("Microphone Test Results")
            .setMessage(message)
            .setPositiveButton("Try Again") { dialog, _ ->
                dialog.dismiss()
                resetToInitialState()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        voiceUploadManager.release() // Release the manager to stop any recording.
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        isListeningForValidation = true
        validationButtonText.text = "Listening..."
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListeningForValidation = false
        validationButtonText.text = "Processing..."
        validationButton.isEnabled = false
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No numbers heard, please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected, please try again."
            else -> "Recognizer error, please try again."
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        resetToInitialState()
    }

    override fun onResults(results: Bundle?) {
        validationButton.isEnabled = true
        val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""

        if (spokenText.isNotEmpty()) {
            val numberWords = mapOf("zero" to "0", "one" to "1", "two" to "2", "three" to "3",
                "four" to "4", "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9")
            var processedText = spokenText.lowercase(Locale.ROOT)
            numberWords.forEach { (word, digit) -> processedText = processedText.replace(word, digit) }
            val extractedDigits = processedText.filter { it.isDigit() }
            val missingNumbers = numbersToRead.filterNot { extractedDigits.contains(it) }

            if (missingNumbers.isEmpty()) {
                onValidationSuccess()
            } else {
                showValidationFailedDialog(spokenText, missingNumbers)
            }
        } else {
            Toast.makeText(this, "The microphone did not hear any speech. Please try again.", Toast.LENGTH_LONG).show()
            resetToInitialState()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
