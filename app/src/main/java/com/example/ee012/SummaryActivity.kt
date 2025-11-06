package com.example.ee012

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

// Import your other activities
import com.example.ee012.MainActivity
import com.example.ee012.MainPageActivity

class SummaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        // --- 1. Get all scores from the Intent ---
        val faceScore = intent.getDoubleExtra("FACE_SCORE", 0.0)
        val palmScore = intent.getDoubleExtra("PALM_SCORE", 0.0)
        val voiceScore = intent.getDoubleExtra("VOICE_SCORE", 0.0)
        val isVerified = intent.getBooleanExtra("IS_VERIFIED", false)

        // --- 2. Find all the Views ---
        val faceProgressBar = findViewById<ProgressBar>(R.id.faceProgressBar)
        val faceScoreText = findViewById<TextView>(R.id.faceScoreText)
        val palmProgressBar = findViewById<ProgressBar>(R.id.palmProgressBar)
        val palmScoreText = findViewById<TextView>(R.id.palmScoreText)
        val voiceProgressBar = findViewById<ProgressBar>(R.id.voiceProgressBar)
        val voiceScoreText = findViewById<TextView>(R.id.voiceScoreText)
        val proceedButton = findViewById<MaterialButton>(R.id.proceedButton)
        val titleText = findViewById<TextView>(R.id.titleText)

        // --- 3. Update the UI with the scores ---
        faceScoreText.text = "${faceScore.toInt()}%"
        faceProgressBar.progress = faceScore.toInt()
        palmScoreText.text = "${palmScore.toInt()}%"
        palmProgressBar.progress = palmScore.toInt()
        voiceScoreText.text = "${voiceScore.toInt()}%"
        voiceProgressBar.progress = voiceScore.toInt()

        // --- 4. Configure the Final Action ---
        if (isVerified) {
            // --- SUCCESS CASE ---
            titleText.text = "Access Granted"
            titleText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            proceedButton.text = "PROCEED"

            proceedButton.setOnClickListener {
                // On success, go to MainPageActivity
                val intent = Intent(this, MainPageActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        } else {
            // --- FAILURE CASE ---
            titleText.text = "Access Denied"
            titleText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            proceedButton.text = "TRY AGAIN"

            proceedButton.setOnClickListener {
                // On failure, go back to MainActivity and tell it to show an error.
                val intent = Intent(this, MainActivity::class.java).apply {
                    // This flag tells MainActivity that the process failed.
                    putExtra("AUTHENTICATION_FAILED", true)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        }
    }
}
