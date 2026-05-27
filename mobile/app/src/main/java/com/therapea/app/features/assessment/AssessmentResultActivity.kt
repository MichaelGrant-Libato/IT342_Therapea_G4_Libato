package com.therapea.app.features.assessment

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import com.therapea.app.features.emergencyMap.EmergencyMapActivity
import com.therapea.app.features.map.FindTherapistActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class AssessmentResultActivity : Activity() {

    private lateinit var btnBack: Button
    private lateinit var tvResultDate: TextView
    private lateinit var tvRiskLevel: TextView
    private lateinit var tvClinicalScore: TextView
    private lateinit var tvPhqScore: TextView
    private lateinit var tvPhqLabel: TextView
    private lateinit var tvGadScore: TextView
    private lateinit var tvGadLabel: TextView
    private lateinit var tvRecordStatus: TextView
    private lateinit var tvActionMessage: TextView
    private lateinit var actionsContainer: LinearLayout

    private var record: AssessmentRecord? = null
    private var isDoctor = false
    private var isProcessing = false
    private var userEmail = ""

    // Emulator: 10.0.2.2 reaches your computer localhost.
    // Real phone: replace with your computer LAN IP.
    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assessment_result)

        bindViews()
        readUserSession()
        setupListeners()
        loadRecord()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnResultBack)
        tvResultDate = findViewById(R.id.tvResultDate)
        tvRiskLevel = findViewById(R.id.tvRiskLevel)
        tvClinicalScore = findViewById(R.id.tvClinicalScore)
        tvPhqScore = findViewById(R.id.tvPhqScore)
        tvPhqLabel = findViewById(R.id.tvPhqLabel)
        tvGadScore = findViewById(R.id.tvGadScore)
        tvGadLabel = findViewById(R.id.tvGadLabel)
        tvRecordStatus = findViewById(R.id.tvRecordStatus)
        tvActionMessage = findViewById(R.id.tvActionMessage)
        actionsContainer = findViewById(R.id.resultActionsContainer)
    }

    private fun readUserSession() {
        val roleOverride  = intent.getStringExtra("role")
        val emailOverride = intent.getStringExtra("email")

        if (!emailOverride.isNullOrBlank()) {
            userEmail = emailOverride
            isDoctor  = (roleOverride ?: "PATIENT").uppercase() == "DOCTOR"
            return
        }

        val prefs       = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        if (sessionData != null) {
            try {
                val json  = JSONObject(sessionData)
                userEmail = json.optString("email")
                isDoctor  = json.optString("role", "PATIENT").uppercase() == "DOCTOR"
                return
            } catch (_: Exception) { }
        }

        isDoctor  = false
        userEmail = ""
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadRecord() {
        val fromIntent = recordFromIntent()

        if (fromIntent != null) {
            record = fromIntent
            render()
            return
        }

        val assessmentId = intent.getStringExtra("assessmentId").orEmpty()

        if (assessmentId.isBlank()) {
            Toast.makeText(this, "Assessment record not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvActionMessage.text = "Loading assessment record..."

        Thread {
            try {
                val response = get("${apiBaseUrl}api/user?email=${encode(userEmail)}")
                val array = JSONObject(response).optJSONArray("assessments") ?: JSONArray()

                var found: AssessmentRecord? = null

                for (i in 0 until array.length()) {
                    val item = parseAssessmentObject(array.getJSONObject(i))
                    if (item.id == assessmentId) {
                        found = item
                        break
                    }
                }

                runOnUiThread {
                    record = found ?: loadLocalRecord(assessmentId)

                    if (record == null) {
                        Toast.makeText(this, "Assessment record not found.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        render()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    record = loadLocalRecord(assessmentId)

                    if (record == null) {
                        Toast.makeText(this, "Assessment record not found.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        render()
                    }
                }
            }
        }.start()
    }

    private fun render() {
        val r = record ?: return

        tvActionMessage.text = ""

        tvResultDate.text = displayDate(r.createdAt)
        tvRiskLevel.text = r.riskLevel
        tvRiskLevel.setTextColor(riskColor(r.riskLevel))

        tvClinicalScore.text = "Clinical score: ${r.clinicalScore} out of 100"

        tvPhqScore.text = r.phq9Score.toString()
        tvPhqLabel.text = "/ 27 • ${phq9Label(r.phq9Score)}"

        tvGadScore.text = r.gad7Score.toString()
        tvGadLabel.text = "/ 21 • ${gad7Label(r.gad7Score)}"

        tvRecordStatus.text = r.status.ifBlank { "Pending" }
        tvRecordStatus.setTextColor(
            if (r.status.equals("Reviewed", true)) Color.parseColor("#0A5C36")
            else Color.parseColor("#64748B")
        )

        actionsContainer.removeAllViews()

        if (isDoctor) {
            renderDoctorActions(r)
        } else {
            renderPatientActions(r)
        }
    }

    private fun renderDoctorActions(r: AssessmentRecord) {
        if (r.status.equals("Pending", true)) {
            val markReviewed = primaryButton(if (isProcessing) "Processing..." else "Mark as Reviewed")
            markReviewed.isEnabled = !isProcessing
            markReviewed.setOnClickListener {
                markAsReviewed(r)
            }
            actionsContainer.addView(markReviewed)
        } else {
            val reviewed = TextView(this).apply {
                text = "✓ This assessment has been reviewed"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.parseColor("#065F46"))
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = roundedDrawable("#F0FDF4")
            }

            actionsContainer.addView(
                reviewed,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun renderPatientActions(r: AssessmentRecord) {
        actionsContainer.addView(primaryButton("View Therapists").also {
            it.setOnClickListener {
                startActivity(Intent(this, FindTherapistActivity::class.java))
            }
        })
        actionsContainer.addView(outlineButton("Emergency Resources").also {
            it.setOnClickListener {
                startActivity(Intent(this, EmergencyMapActivity::class.java))
            }
        })

        actionsContainer.addView(
            dangerButton(if (isProcessing) "Deleting..." else "Delete this Record").also {
                it.isEnabled = !isProcessing
                it.setOnClickListener { showDeleteDialog(r) }
            }
        )
    }

    private fun showDeleteDialog(r: AssessmentRecord) {
        AlertDialog.Builder(this)
            .setTitle("Delete Assessment?")
            .setMessage("Are you sure you want to delete this record? This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteRecord(r)
            }
            .show()
    }

    private fun markAsReviewed(r: AssessmentRecord) {
        isProcessing = true
        render()

        Thread {
            try {
                patch("${apiBaseUrl}api/assessments/${r.id}/review")

                runOnUiThread {
                    isProcessing = false
                    record = r.copy(status = "Reviewed")
                    updateLocalRecordStatus(r.id, "Reviewed")
                        Toast.makeText(this, "Assessment marked as reviewed.", Toast.LENGTH_SHORT).show()
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isProcessing = false
                    Toast.makeText(this, "Failed to mark as reviewed.", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }.start()
    }

    private fun deleteRecord(r: AssessmentRecord) {
        isProcessing = true
        render()

        Thread {
            try {
                delete("${apiBaseUrl}api/assessments/${r.id}")
            } catch (_: Exception) {
            }

            removeLocalRecord(r.id)

            runOnUiThread {
                isProcessing = false
                Toast.makeText(this, "Assessment deleted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    private fun recordFromIntent(): AssessmentRecord? {
        val id = intent.getStringExtra("assessmentId").orEmpty()
        if (id.isBlank()) return null

        val hasPayload =
            intent.hasExtra("riskLevel") ||
                    intent.hasExtra("clinicalScore") ||
                    intent.hasExtra("phq9Score") ||
                    intent.hasExtra("gad7Score")

        if (!hasPayload) return null

        return AssessmentRecord(
            id = id,
            assessmentType = intent.getStringExtra("assessmentType") ?: "Triage Assessment",
            phq9Score = intent.getIntExtra("phq9Score", 0),
            gad7Score = intent.getIntExtra("gad7Score", 0),
            clinicalScore = intent.getIntExtra("clinicalScore", 0),
            riskLevel = intent.getStringExtra("riskLevel") ?: "Low",
            status = intent.getStringExtra("status") ?: "Pending",
            createdAt = intent.getStringExtra("createdAt") ?: ""
        )
    }

    private fun parseAssessmentObject(obj: JSONObject): AssessmentRecord {
        return AssessmentRecord(
            id = obj.optString("id", "asm_${System.currentTimeMillis()}"),
            assessmentType = obj.optString("assessmentType", "Triage Assessment"),
            phq9Score = obj.optInt("phq9Score", 0),
            gad7Score = obj.optInt("gad7Score", 0),
            clinicalScore = obj.optInt("clinicalScore", obj.optInt("score", 0)),
            riskLevel = obj.optString("riskLevel", "Low"),
            status = obj.optString("status", "Pending"),
            createdAt = obj.optString("createdAt", obj.optString("date", ""))
        )
    }

    private fun loadLocalRecord(id: String): AssessmentRecord? {
        val prefs = getSharedPreferences("therapea_assessments", MODE_PRIVATE)
        val key = "assessments_${userEmail.ifBlank { "guest" }}"
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        for (i in 0 until array.length()) {
            val record = parseAssessmentObject(array.getJSONObject(i))
            if (record.id == id) return record
        }

        return null
    }

    private fun updateLocalRecordStatus(id: String, status: String) {
        val prefs = getSharedPreferences("therapea_assessments", MODE_PRIVATE)
        val key = "assessments_${userEmail.ifBlank { "guest" }}"
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            if (obj.optString("id") == id) {
                obj.put("status", status)
            }

            updated.put(obj)
        }

        prefs.edit().putString(key, updated.toString()).apply()
    }

    private fun removeLocalRecord(id: String) {
        val prefs = getSharedPreferences("therapea_assessments", MODE_PRIVATE)
        val key = "assessments_${userEmail.ifBlank { "guest" }}"
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("id") != id) {
                updated.put(obj)
            }
        }

        prefs.edit().putString(key, updated.toString()).apply()
    }

    private fun get(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        return readResponse(connection)
    }

    private fun patch(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "PATCH"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        return readResponse(connection)
    }

    private fun delete(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
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

    private fun displayDate(value: String): String {
        if (value.isBlank()) return "N/A"

        value.toLongOrNull()?.let {
            return SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(it)
        }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "MMM d, yyyy",
            "MMMM d, yyyy"
        )

        for (pattern in patterns) {
            try {
                val date = SimpleDateFormat(pattern, Locale.US).parse(value)
                if (date != null) {
                    return SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(date)
                }
            } catch (_: Exception) {
            }
        }

        return value
    }

    private fun phq9Label(score: Int): String {
        return when {
            score <= 4 -> "None-minimal"
            score <= 9 -> "Mild"
            score <= 14 -> "Moderate"
            score <= 19 -> "Moderately severe"
            else -> "Severe"
        }
    }

    private fun gad7Label(score: Int): String {
        return when {
            score <= 4 -> "Minimal"
            score <= 9 -> "Mild"
            score <= 14 -> "Moderate"
            else -> "Severe"
        }
    }

    private fun riskColor(level: String): Int {
        return when (level) {
            "High" -> Color.parseColor("#DC2626")
            "Moderate" -> Color.parseColor("#D97706")
            "Mild" -> Color.parseColor("#D97706")
            else -> Color.parseColor("#0A5C36")
        }
    }

    private fun primaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.admin_bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun outlineButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E293B"))
            setBackgroundResource(R.drawable.admin_bg_button_outline)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun dangerButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#DC2626"))
            setBackgroundResource(R.drawable.admin_bg_button_outline)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun roundedDrawable(color: String): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class AssessmentRecord(
        val id: String,
        val assessmentType: String,
        val phq9Score: Int,
        val gad7Score: Int,
        val clinicalScore: Int,
        val riskLevel: String,
        val status: String,
        val createdAt: String
    )
}