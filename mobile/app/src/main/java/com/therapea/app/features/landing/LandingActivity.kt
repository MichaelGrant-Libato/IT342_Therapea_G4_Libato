package com.therapea.app.features.landing

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.therapea.app.R
import com.therapea.app.features.assessment.AssessmentActivity
import com.therapea.app.features.auth.LoginActivity
import com.therapea.app.features.auth.ReferenceActivity
import com.therapea.app.features.auth.RegisterActivity

class LandingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnReference).setOnClickListener {
            startActivity(Intent(this, ReferenceActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnTriage).setOnClickListener {
            startActivity(Intent(this, AssessmentActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnHowItWorks).setOnClickListener {
            Toast.makeText(this, "Scroll to How It Works", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnCtaGetStarted).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}