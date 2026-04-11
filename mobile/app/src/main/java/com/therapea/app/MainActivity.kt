package com.therapea.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.therapea.app.ui.landing.LandingActivity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, LandingActivity::class.java)
        startActivity(intent)
        finish()
    }
}