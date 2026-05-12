package com.therapea.app.features.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.therapea.app.R
import com.therapea.app.features.home.DoctorHomeActivity
import com.therapea.app.features.home.HomeActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : Activity() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val API_BASE_URL = "http://10.0.2.2:8083"

    private var googleEmail = ""
    private var forgotEmail = ""
    private lateinit var loginFlipper: ViewFlipper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_login)
        loginFlipper = findViewById(R.id.loginFlipper)

        handleGoogleDeepLink(intent)

        findViewById<MaterialButton>(R.id.btnGoToRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        findViewById<MaterialButton>(R.id.btnCancelOtp).setOnClickListener {
            loginFlipper.displayedChild = 0
        }

        findViewById<MaterialButton>(R.id.btnSubmitLogin).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val password = findViewById<EditText>(R.id.etPassword).text.toString()
            val rememberMe = findViewById<CheckBox>(R.id.cbRememberMe).isChecked
            val tvError = findViewById<TextView>(R.id.tvLoginError)
            tvError.visibility = View.GONE

            val btnLogin = it as MaterialButton
            btnLogin.isEnabled = false
            btnLogin.text = "Signing In..."

            scope.launch(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply { put("email", email); put("password", password) }
                    val request = Request.Builder().url("$API_BASE_URL/api/auth/login")
                        .post(body.toString().toRequestBody(JSON)).build()

                    val response = client.newCall(request).execute()
                    val resData = JSONObject(response.body?.string() ?: "{}")

                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Sign In"
                        if (resData.optBoolean("requiresOtp", false)) {
                            googleEmail = resData.optString("email", email)
                            findViewById<TextView>(R.id.tvOtpSubtitle).text = "Check your email for the 6-digit verification code."
                            loginFlipper.displayedChild = 1
                        } else if (response.isSuccessful) {
                            val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
                            if (rememberMe) prefs.edit().putString("user_data", resData.toString()).apply()
                            else prefs.edit().putString("temp_session", resData.toString()).apply()

                            val intent = if (resData.optString("role", "PATIENT") == "DOCTOR") Intent(this@LoginActivity, DoctorHomeActivity::class.java)
                            else Intent(this@LoginActivity, HomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            tvError.text = resData.optString("error", "Login failed.")
                            tvError.visibility = View.VISIBLE
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Sign In"
                        tvError.text = "Connection error."
                        tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnGoogleLogin).setOnClickListener {
            // Save origin so the deep link router knows we started from Login
            getSharedPreferences("TheraPeaAuth", MODE_PRIVATE).edit().putString("auth_origin", "LOGIN").apply()
            scope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("$API_BASE_URL/api/auth/google-register-url?source=android").build()
                    val response = client.newCall(request).execute()
                    val data = JSONObject(response.body?.string() ?: "{}")
                    val authUrl = data.optString("url")
                    if (authUrl.isNotEmpty()) withContext(Dispatchers.Main) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))) }
                } catch (e: Exception) { }
            }
        }

        findViewById<MaterialButton>(R.id.btnSubmitOtp).setOnClickListener {
            val otp = findViewById<EditText>(R.id.etLoginOtp).text.toString()
            if (otp.length != 6) {
                Toast.makeText(this, "Please enter 6 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyOtpCall(otp)
        }

        findViewById<TextView>(R.id.btnResendOtp).setOnClickListener {
            sendOtpRequest(googleEmail)
            Toast.makeText(this, "Code sent!", Toast.LENGTH_SHORT).show()
        }

        setupForgotModal()
    }

    // REQUIRED FOR DEEP LINKS: Catches the intent if the app is already open in the background
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGoogleDeepLink(intent)
    }

    private fun handleGoogleDeepLink(incomingIntent: Intent?) {
        incomingIntent?.data?.let { link ->
            if (link.scheme == "therapea") {
                val email = link.getQueryParameter("email") ?: ""
                val type = link.getQueryParameter("type") ?: ""

                val prefs = getSharedPreferences("TheraPeaAuth", MODE_PRIVATE)
                val origin = prefs.getString("auth_origin", "LOGIN")
                prefs.edit().remove("auth_origin").apply()

                if (origin == "REGISTER") {
                    val regIntent = Intent(this, RegisterActivity::class.java)
                    if (type == "existing") {
                        regIntent.putExtra("google_error", "Account already existing")
                    } else if (type == "new" && email.isNotEmpty()) {
                        regIntent.putExtra("google_email", email)
                    }
                    startActivity(regIntent)
                    finish()
                } else {
                    // Origin is LOGIN
                    if (type == "new") {
                        Toast.makeText(this, "Account not found. Let's create one!", Toast.LENGTH_LONG).show()
                        val regIntent = Intent(this, RegisterActivity::class.java)
                        regIntent.putExtra("google_email", email)
                        startActivity(regIntent)
                        finish()
                    } else if (type == "existing" && email.isNotEmpty()) {
                        googleEmail = email
                        findViewById<TextView>(R.id.tvOtpSubtitle).text = "Verification code sent to $email"
                        loginFlipper.displayedChild = 1
                        sendOtpRequest(email)
                    }
                }
            }
        }
    }

    private fun sendOtpRequest(email: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", email); put("type", "LOGIN") }
                val request = Request.Builder().url("$API_BASE_URL/api/auth/send-otp").post(body.toString().toRequestBody(JSON)).build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {}
        }
    }

    private fun verifyOtpCall(otp: String) {
        val btnVerify = findViewById<MaterialButton>(R.id.btnSubmitOtp)
        btnVerify.isEnabled = false
        btnVerify.text = "Verifying..."

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", googleEmail); put("otp", otp) }
                val req = Request.Builder().url("$API_BASE_URL/api/auth/verify-otp").post(body.toString().toRequestBody(JSON)).build()
                if (client.newCall(req).execute().isSuccessful) {
                    val loginBody = JSONObject().apply { put("email", googleEmail) }
                    val loginReq = Request.Builder().url("$API_BASE_URL/api/auth/google-login").post(loginBody.toString().toRequestBody(JSON)).build()
                    val loginRes = client.newCall(loginReq).execute()
                    val resData = JSONObject(loginRes.body?.string() ?: "{}")

                    withContext(Dispatchers.Main) {
                        btnVerify.isEnabled = true
                        btnVerify.text = "Verify & Sign In"
                        if (loginRes.isSuccessful) {
                            getSharedPreferences("TheraPeaSession", MODE_PRIVATE).edit().putString("user_data", resData.toString()).apply()
                            val intent = if (resData.optString("role", "PATIENT") == "DOCTOR") Intent(this@LoginActivity, DoctorHomeActivity::class.java)
                            else Intent(this@LoginActivity, HomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                            loginFlipper.displayedChild = 0
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        btnVerify.isEnabled = true
                        btnVerify.text = "Verify & Sign In"
                        Toast.makeText(this@LoginActivity, "Invalid Code", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify & Sign In"
                }
            }
        }
    }

    private fun setupForgotModal() {
        val overlay = findViewById<FrameLayout>(R.id.modalOverlay)
        val flipper = findViewById<ViewFlipper>(R.id.forgotFlipper)
        val tvError = findViewById<TextView>(R.id.tvModalError)
        val tvTitle = findViewById<TextView>(R.id.tvModalTitle)

        findViewById<TextView>(R.id.btnForgotPassword).setOnClickListener {
            overlay.visibility = View.VISIBLE
            flipper.displayedChild = 0
            tvTitle.text = "Reset Password"
        }

        findViewById<ImageView>(R.id.btnCloseModal).setOnClickListener { overlay.visibility = View.GONE }

        findViewById<MaterialButton>(R.id.btnForgotSend).setOnClickListener {
            forgotEmail = findViewById<EditText>(R.id.etForgotEmail).text.toString()
            scope.launch(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply { put("email", forgotEmail); put("type", "FORGOT_PASSWORD") }
                    val request = Request.Builder().url("$API_BASE_URL/api/auth/send-otp").post(body.toString().toRequestBody(JSON)).build()
                    val response = client.newCall(request).execute()
                    val data = JSONObject(response.body?.string() ?: "{}")

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            if (data.optBoolean("requiresOtp", true)) {
                                flipper.displayedChild = 1
                                tvTitle.text = "Verify Email"
                            } else {
                                flipper.displayedChild = 2
                                tvTitle.text = "Create Password"
                            }
                        } else { tvError.text = "Error sending code"; tvError.visibility = View.VISIBLE }
                    }
                } catch (e: Exception) { }
            }
        }

        findViewById<MaterialButton>(R.id.btnForgotVerify).setOnClickListener {
            val otp = findViewById<EditText>(R.id.etForgotOtp).text.toString()
            scope.launch(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply { put("email", forgotEmail); put("otp", otp) }
                    val request = Request.Builder().url("$API_BASE_URL/api/auth/verify-otp").post(body.toString().toRequestBody(JSON)).build()
                    withContext(Dispatchers.Main) {
                        if (client.newCall(request).execute().isSuccessful) {
                            flipper.displayedChild = 2
                            tvTitle.text = "Create Password"
                        } else { tvError.text = "Invalid Code"; tvError.visibility = View.VISIBLE }
                    }
                } catch (e: Exception) {}
            }
        }

        findViewById<MaterialButton>(R.id.btnForgotSave).setOnClickListener {
            val pass = findViewById<EditText>(R.id.etForgotNewPass).text.toString()
            scope.launch(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply { put("email", forgotEmail); put("newPassword", pass) }
                    val request = Request.Builder().url("$API_BASE_URL/api/auth/reset-password").post(body.toString().toRequestBody(JSON)).build()
                    withContext(Dispatchers.Main) {
                        if (client.newCall(request).execute().isSuccessful) {
                            Toast.makeText(this@LoginActivity, "Password updated successfully!", Toast.LENGTH_LONG).show()
                            overlay.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}