package com.therapea.app.features.assessment

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.therapea.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class AssessmentHistoryActivity : Activity() {

    private lateinit var btnBackDashboard: Button
    private lateinit var tvHistoryCount: TextView
    private lateinit var tvHistoryMessage: TextView
    private lateinit var historyHeaderScroll: HorizontalScrollView
    private lateinit var historyListContainer: LinearLayout

    private val assessments = mutableListOf<AssessmentRecord>()
    private var isLoading = false
    private var userEmail = ""

    // Emulator: 10.0.2.2 reaches your computer localhost.
    // Real phone: replace this with your computer LAN IP.
    private val apiBaseUrl = "http://10.0.2.2:8083"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assessment_history)

        bindViews()
        readUserSession()
        setupListeners()
        render()
        loadAssessments()
    }

    private fun bindViews() {
        btnBackDashboard = findViewById(R.id.btnBackDashboard)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        tvHistoryMessage = findViewById(R.id.tvHistoryMessage)
        historyHeaderScroll = findViewById(R.id.historyHeaderScroll)
        historyListContainer = findViewById(R.id.historyListContainer)
    }

    private fun readUserSession() {
        val prefs = getSharedPreferences("therapea_session", MODE_PRIVATE)

        userEmail =
            intent.getStringExtra("email")
                ?: prefs.getString("email", "")
                        ?: ""

        if (userEmail.isBlank()) {
            userEmail = "guest"
        }
    }

    private fun setupListeners() {
        btnBackDashboard.setOnClickListener {
            finish()
        }
    }

    private fun loadAssessments() {
        isLoading = true
        render()

        Thread {
            try {
                val response = get("$apiBaseUrl/api/assessments/user?email=${encode(userEmail)}")
                val loaded = parseBackendAssessments(JSONObject(response))

                runOnUiThread {
                    assessments.clear()
                    assessments.addAll(loaded.sortedByDescending { parseCreatedAtMillis(it.createdAt) })
                    isLoading = false
                    render()
                }
            } catch (e: Exception) {
                val local = loadLocalAssessments()

                runOnUiThread {
                    assessments.clear()
                    assessments.addAll(local.sortedByDescending { parseCreatedAtMillis(it.createdAt) })
                    isLoading = false

                    if (local.isNotEmpty()) {
                        Toast.makeText(this, "Loaded local assessment history.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to load assessment history.", Toast.LENGTH_SHORT).show()
                    }

                    render()
                }
            }
        }.start()
    }

    private fun render() {
        historyListContainer.removeAllViews()
        tvHistoryCount.text = "${assessments.size} record${if (assessments.size == 1) "" else "s"}"

        if (isLoading) {
            showMessage("Loading assessment history...")
            historyHeaderScroll.visibility = View.GONE
            return
        }

        if (assessments.isEmpty()) {
            showMessage("No assessments yet. Take your first Triage Assessment to start tracking your progress.")
            historyHeaderScroll.visibility = View.GONE
            return
        }

        tvHistoryMessage.visibility = View.GONE
        historyHeaderScroll.visibility = View.VISIBLE

        assessments.forEach { record ->
            historyListContainer.addView(historyRow(record))
        }
    }

    private fun historyRow(record: AssessmentRecord): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.admin_bg_card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        card.addView(titleText(displayDate(record.createdAt), 16f))
        card.addView(bodyText(record.assessmentType.ifBlank { "Triage Assessment" }))

        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(10))
        }

        metaRow.addView(
            pillText("${record.riskLevel} • ${record.clinicalScore}", riskBg(record.riskLevel), riskText(record.riskLevel)),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(8)
            }
        )

        metaRow.addView(
            pillText(record.status.ifBlank { "Pending" }, statusBg(record.status), statusText(record.status))
        )

        card.addView(metaRow)

        card.addView(bodyText("PHQ-9: ${record.phq9Score} / 27"))
        card.addView(bodyText("GAD-7: ${record.gad7Score} / 21"))

        val action = primaryButton("View Details")
        action.setOnClickListener {
            openAssessmentResult(record)
        }
        card.addView(action)

        return card
    }

    private fun openAssessmentResult(record: AssessmentRecord) {
        val intent = Intent().setClassName(
            packageName,
            "com.therapea.app.features.assessment.AssessmentResultActivity"
        )

        intent.putExtra("assessmentId", record.id)
        intent.putExtra("assessmentType", record.assessmentType)
        intent.putExtra("phq9Score", record.phq9Score)
        intent.putExtra("gad7Score", record.gad7Score)
        intent.putExtra("clinicalScore", record.clinicalScore)
        intent.putExtra("riskLevel", record.riskLevel)
        intent.putExtra("status", record.status)
        intent.putExtra("createdAt", record.createdAt)

        startActivity(intent)
    }

    private fun parseBackendAssessments(response: JSONObject): List<AssessmentRecord> {
        val success = response.optBoolean("success", true)
        if (!success) return emptyList()

        val array = response.optJSONArray("assessments") ?: JSONArray()
        val result = mutableListOf<AssessmentRecord>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(parseAssessmentObject(obj))
        }

        return result
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
            createdAt = obj.optString("createdAt", obj.optString("date", System.currentTimeMillis().toString()))
        )
    }

    private fun loadLocalAssessments(): List<AssessmentRecord> {
        val prefs = getSharedPreferences("therapea_assessments", MODE_PRIVATE)
        val key = "assessments_$userEmail"
        val raw = prefs.getString(key, "[]") ?: "[]"

        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }

        val result = mutableListOf<AssessmentRecord>()

        for (i in 0 until array.length()) {
            result.add(parseAssessmentObject(array.getJSONObject(i)))
        }

        return result
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

    private fun parseCreatedAtMillis(value: String): Long {
        if (value.isBlank()) return 0L

        value.toLongOrNull()?.let { return it }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "MMM d, yyyy",
            "MMMM d, yyyy"
        )

        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(value)?.time ?: 0L
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    private fun displayDate(value: String): String {
        val millis = parseCreatedAtMillis(value)

        if (millis <= 0L) {
            return value.ifBlank { "N/A" }
        }

        return SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.US).format(millis)
    }

    private fun showMessage(message: String) {
        tvHistoryMessage.text = message
        tvHistoryMessage.visibility = View.VISIBLE
    }

    private fun titleText(text: String, size: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor("#1E293B"))
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setPadding(0, 0, 0, dp(5))
        }
    }

    private fun bodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun pillText(text: String, bgColor: String, textColor: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textColor))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedDrawable(bgColor)
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
                dp(50)
            ).apply {
                topMargin = dp(12)
            }
        }
    }

    private fun riskBg(level: String): String {
        return when (level) {
            "High" -> "#FEF2F2"
            "Moderate" -> "#FFF7ED"
            "Mild" -> "#FFFBEB"
            else -> "#F0FDF4"
        }
    }

    private fun riskText(level: String): String {
        return when (level) {
            "High" -> "#991B1B"
            "Moderate" -> "#9A3412"
            "Mild" -> "#92400E"
            else -> "#065F46"
        }
    }

    private fun statusBg(status: String): String {
        return if (status.equals("Reviewed", true)) "#F0FDF4" else "#F8FAFC"
    }

    private fun statusText(status: String): String {
        return if (status.equals("Reviewed", true)) "#065F46" else "#64748B"
    }

    private fun roundedDrawable(color: String): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(100).toFloat()
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