// app/src/main/java/com/therapea/app/features/auth/ReferenceActivity.kt
package com.therapea.app.features.auth

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class ReferenceActivity : Activity() {

    private lateinit var formContainer: LinearLayout
    private lateinit var resultContainer: LinearLayout

    private lateinit var etReferenceNumber: EditText
    private lateinit var etReferenceEmail: EditText
    private lateinit var tvError: TextView

    private lateinit var btnCheckStatus: Button
    private lateinit var btnCheckAnother: Button
    private lateinit var btnGoLogin: Button
    private lateinit var btnReferenceHome: Button

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusMessage: TextView

    private var isLoading = false

    // Emulator: 10.0.2.2 reaches your computer localhost.
    // Real phone: replace this with your computer LAN IP.
    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reference)

        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        formContainer = findViewById(R.id.referenceFormContainer)
        resultContainer = findViewById(R.id.referenceResultContainer)

        etReferenceNumber = findViewById(R.id.etReferenceNumber)
        etReferenceEmail = findViewById(R.id.etReferenceEmail)
        tvError = findViewById(R.id.tvReferenceError)

        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        btnCheckAnother = findViewById(R.id.btnCheckAnother)
        btnGoLogin = findViewById(R.id.btnGoLogin)
        btnReferenceHome = findViewById(R.id.btnReferenceHome)

        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusMessage = findViewById(R.id.tvStatusMessage)
    }

    private fun setupListeners() {
        btnCheckStatus.setOnClickListener {
            checkStatus()
        }

        btnCheckAnother.setOnClickListener {
            resetForm()
        }

        btnGoLogin.setOnClickListener {
            // If you have LoginActivity, replace finish() with startActivity(Intent(...)).
            finish()
        }

        btnReferenceHome.setOnClickListener {
            finish()
        }
    }

    private fun checkStatus() {
        val referenceNumber = etReferenceNumber.text.toString().trim()
        val email = etReferenceEmail.text.toString().trim()

        if (referenceNumber.isBlank()) {
            showError("Please enter your reference number.")
            return
        }

        if (email.isBlank()) {
            showError("Please enter your registered email.")
            return
        }

        hideError()
        setLoading(true)

        Thread {
            try {
                val url =
                    "${apiBaseUrl}api/auth/check-status?ref=${encode(referenceNumber)}&email=${encode(email)}"

                val response = get(url)
                val json = JSONObject(response)

                val status = json.optString("status", "PENDING")
                val message = json.optString("message", "")

                runOnUiThread {
                    setLoading(false)
                    showResult(status, message)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showError("No application found. If your application was recently rejected, your record may have been cleared so you can register again.")
                }
            }
        }.start()
    }

    private fun showResult(statusRaw: String, serverMessage: String) {
        val status = statusRaw.uppercase()

        formContainer.visibility = View.GONE
        resultContainer.visibility = View.VISIBLE

        tvStatusBadge.text = status
        tvStatusTitle.text = "Status: $status"

        when (status) {
            "APPROVED" -> {
                tvStatusBadge.setTextColor(Color.parseColor("#0A5C36"))
                tvStatusTitle.setTextColor(Color.parseColor("#0A5C36"))
                tvStatusMessage.text =
                    "Congratulations! Your account has been approved and activated. You can now log in to your provider dashboard."
                btnGoLogin.visibility = View.VISIBLE
            }

            "REJECTED" -> {
                tvStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                tvStatusTitle.setTextColor(Color.parseColor("#DC2626"))
                tvStatusMessage.text =
                    serverMessage.ifBlank { "Your application was declined. Please contact support for more details." }
                btnGoLogin.visibility = View.GONE
            }

            else -> {
                tvStatusBadge.setTextColor(Color.parseColor("#D97706"))
                tvStatusTitle.setTextColor(Color.parseColor("#D97706"))
                tvStatusMessage.text =
                    "Your application is currently under review by our administration team. We will send you an email as soon as a decision is made."
                btnGoLogin.visibility = View.GONE
            }
        }
    }

    private fun resetForm() {
        resultContainer.visibility = View.GONE
        formContainer.visibility = View.VISIBLE
        btnGoLogin.visibility = View.GONE
        hideError()

        etReferenceNumber.setText("")
        etReferenceEmail.setText("")
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        btnCheckStatus.isEnabled = !loading
        btnCheckStatus.text = if (loading) "Checking..." else "Check Status"
    }

    private fun get(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use(BufferedReader::readText)

        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $response")
        }

        return response
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
}