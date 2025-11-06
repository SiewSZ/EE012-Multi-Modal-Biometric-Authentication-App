// File: MainActivity.kt
// FINAL, CORRECTED CODE. Uses Firebase Auth and Firebase Storage only. No database.

package com.example.ee012

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.storage.storage

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        auth = Firebase.auth
        setupLoginScreen()
    }



    public override fun onStart() {
        super.onStart()
        // Automatic login is correctly disabled to allow manual login.
    }

    private fun setupLoginScreen() {
        val emailEditText = findViewById<EditText>(R.id.email)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signupTextView = findViewById<TextView>(R.id.signupText)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Logging in..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "signInWithEmail:success")
                        task.result.user?.let { user ->
                            // On successful auth, check STORAGE for registration files
                            handleLoginSuccess(user)
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.exception)
                        Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        loginButton.isEnabled = true
                        loginButton.text = "Login"
                    }
                }
        }

        signupTextView.paintFlags = signupTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        signupTextView.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Handles successful login by checking FIREBASE STORAGE for a registration file
     * before starting the verification activity flow.
     */
    private fun handleLoginSuccess(user: FirebaseUser) {
        val loginButton = findViewById<Button>(R.id.loginButton)

        // Get a reference to Firebase Storage
        val storage = Firebase.storage
        // Create a reference to a file we expect to exist after registration (e.g., the face image)
        val faceImageRef = storage.reference.child("face_images/${user.uid}-face.jpg")

        // Try to get the file's metadata. If this succeeds, the file exists and the user is registered.
        faceImageRef.metadata
            .addOnSuccessListener {
                // FILE EXISTS - USER IS REGISTERED
                Log.d(TAG, "User registration file found in Storage. Starting verification flow.")
                Toast.makeText(this, "Login successful. Starting verification.", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, VerifyFace::class.java).apply {
                    putExtra("USER_UID", user.uid)
                }
                startActivity(intent)
                finish() // Close the login screen
            }
            .addOnFailureListener { exception ->
                // FILE DOES NOT EXIST - USER IS NOT REGISTERED
                Log.w(TAG, "User registration file NOT found in Storage. Registration incomplete.", exception)
                Toast.makeText(this, "Login failed: Biometric registration not found. Please sign up to register.", Toast.LENGTH_LONG).show()

                // Log out the user as they cannot proceed
                auth.signOut()
                loginButton.isEnabled = true
                loginButton.text = "Login"
            }
    }

    companion object {
        private const val TAG = "MainActivityLogin"
    }
}
