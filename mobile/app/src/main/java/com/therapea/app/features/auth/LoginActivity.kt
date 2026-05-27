// app/src/main/java/com/therapea/app/features/auth/LoginActivity.kt
package com.therapea.app.features.auth

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.therapea.app.BuildConfig
import com.therapea.app.R
import com.therapea.app.features.admin.AdminApprovalsActivity
import com.therapea.app.features.home.DoctorHomeActivity
import com.therapea.app.features.home.HomeActivity
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : Activity() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val apiBaseUrl = BuildConfig.BASE_URL
        .removeSuffix("/api/")
        .removeSuffix("/api")
        .trimEnd('/') + "/"
    private val googleRequestCode = 7100
    private var loginEmailForOtp = ""
    private var forgotEmail = ""

    private lateinit var loginFlipper: ViewFlipper
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var tvLoginError: TextView
    private lateinit var tvModalError: TextView
    private lateinit var tvModalTitle: TextView
    private lateinit var modalOverlay: FrameLayout
    private lateinit var forgotFlipper: ViewFlipper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_login)

        bindViews()

        if (redirectExistingSession()) return

        setupGoogleLogin()
        setupLoginActions()
        setupOtpActions()
        setupForgotModal()
    }

    private fun bindViews() {
        loginFlipper = findViewById(R.id.loginFlipper)
        tvLoginError = findViewById(R.id.tvLoginError)
        modalOverlay = findViewById(R.id.modalOverlay)
        forgotFlipper = findViewById(R.id.forgotFlipper)
        tvModalError = findViewById(R.id.tvModalError)
        tvModalTitle = findViewById(R.id.tvModalTitle)
    }

    private fun redirectExistingSession(): Boolean {
        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val raw = prefs.getString("user_data", null)
            ?: prefs.getString("temp_session", null)
            ?: prefs.getString("remembered_user_data", null)

        if (raw.isNullOrBlank()) return false

        return try {
            goToHome(JSONObject(raw))
            true
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            false
        }
    }

    private fun setupGoogleLogin() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(com.therapea.app.BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<MaterialButton>(R.id.btnGoogleLogin).setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, googleRequestCode)
            }
        }
    }

    @Deprecated("Used because this Activity intentionally avoids AppCompat/activity-result APIs.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == googleRequestCode) {
            if (resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleGoogleSignInResult(task)
            } else {
                showInfoDialog("Google Sign-In Cancelled", "No Google account was selected.")
            }
        }
    }

    private fun setupLoginActions() {
        findViewById<MaterialButton>(R.id.btnGoToRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        findViewById<MaterialButton>(R.id.btnSubmitLogin).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val password = findViewById<EditText>(R.id.etPassword).text.toString()
            val rememberMe = findViewById<CheckBox>(R.id.cbRememberMe).isChecked

            hideLoginError()

            if (email.isBlank()) {
                showLoginError("Please enter your email address.")
                return@setOnClickListener
            }

            if (password.isBlank()) {
                showLoginError("Please enter your password.")
                return@setOnClickListener
            }

            submitLogin(email, password, rememberMe, it as MaterialButton)
        }
    }

    private fun setupOtpActions() {
        findViewById<MaterialButton>(R.id.btnCancelOtp).setOnClickListener {
            loginFlipper.displayedChild = 0
            findViewById<EditText>(R.id.etLoginOtp).setText("")
            hideLoginError()
        }

        findViewById<MaterialButton>(R.id.btnSubmitOtp).setOnClickListener {
            val otp = findViewById<EditText>(R.id.etLoginOtp).text.toString().trim()

            if (otp.length != 6) {
                showLoginError("Please enter the 6-digit verification code.")
                return@setOnClickListener
            }

            verifyOtpCall(otp)
        }

        findViewById<TextView>(R.id.btnResendOtp).setOnClickListener {
            if (loginEmailForOtp.isBlank()) {
                showLoginError("We could not find the email for this verification request.")
                return@setOnClickListener
            }

            sendOtpRequest(loginEmailForOtp, "LOGIN") {
                showInfoDialog("Code Sent", "A new verification code was sent to your email.")
            }
        }
    }

    private fun submitLogin(
        email: String,
        password: String,
        rememberMe: Boolean,
        button: MaterialButton
    ) {
        button.isEnabled = false
        button.text = "Signing In..."

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("email", email)
                    .put("password", password)

                val request = Request.Builder()
                    .url("${apiBaseUrl}api/auth/login")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val data = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })

                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Sign In"

                    when {
                        data.optBoolean("requiresOtp", false) -> {
                            loginEmailForOtp = data.optString("email", email)
                            findViewById<TextView>(R.id.tvOtpSubtitle).text =
                                "Check your email for the 6-digit verification code."
                            loginFlipper.displayedChild = 1
                            hideLoginError()
                        }

                        response.isSuccessful -> {
                            saveSession(data, rememberMe)
                            goToHome(data)
                        }

                        else -> showLoginError(parseError(data))
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Sign In"
                    showLoginError("Connection error. Please check your server and internet connection.")
                }
            }
        }
    }

    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken.isNullOrBlank()) {
                showInfoDialog("Google Sign-In Failed", "Google did not return a valid identity token.")
                return
            }

            showInlineAuthState("Authenticating with server...")

            scope.launch(Dispatchers.IO) {
                try {
                    val body = JSONObject().put("idToken", idToken)

                    val request = Request.Builder()
                        .url("${apiBaseUrl}api/auth/google-native")
                        .post(body.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val data = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })

                    withContext(Dispatchers.Main) {
                        hideLoginError()

                        if (response.isSuccessful) {
                            if (data.optBoolean("requiresOtp", false)) {
                                loginEmailForOtp = data.optString("email", account.email ?: "")
                                findViewById<TextView>(R.id.tvOtpSubtitle).text =
                                    "Check your email for the 6-digit verification code."
                                loginFlipper.displayedChild = 1
                            } else {
                                saveSession(data, rememberMe = true)
                                goToHome(data)
                            }
                        } else {
                            showInfoDialog("Authentication Failed", parseError(data))
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        showInfoDialog(
                            "Server Connection Error",
                            "We could not authenticate with the server. Please try again."
                        )
                    }
                }
            }
        } catch (e: ApiException) {
            showInfoDialog("Google Sign-In Failed", "Google returned error code: ${e.statusCode}")
        }
    }

    private fun sendOtpRequest(email: String, type: String, onComplete: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("email", email)
                    .put("type", type)

                val request = Request.Builder()
                    .url("${apiBaseUrl}api/auth/send-otp")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val data = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        onComplete?.invoke()
                    } else {
                        val msg = parseError(data)
                        if (type == "LOGIN") showLoginError(msg) else showModalError(msg)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = "Could not send verification code. Please try again."
                    if (type == "LOGIN") showLoginError(msg) else showModalError(msg)
                }
            }
        }
    }

    private fun verifyOtpCall(otp: String) {
        val btnVerify = findViewById<MaterialButton>(R.id.btnSubmitOtp)
        val rememberMe = findViewById<CheckBox>(R.id.cbRememberMe).isChecked

        btnVerify.isEnabled = false
        btnVerify.text = "Verifying..."
        hideLoginError()

        scope.launch(Dispatchers.IO) {
            try {
                val verifyBody = JSONObject()
                    .put("email", loginEmailForOtp)
                    .put("otp", otp)

                val verifyRequest = Request.Builder()
                    .url("${apiBaseUrl}api/auth/verify-otp")
                    .post(verifyBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val verifyResponse = client.newCall(verifyRequest).execute()
                val verifyData = JSONObject(verifyResponse.body?.string().orEmpty().ifBlank { "{}" })

                if (!verifyResponse.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        btnVerify.isEnabled = true
                        btnVerify.text = "Verify & Sign In"
                        showLoginError(parseError(verifyData, "Invalid verification code."))
                    }
                    return@launch
                }

                val sessionBody = JSONObject().put("email", loginEmailForOtp)

                val sessionRequest = Request.Builder()
                    .url("${apiBaseUrl}api/auth/google-login")
                    .post(sessionBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val sessionResponse = client.newCall(sessionRequest).execute()
                val sessionData = JSONObject(sessionResponse.body?.string().orEmpty().ifBlank { "{}" })

                withContext(Dispatchers.Main) {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify & Sign In"

                    if (sessionResponse.isSuccessful) {
                        saveSession(sessionData, rememberMe)
                        goToHome(sessionData)
                    } else {
                        showLoginError(parseError(sessionData, "Could not create login session."))
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify & Sign In"
                    showLoginError("Connection error while verifying code.")
                }
            }
        }
    }

    private fun setupForgotModal() {
        findViewById<TextView>(R.id.btnForgotPassword).setOnClickListener {
            modalOverlay.visibility = View.VISIBLE
            forgotFlipper.displayedChild = 0
            tvModalTitle.text = "Reset Password"
            clearModalError()
        }

        findViewById<ImageView>(R.id.btnCloseModal).setOnClickListener {
            modalOverlay.visibility = View.GONE
            clearModalError()
        }

        findViewById<MaterialButton>(R.id.btnForgotSend).setOnClickListener {
            forgotEmail = findViewById<EditText>(R.id.etForgotEmail).text.toString().trim()

            if (forgotEmail.isBlank()) {
                showModalError("Please enter your email address.")
                return@setOnClickListener
            }

            sendForgotOtp(it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.btnForgotVerify).setOnClickListener {
            val otp = findViewById<EditText>(R.id.etForgotOtp).text.toString().trim()

            if (otp.length != 6) {
                showModalError("Please enter the 6-digit code.")
                return@setOnClickListener
            }

            verifyForgotOtp(otp, it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.btnForgotSave).setOnClickListener {
            val pass = findViewById<EditText>(R.id.etForgotNewPass).text.toString()
            val confirm = findViewById<EditText>(R.id.etForgotConfirmPass).text.toString()

            if (pass.length < 6) {
                showModalError("Password must be at least 6 characters.")
                return@setOnClickListener
            }

            if (pass != confirm) {
                showModalError("Passwords do not match.")
                return@setOnClickListener
            }

            resetPassword(pass, it as MaterialButton)
        }
    }

    private fun sendForgotOtp(button: MaterialButton) {
        clearModalError()
        button.isEnabled = false
        button.text = "Sending..."

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("email", forgotEmail)
                    .put("type", "FORGOT_PASSWORD")

                val request = Request.Builder()
                    .url("${apiBaseUrl}api/auth/send-otp")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val data = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })

                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Proceed"

                    if (response.isSuccessful) {
                        if (data.optBoolean("requiresOtp", true)) {
                            forgotFlipper.displayedChild = 1
                            tvModalTitle.text = "Verify Email"
                        } else {
                            forgotFlipper.displayedChild = 2
                            tvModalTitle.text = "Create Password"
                        }
                    } else {
                        showModalError(parseError(data, "Error sending code."))
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Proceed"
                    showModalError("Connection error. Please try again.")
                }
            }
        }
    }

    private fun verifyForgotOtp(otp: String, button: MaterialButton) {
        clearModalError()
        button.isEnabled = false
        button.text = "Verifying..."

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("email", forgotEmail)
                    .put("otp", otp)

                val request = Request.Builder()
                    .url("${apiBaseUrl}api/auth/verify-otp")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val data = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })

                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Verify Code"

                    if (response.isSuccessful) {
                        forgotFlipper.displayedChild = 2
                        tvModalTitle.text = "Create Password"
                    } else {
                        showModalError(parseError(data, "Invalid code."))
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Verify Code"
                    showModalError("Connection error while verifying code.")
                }
            }
        }
    }

    private fun resetPassword(newPassword: String, button: MaterialButton) {
        clearModalError()
        button.isEnabled = false
        button.text = "Updating..."

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("email", forgotEmail)
                    .put("newPassword", newPassword)

                val request = Request.Builder()
                    .url("${apiBaseUrl}api/auth/reset-password")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val data = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })

                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Update Password"

                    if (response.isSuccessful) {
                        modalOverlay.visibility = View.GONE
                        showInfoDialog("Password Updated", "You can now sign in with your new password.")
                    } else {
                        showModalError(parseError(data, "Could not update password."))
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.text = "Update Password"
                    showModalError("Connection error. Please try again.")
                }
            }
        }
    }

    private fun saveSession(data: JSONObject, rememberMe: Boolean) {
        val normalizedData = JSONObject(data.toString())
        val role = normalizedData.optString("role", "PATIENT").uppercase()
        normalizedData.put("role", role)

        val sessionJson = normalizedData.toString()

        getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
            .edit()
            .putString("user_data", sessionJson)
            .putString(if (rememberMe) "remembered_user_data" else "temp_session", sessionJson)
            .apply()

        getSharedPreferences("therapea_session", MODE_PRIVATE)
            .edit()
            .putString("email", normalizedData.optString("email"))
            .putString(
                "fullName",
                normalizedData.optString("fullName", normalizedData.optString("name", "User"))
            )
            .putString("userId", normalizedData.optString("userId"))
            .putString("role", role)
            .apply()

        getSharedPreferences("TherapeaPrefs", MODE_PRIVATE)
            .edit()
            .putString("email", normalizedData.optString("email"))
            .apply()
    }

    private fun goToHome(data: JSONObject) {
        val role = data.optString("role", "PATIENT").uppercase()

        val intent = when (role) {
            "ADMIN" -> Intent(this, AdminApprovalsActivity::class.java)
            "DOCTOR" -> Intent(this, DoctorHomeActivity::class.java)
            else -> Intent(this, HomeActivity::class.java)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun parseError(data: JSONObject, fallback: String = "Something went wrong. Please try again."): String {
        return data.optString("error", data.optString("message", fallback))
    }

    private fun showInlineAuthState(message: String) {
        tvLoginError.text = message
        tvLoginError.setTextColor(android.graphics.Color.parseColor("#64748B"))
        tvLoginError.visibility = View.VISIBLE
    }

    private fun showLoginError(message: String) {
        tvLoginError.text = message
        tvLoginError.setTextColor(android.graphics.Color.parseColor("#DC2626"))
        tvLoginError.visibility = View.VISIBLE
    }

    private fun hideLoginError() {
        tvLoginError.visibility = View.GONE
    }

    private fun showModalError(message: String) {
        tvModalError.text = message
        tvModalError.visibility = View.VISIBLE
    }

    private fun clearModalError() {
        tvModalError.visibility = View.GONE
        tvModalError.text = ""
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}