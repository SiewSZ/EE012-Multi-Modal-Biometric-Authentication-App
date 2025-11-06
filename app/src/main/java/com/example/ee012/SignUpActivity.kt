package com.example.ee012

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.Firebase

class SignUpActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.secure_profile)

        auth = Firebase.auth
        setupSignUpScreen()
    }

    private fun setupSignUpScreen() {
        val emailEditText = findViewById<EditText>(R.id.Email)
        val passwordEditText = findViewById<EditText>(R.id.Password)
        val continueButton = findViewById<LinearLayout>(R.id.continueButton)

        continueButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            // Basic validation
            if (email.isEmpty() || password.isEmpty() || password.length < 6 || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please check your email and password (must be 6+ characters).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable the button to prevent multiple clicks
            continueButton.isClickable = false
            continueButton.alpha = 0.5f

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign up success
                        Log.d(TAG, "createUserWithEmail:success")
                        val user = auth.currentUser
                        // **FIX:** Navigate immediately on success, no Firestore save.
                        handleRegistrationSuccess(user)
                    } else {
                        // If sign up fails, display a message to the user.
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        Toast.makeText(
                            baseContext,
                            "Sign-up failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        // Re-enable the button so the user can try again
                        continueButton.isClickable = true
                        continueButton.alpha = 1.0f
                    }
                }
        }
    }

    private fun handleRegistrationSuccess(user: FirebaseUser?) {
        if (user == null) {
            // This should not happen if task was successful, but it's a good safety check.
            Toast.makeText(this, "Registration failed: User not found.", Toast.LENGTH_SHORT).show()
            findViewById<LinearLayout>(R.id.continueButton).apply {
                isClickable = true
                alpha = 1.0f
            }
            return
        }

        Toast.makeText(this, "Registration successful. Proceeding to verification.", Toast.LENGTH_SHORT).show()

        // Navigate to the next step
        val intent = Intent(this, SecureProfile2Activity::class.java).apply {
            putExtra("USER_UID", user.uid)
        }
        startActivity(intent)
        finish() // Close the sign-up screen
    }

    companion object {
        private const val TAG = "SignUpActivity"
    }
}
