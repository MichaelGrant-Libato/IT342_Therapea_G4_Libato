package com.therapea.app.features.auth

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.therapea.app.R
import com.therapea.app.features.home.HomeActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class RegisterActivity : Activity() {

    private val API_BASE_URL = "http://10.0.2.2:8083"
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val PICK_FILE_REQUEST_CODE = 1001

    private lateinit var flipper: ViewFlipper
    private lateinit var tvError1: TextView
    private lateinit var tvError3: TextView

    private var selectedFileUri: Uri? = null
    private var selectedRole = "PATIENT"
    private var isGoogleFlow = false
    private var googleEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_activity_register_host)
        flipper = findViewById(R.id.regFlipper)
        tvError1 = findViewById(R.id.tvRegError1)
        tvError3 = findViewById(R.id.tvRegError3)

        handleRoutedIntent(intent)
        setupStep1()
        setupOtpStep()
        setupRoleStep()
        setupReviewStep()
        setupDoctorSteps()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRoutedIntent(intent)
    }

    private fun handleRoutedIntent(incomingIntent: Intent?) {
        val error = incomingIntent?.getStringExtra("google_error") ?: ""
        val email = incomingIntent?.getStringExtra("google_email") ?: ""

        if (error.isNotEmpty()) {
            flipper.displayedChild = 0
            showError1(error)
        } else if (email.isNotEmpty()) {
            isGoogleFlow = true
            googleEmail = email
            flipper.displayedChild = 1
            findViewById<TextView>(R.id.tvOtpSubtitle).text = "We've sent a 6-digit code to $email."
            sendOtpRequest(email)
        }
    }

    private fun setupStep1() {
        findViewById<TextView>(R.id.btnGoToLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<MaterialButton>(R.id.btnNext1).setOnClickListener {
            val name = findViewById<EditText>(R.id.etRegName).text.toString().trim()
            val email = findViewById<EditText>(R.id.etRegEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etRegPass).text.toString()
            val confirm = findViewById<EditText>(R.id.etRegConfirm).text.toString()

            when {
                name.isEmpty() -> showError1("Please enter your full name.")
                email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> showError1("Please enter a valid email address.")
                pass.length < 8 -> showError1("Password must be at least 8 characters.")
                pass != confirm -> showError1("Passwords do not match.")
                else -> checkEmailExistence(email)
            }
        }

        findViewById<MaterialButton>(R.id.btnRegGoogleLogin).setOnClickListener {
            getSharedPreferences("TheraPeaAuth", MODE_PRIVATE).edit().putString("auth_origin", "REGISTER").apply()
            scope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("$API_BASE_URL/api/auth/google-register-url?source=android").build()
                    val response = client.newCall(request).execute()
                    val data = JSONObject(response.body?.string() ?: "{}")
                    val authUrl = data.optString("url")
                    if (authUrl.isNotEmpty()) withContext(Dispatchers.Main) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))) }
                } catch (e: Exception) {}
            }
        }
    }

    private fun checkEmailExistence(email: String) {
        val btnNext = findViewById<MaterialButton>(R.id.btnNext1)
        btnNext.isEnabled = false
        btnNext.text = "Checking..."

        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$API_BASE_URL/api/auth/me?email=$email").build()
                val response = client.newCall(request).execute()
                val exists = response.code == 200
                response.close()

                withContext(Dispatchers.Main) {
                    btnNext.isEnabled = true
                    btnNext.text = "Continue"
                    if (exists) showError1("Account already existing")
                    else {
                        findViewById<TextView>(R.id.tvRegError1).visibility = View.GONE
                        flipper.displayedChild = 2
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { btnNext.isEnabled = true; btnNext.text = "Continue"; flipper.displayedChild = 2 }
            }
        }
    }

    private fun setupOtpStep() {
        findViewById<MaterialButton>(R.id.btnVerifyOtp).setOnClickListener {
            val otpCode = findViewById<EditText>(R.id.etOtpCode).text.toString().trim()
            if (otpCode.length != 6) return@setOnClickListener Toast.makeText(this, "Enter 6 digits", Toast.LENGTH_SHORT).show()
            verifyOtpCall(otpCode)
        }
        findViewById<TextView>(R.id.btnResendOtp).setOnClickListener { sendOtpRequest(googleEmail) }
    }

    private fun sendOtpRequest(email: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", email); put("type", "REGISTER") }
                val request = Request.Builder().url("$API_BASE_URL/api/auth/send-otp").post(body.toString().toRequestBody(JSON)).build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {}
        }
    }

    private fun verifyOtpCall(otp: String) {
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyOtp)
        btnVerify.isEnabled = false
        btnVerify.text = "Verifying..."

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", googleEmail); put("otp", otp) }
                val request = Request.Builder().url("$API_BASE_URL/api/auth/verify-otp").post(body.toString().toRequestBody(JSON)).build()
                val isSuccess = client.newCall(request).execute().isSuccessful

                withContext(Dispatchers.Main) {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify & Continue"
                    if (isSuccess) flipper.displayedChild = 2
                    else Toast.makeText(this@RegisterActivity, "Invalid code.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { btnVerify.isEnabled = true; btnVerify.text = "Verify & Continue" }
            }
        }
    }

    private fun setupRoleStep() {
        val cardPatient = findViewById<MaterialCardView>(R.id.cardPatient)
        val cardDoctor = findViewById<MaterialCardView>(R.id.cardDoctor)

        fun updateRoleUI() {
            if (selectedRole == "PATIENT") {
                cardPatient.strokeColor = Color.parseColor("#7A9E78")
                cardPatient.setCardBackgroundColor(Color.parseColor("#F0F4F0"))
                cardDoctor.strokeColor = Color.parseColor("#ECEEE8")
                cardDoctor.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            } else {
                cardDoctor.strokeColor = Color.parseColor("#7A9E78")
                cardDoctor.setCardBackgroundColor(Color.parseColor("#F0F4F0"))
                cardPatient.strokeColor = Color.parseColor("#ECEEE8")
                cardPatient.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            }
        }

        cardPatient.setOnClickListener { selectedRole = "PATIENT"; updateRoleUI() }
        cardDoctor.setOnClickListener { selectedRole = "DOCTOR"; updateRoleUI() }

        findViewById<MaterialButton>(R.id.btnBackToStep1).setOnClickListener {
            if (isGoogleFlow) { startActivity(Intent(this, LoginActivity::class.java)); finish() }
            else flipper.displayedChild = 0
        }

        findViewById<MaterialButton>(R.id.btnNext2).setOnClickListener {
            val nameToDisplay = if (isGoogleFlow) googleEmail.split("@")[0] else findViewById<EditText>(R.id.etRegName).text.toString()
            val emailToDisplay = if (isGoogleFlow) googleEmail else findViewById<EditText>(R.id.etRegEmail).text.toString()
            findViewById<TextView>(R.id.tvReviewName).text = nameToDisplay
            findViewById<TextView>(R.id.tvReviewEmail).text = emailToDisplay
            findViewById<TextView>(R.id.tvReviewRole).text = if (selectedRole == "DOCTOR") "Licensed Doctor" else "Patient"

            val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitFinal)
            btnSubmit.text = if (selectedRole == "DOCTOR") "Continue to Verification" else "Register Account"
            flipper.displayedChild = 3
        }
    }

    private fun setupReviewStep() {
        findViewById<MaterialButton>(R.id.btnBackToStep2).setOnClickListener { flipper.displayedChild = 2 }

        findViewById<MaterialButton>(R.id.btnSubmitFinal).setOnClickListener {
            if (!findViewById<CheckBox>(R.id.cbTerms).isChecked) {
                showError3("You must accept the Terms of Service to continue.")
                return@setOnClickListener
            }
            findViewById<TextView>(R.id.tvRegError3).visibility = View.GONE

            if (selectedRole == "DOCTOR") {
                val nameToDisplay = if (isGoogleFlow) googleEmail.split("@")[0] else findViewById<EditText>(R.id.etRegName).text.toString()
                findViewById<TextView>(R.id.tvDoctorWelcome).text = "Welcome, Dr. ${nameToDisplay.split(" ")[0]}"
                flipper.displayedChild = 4
            } else {
                if (isGoogleFlow) submitGooglePatient() else submitRegularPatient()
            }
        }
    }

    private fun setupDoctorSteps() {
        findViewById<MaterialButton>(R.id.btnDocNext1).setOnClickListener {
            if (findViewById<EditText>(R.id.etDocBio).text.toString().isEmpty() || findViewById<EditText>(R.id.etDocRate).text.toString().isEmpty()) return@setOnClickListener
            flipper.displayedChild = 5
        }
        findViewById<LinearLayout>(R.id.btnPickFile).setOnClickListener { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, PICK_FILE_REQUEST_CODE) }
        findViewById<MaterialButton>(R.id.btnDocBackToBio).setOnClickListener { flipper.displayedChild = 4 }
        findViewById<MaterialButton>(R.id.btnSubmitDoctor).setOnClickListener {
            if (selectedFileUri == null) return@setOnClickListener Toast.makeText(this, "Please attach your PRC license", Toast.LENGTH_SHORT).show()
            submitDoctor()
        }
        findViewById<MaterialButton>(R.id.btnGoHome).setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)); finish() }
    }

    // =========================================================
    // DYNAMIC ERROR HANDLING UPDATES FOR SUBMISSIONS
    // =========================================================

    private fun submitRegularPatient() {
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitFinal)
        btnSubmit.text = "Registering..."
        btnSubmit.isEnabled = false

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("fullName", findViewById<EditText>(R.id.etRegName).text.toString())
                    put("email", findViewById<EditText>(R.id.etRegEmail).text.toString())
                    put("password", findViewById<EditText>(R.id.etRegPass).text.toString())
                    put("role", selectedRole)
                }
                val request = Request.Builder().url("$API_BASE_URL/api/auth/register").post(body.toString().toRequestBody(JSON)).build()
                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                val responseBody = response.body?.string() ?: "{}"

                withContext(Dispatchers.Main) {
                    btnSubmit.text = "Register Account"
                    btnSubmit.isEnabled = true

                    if (isSuccess) {
                        try {
                            getSharedPreferences("TheraPeaSession", MODE_PRIVATE).edit().putString("user_data", JSONObject(responseBody).toString()).apply()
                            showFinalPopup("You're now registered!", true)
                        } catch (e: Exception) { showFinalPopup("You're now registered!", true) }
                    } else {
                        val resData = try { JSONObject(responseBody) } catch(e: Exception) { JSONObject() }
                        val errorMsg = resData.optString("error", "Registration failed.")
                        flipper.displayedChild = 0
                        showError1(errorMsg) // Now shows the REAL backend error!
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { btnSubmit.text = "Register Account"; btnSubmit.isEnabled = true; flipper.displayedChild = 0; showError1("Connection error. Please try again.") }
            }
        }
    }

    private fun submitGooglePatient() {
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitFinal)
        btnSubmit.text = "Setting up account..."
        btnSubmit.isEnabled = false

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", googleEmail)
                    put("fullName", googleEmail.split("@")[0])
                    put("role", selectedRole)
                    put("password", "")
                }
                val request = Request.Builder().url("$API_BASE_URL/api/auth/complete-google-profile").post(body.toString().toRequestBody(JSON)).build()
                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                val responseBody = response.body?.string() ?: "{}"

                withContext(Dispatchers.Main) {
                    btnSubmit.text = "Create My Account"
                    btnSubmit.isEnabled = true

                    if (isSuccess) {
                        try {
                            getSharedPreferences("TheraPeaSession", MODE_PRIVATE).edit().putString("user_data", JSONObject(responseBody).toString()).apply()
                            showFinalPopup("You're now registered!", true)
                        } catch (e: Exception) { showFinalPopup("You're now registered!", true) }
                    } else {
                        val resData = try { JSONObject(responseBody) } catch(e: Exception) { JSONObject() }
                        val errorMsg = resData.optString("error", "Failed to complete profile.")
                        showError3(errorMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { btnSubmit.text = "Create My Account"; btnSubmit.isEnabled = true; showError3("Connection error. Please try again.") }
            }
        }
    }

    private fun submitDoctor() {
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitDoctor)
        btnSubmit.text = "Submitting..."
        btnSubmit.isEnabled = false

        scope.launch(Dispatchers.IO) {
            try {
                val endpoint = if (isGoogleFlow) "/api/auth/register-google-doctor" else "/api/auth/register-doctor"
                val file = getFileFromUri(selectedFileUri!!)
                val mimeType = contentResolver.getType(selectedFileUri!!) ?: "application/pdf"

                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("fullName", if (isGoogleFlow) googleEmail.split("@")[0] else findViewById<EditText>(R.id.etRegName).text.toString())
                    .addFormDataPart("email", if (isGoogleFlow) googleEmail else findViewById<EditText>(R.id.etRegEmail).text.toString())
                    .addFormDataPart("clinicalBio", findViewById<EditText>(R.id.etDocBio).text.toString())
                    .addFormDataPart("hourlyRate", findViewById<EditText>(R.id.etDocRate).text.toString())
                    .addFormDataPart("prcLicense", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))

                if (!isGoogleFlow) requestBody.addFormDataPart("password", findViewById<EditText>(R.id.etRegPass).text.toString())

                val request = Request.Builder().url("$API_BASE_URL$endpoint").post(requestBody.build()).build()
                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                val responseBody = response.body?.string() ?: "{}"

                withContext(Dispatchers.Main) {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Application"

                    if (isSuccess) {
                        val data = JSONObject(responseBody)
                        findViewById<TextView>(R.id.tvRefNumber).text = data.optString("referenceNumber", "REF-XXXXXX")
                        showFinalPopup("Your registration is now confirmed and will be checked by the admin.", false)
                    } else {
                        val resData = try { JSONObject(responseBody) } catch(e: Exception) { JSONObject() }
                        val errorMsg = resData.optString("error", "Registration failed.")

                        // Only bounce back to Step 1 if the error is email-related
                        if (errorMsg.contains("email", ignoreCase = true) || errorMsg.contains("exist", ignoreCase = true)) {
                            flipper.displayedChild = 0
                            showError1(errorMsg)
                        } else {
                            // Otherwise, stay on the Doctor screen and show a toast (e.g. for invalid file size)
                            Toast.makeText(this@RegisterActivity, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSubmit.isEnabled = true;
                    btnSubmit.text = "Submit Application";
                    Toast.makeText(this@RegisterActivity, "Connection error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFinalPopup(message: String, isPatient: Boolean = true) {
        AlertDialog.Builder(this).setTitle(if (isPatient) "Success" else "Registration Submitted").setMessage(message).setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                if (isPatient) { startActivity(Intent(this, HomeActivity::class.java)); finish() }
                else flipper.displayedChild = 6
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedFileUri = uri
                findViewById<TextView>(R.id.tvSelectedFile).apply { text = "Attached: ${getFileName(uri)}"; visibility = View.VISIBLE }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
    private fun showError1(msg: String) { findViewById<TextView>(R.id.tvRegError1).apply { text = msg; visibility = View.VISIBLE } }
    private fun showError3(msg: String) { findViewById<TextView>(R.id.tvRegError3).apply { text = msg; visibility = View.VISIBLE } }
    private fun getFileFromUri(uri: Uri): File {
        val tempFile = File(cacheDir, getFileName(uri))
        contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(tempFile).use { output -> input.copyTo(output) } }
        return tempFile
    }
    private fun getFileName(uri: Uri): String {
        var name = "uploaded_file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
        return name
    }
}