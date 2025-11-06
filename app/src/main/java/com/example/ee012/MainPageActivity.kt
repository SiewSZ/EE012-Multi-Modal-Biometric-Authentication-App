package com.example.ee012

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_page) // This loads your successful login page.

        // EXAMPLE: If you have a logout button in main_page.xml
        // val logoutButton = findViewById<Button>(R.id.logout_button_id)
        // logoutButton.setOnClickListener {
        //     val intent = Intent(this, MainActivity::class.java)
        //     intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        //     startActivity(intent)
        //     finish()
        // }
    }
}
