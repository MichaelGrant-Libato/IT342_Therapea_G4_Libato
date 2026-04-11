package com.therapea.app.ui.landing

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.therapea.app.R
import com.therapea.app.ui.auth.LoginActivity
import com.therapea.app.ui.auth.RegisterActivity // ✅ Pointing to the new Host Activity

class LandingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)

        // Navigate to Login
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Navigate to the Registration Flow (Host Activity)
        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            // ✅ Fix: Launches the single RegisterActivity instead of Step 1
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}