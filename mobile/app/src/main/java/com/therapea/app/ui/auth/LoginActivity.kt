package com.therapea.app.ui.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialException
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import com.therapea.app.network.*
import com.therapea.app.ui.home.*
import kotlinx.coroutines.*

class LoginActivity : FragmentActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var googleEmail: String? = null
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Session Check (Skip login if already active)
        val prefs = getSharedPreferences("TheraPea", Context.MODE_PRIVATE)
        if (prefs.contains("user_data")) {
            navigateDashboard(prefs.getString("role", "PATIENT") ?: "PATIENT")
            return
        }

        // 2. Custom Back Navigation (Block exit if in OTP flow)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val otpLayout = findViewById<LinearLayout>(R.id.layoutOtpFields)
                if (otpLayout.visibility == View.VISIBLE) {
                    showLoginUI()
                } else {
                    finish()
                }
            }
        })

        setContentView(R.layout.activity_login)

        // --- Standard Login Listeners ---
        findViewById<Button>(R.id.btnSignIn).setOnClickListener { handleManualLogin() }
        findViewById<Button>(R.id.btnGoogle).setOnClickListener { startGoogleFlow() }

        // --- OTP Flow Listeners ---
        findViewById<Button>(R.id.btnVerifyOtp).setOnClickListener { handleVerifyOtp() }
        findViewById<TextView>(R.id.tvBackToLogin).setOnClickListener { showLoginUI() }

        // --- Navigation to Registration ---
        findViewById<TextView>(R.id.tvSignUp).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // --- Password Visibility Toggle ---
        findViewById<ImageView>(R.id.ivTogglePassword).setOnClickListener {
            togglePasswordVisibility()
        }
    }

    private fun startGoogleFlow() {
        val cm = CredentialManager.create(this)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        activityScope.launch {
            try {
                val result = cm.getCredential(this@LoginActivity, request)
                val cred = GoogleIdTokenCredential.createFrom(result.credential.data)

                val response = withContext(Dispatchers.IO) {
                    ApiClient.authService.googleCheck(mapOf("idToken" to cred.idToken))
                }

                if (response.isSuccessful) {
                    saveAndNavigate(response.body()!!)
                } else if (response.code() == 404) {
                    val intent = Intent(this@LoginActivity, RegisterActivity::class.java).apply {
                        putExtra("email", cred.id)
                        putExtra("name", cred.displayName)
                        putExtra("isGoogle", true)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("GOOGLE_AUTH", "User cancelled or error: ${e.message}")
            }
        }
    }

    private fun sendOtp(email: String) {
        activityScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.authService.sendOtp(mapOf("email" to email))
                }
                if (res.isSuccessful && res.body()?.success == true) {
                    // Ensure these IDs match your activity_login.xml exactly!
                    findViewById<LinearLayout>(R.id.layoutLoginFields).visibility = View.GONE
                    findViewById<LinearLayout>(R.id.layoutOtpFields).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tvOtpSubtitle).text = "Enter the code sent to $email"

                    res.body()?.otp?.let {
                        Toast.makeText(this@LoginActivity, "Dev OTP: $it", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error sending OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleVerifyOtp() {
        val otp = findViewById<EditText>(R.id.etOtpCode).text.toString()
        if (otp.length < 6) return

        activityScope.launch {
            val verifyRes = withContext(Dispatchers.IO) {
                ApiClient.authService.verifyOtp(mapOf("email" to googleEmail!!, "otp" to otp))
            }

            if (verifyRes.isSuccessful && verifyRes.body()?.success == true) {
                val loginRes = withContext(Dispatchers.IO) {
                    ApiClient.authService.googleLogin(mapOf("email" to googleEmail!!))
                }
                if (loginRes.isSuccessful) saveAndNavigate(loginRes.body()!!)
            } else {
                Toast.makeText(this@LoginActivity, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleManualLogin() {
        val emailInput = findViewById<EditText>(R.id.etLoginEmail).text.toString().trim()
        val passwordInput = findViewById<EditText>(R.id.etLoginPassword).text.toString().trim()

        if (emailInput.isEmpty() || passwordInput.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        activityScope.launch {
            try {
                // Ensure LoginRequest uses 'password' field to match LoginDTO.java
                val response = withContext(Dispatchers.IO) {
                    ApiClient.authService.login(LoginRequest(emailInput, passwordInput))
                }

                if (response.isSuccessful) {
                    saveAndNavigate(response.body()!!)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AUTH_DEBUG", "Login Failed. Server said: $errorBody")

                    // Specific toast if it's an auth error (401)
                    if (response.code() == 401) {
                        Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@LoginActivity, "Login Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AUTH_DEBUG", "Connection error", e)
                Toast.makeText(this@LoginActivity, "Server Connection Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoginUI() {
        findViewById<LinearLayout>(R.id.layoutLoginFields).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.layoutOtpFields).visibility = View.GONE
    }

    private fun togglePasswordVisibility() {
        val etPass = findViewById<EditText>(R.id.etLoginPassword)
        val ivToggle = findViewById<ImageView>(R.id.ivTogglePassword)

        if (isPasswordVisible) {
            etPass.transformationMethod = PasswordTransformationMethod.getInstance()
            ivToggle.setImageResource(R.drawable.ic_eye_closed) // Ensure you have this drawable
            ivToggle.alpha = 0.4f
        } else {
            etPass.transformationMethod = HideReturnsTransformationMethod.getInstance()
            ivToggle.setImageResource(R.drawable.ic_eye_open) // Ensure you have this drawable
            ivToggle.alpha = 0.9f
        }
        isPasswordVisible = !isPasswordVisible
        etPass.setSelection(etPass.text.length)
    }

    private fun saveAndNavigate(user: DashboardDTO) {
        val prefs = getSharedPreferences("TheraPea", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_data", "active")
            putString("role", user.role)
            putString("name", user.fullName)
            putString("email", user.email)
            apply()
        }
        navigateDashboard(user.role!!)
    }

    private fun navigateDashboard(role: String) {
        val intent = if (role == "DOCTOR") Intent(this, DoctorHomeActivity::class.java)
        else Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}