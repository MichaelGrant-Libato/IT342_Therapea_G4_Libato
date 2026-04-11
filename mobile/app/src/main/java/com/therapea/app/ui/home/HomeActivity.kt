package com.therapea.app.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.therapea.app.R
import com.therapea.app.ui.auth.LoginActivity

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_patient)

        val prefs = getSharedPreferences("TheraPea", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "User")

        // Update the greeting
        findViewById<TextView>(R.id.tvPatientName).text = "Hello, $name"

        // Logout listener (attached to the profile icon/view we'll define in XML)
        findViewById<ImageView>(R.id.ivLogout).setOnClickListener {
            handleLogout()
        }
    }

    private fun handleLogout() {
        val prefs = getSharedPreferences("TheraPea", Context.MODE_PRIVATE)
        prefs.edit().clear().apply() // Wipes user_data, role, name, email

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}