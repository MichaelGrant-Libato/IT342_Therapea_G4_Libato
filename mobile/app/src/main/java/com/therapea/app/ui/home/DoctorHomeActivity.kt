package com.therapea.app.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.therapea.app.R
import com.therapea.app.ui.auth.LoginActivity

class DoctorHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Switch from manual TextView to your new XML
        setContentView(R.layout.activity_home_doctor)

        val prefs = getSharedPreferences("TheraPea", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "Doctor")

        // Update greeting
        findViewById<TextView>(R.id.tvDoctorName).text = "Hello, Dr. $name"

        // Logout logic
        findViewById<ImageView>(R.id.ivLogout).setOnClickListener {
            val editor = prefs.edit()
            editor.clear()
            editor.apply()

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}