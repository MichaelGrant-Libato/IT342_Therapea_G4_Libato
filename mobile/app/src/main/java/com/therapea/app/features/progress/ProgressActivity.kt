package com.therapea.app.features.progress

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class ProgressActivity : Activity() {

    private lateinit var btnBackToPatientList: Button
    private lateinit var tvProgressTitle: TextView
    private lateinit var tvProgressSubtitle: TextView
    private lateinit var etPatientSearch: EditText
    private lateinit var tvProgressMessage: TextView
    private lateinit var patientRosterContainer: LinearLayout
    private lateinit var chartsContainer: LinearLayout
    private lateinit var phqBarsContainer: LinearLayout
    private lateinit var gadBarsContainer: LinearLayout
    private lateinit var tvPhqTrend: TextView
    private lateinit var tvGadTrend: TextView

    private var currentUser = UserData("", "PATIENT")
    private var selectedPatient: PatientData? = null
    private var patientSearchQuery = ""
    private var isLoading = false

    private val patients = mutableListOf<PatientData>()
    private val historyData = mutableListOf<ChartData>()

    // Emulator: 10.0.2.2 reaches your computer localhost.
    // Real phone: replace with your computer LAN IP.
    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        bindViews()
        readUserSession()
        setupListeners()
        initLoad()
    }

    private fun bindViews() {
        btnBackToPatientList = findViewById(R.id.btnBackToPatientList)
        tvProgressTitle = findViewById(R.id.tvProgressTitle)
        tvProgressSubtitle = findViewById(R.id.tvProgressSubtitle)
        etPatientSearch = findViewById(R.id.etPatientSearch)
        tvProgressMessage = findViewById(R.id.tvProgressMessage)
        patientRosterContainer = findViewById(R.id.patientRosterContainer)
        chartsContainer = findViewById(R.id.chartsContainer)
        phqBarsContainer = findViewById(R.id.phqBarsContainer)
        gadBarsContainer = findViewById(R.id.gadBarsContainer)
        tvPhqTrend = findViewById(R.id.tvPhqTrend)
        tvGadTrend = findViewById(R.id.tvGadTrend)
    }

    private fun readUserSession() {
        val prefs = getSharedPreferences("therapea_session", MODE_PRIVATE)

        val email = intent.getStringExtra("email")
            ?: prefs.getString("email", "")
            ?: ""

        val role = intent.getStringExtra("role")
            ?: prefs.getString("role", "PATIENT")
            ?: "PATIENT"

        currentUser = UserData(
            email = email.ifBlank { "patient@example.com" },
            role = role.uppercase()
        )
    }

    private fun setupListeners() {
        btnBackToPatientList.setOnClickListener {
            selectedPatient = null
            historyData.clear()
            render()
        }

        etPatientSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                patientSearchQuery = s?.toString().orEmpty()
                renderPatientRoster()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun initLoad() {
        val targetPatientEmail = intent.getStringExtra("patient")

        if (currentUser.role == "DOCTOR") {
            loadPatients(targetPatientEmail)
        } else {
            loadAssessments(currentUser.email)
        }
    }

    private fun loadPatients(autoSelectEmail: String?) {
        isLoading = true
        render()

        Thread {
            try {
                val response = get("${apiBaseUrl}api/patients/doctor?email=${encode(currentUser.email)}")
                val json = JSONObject(response)

                val loaded = mutableListOf<PatientData>()
                if (json.optBoolean("success", false)) {
                    val array = json.optJSONArray("patients") ?: JSONArray()

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        loaded.add(
                            PatientData(
                                id = obj.optString("id", i.toString()),
                                name = obj.optString("name", obj.optString("fullName", obj.optString("email", "Unknown Patient"))),
                                email = obj.optString("email")
                            )
                        )
                    }
                }

                runOnUiThread {
                    patients.clear()
                    patients.addAll(loaded)
                    isLoading = false

                    val autoSelected = autoSelectEmail?.let { email ->
                        patients.find { it.email == email } ?: PatientData("0", email, email)
                    }

                    if (autoSelected != null) {
                        selectedPatient = autoSelected
                        loadAssessments(autoSelected.email)
                    } else {
                        render()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isLoading = false
                    showDialog("Unable to Load Patients", "Please check your connection and try again.")
                    render()
                }
            }
        }.start()
    }

    private fun loadAssessments(targetEmail: String) {
        isLoading = true
        render()

        Thread {
            try {
                val response = get("${apiBaseUrl}api/assessments/user?email=${encode(targetEmail)}")
                val json = JSONObject(response)

                val loaded = mutableListOf<AssessmentRecord>()

                if (json.optBoolean("success", true)) {
                    val array = json.optJSONArray("assessments") ?: JSONArray()

                    for (i in 0 until array.length()) {
                        loaded.add(parseAssessment(array.getJSONObject(i)))
                    }
                }

                val recent = loaded
                    .sortedBy { parseCreatedAtMillis(it.createdAt) }
                    .takeLast(6)
                    .map {
                        ChartData(
                            date = displayShortDate(it.createdAt),
                            phq = it.phq9Score,
                            gad = it.gad7Score
                        )
                    }

                runOnUiThread {
                    historyData.clear()
                    historyData.addAll(recent)
                    isLoading = false
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    historyData.clear()
                    isLoading = false
                    showDialog("Unable to Load Progress", "Assessment progress could not be loaded right now.")
                    render()
                }
            }
        }.start()
    }

    private fun render() {
        hideMessage()
        patientRosterContainer.removeAllViews()
        chartsContainer.visibility = View.GONE
        btnBackToPatientList.visibility = View.GONE
        etPatientSearch.visibility = View.GONE

        if (isLoading) {
            showMessage("Loading progress data...")
            return
        }

        if (currentUser.role == "DOCTOR" && selectedPatient == null) {
            renderDoctorRosterScreen()
        } else {
            renderChartsScreen()
        }
    }

    private fun renderDoctorRosterScreen() {
        tvProgressTitle.text = "Patient Trajectory"
        tvProgressSubtitle.text = "Select a patient from your roster to review their clinical scores."
        etPatientSearch.visibility = View.VISIBLE

        renderPatientRoster()
    }

    private fun renderPatientRoster() {
        patientRosterContainer.removeAllViews()

        val filtered = patients.filter {
            patientSearchQuery.isBlank() ||
                    it.name.contains(patientSearchQuery, true) ||
                    it.email.contains(patientSearchQuery, true)
        }

        if (filtered.isEmpty()) {
            showMessage(
                if (patientSearchQuery.isBlank()) {
                    "You currently have no patients assigned to you."
                } else {
                    "No patients found. Try adjusting your search."
                }
            )
            return
        }

        hideMessage()

        filtered.forEach { patient ->
            patientRosterContainer.addView(patientCard(patient))
        }
    }

    private fun renderChartsScreen() {
        if (currentUser.role == "DOCTOR") {
            val p = selectedPatient
            btnBackToPatientList.visibility = View.VISIBLE
            tvProgressTitle.text = "Trajectory: ${p?.name ?: "Patient"}"
            tvProgressSubtitle.text = "Reviewing self-assessment clinical scores over time."
        } else {
            tvProgressTitle.text = "Clinical Progress"
            tvProgressSubtitle.text = "Track your PHQ-9 and GAD-7 scores over time."
        }

        if (historyData.isEmpty()) {
            showMessage("No assessment data available yet.")
            return
        }

        chartsContainer.visibility = View.VISIBLE
        renderBarChart(phqBarsContainer, historyData, maxScore = 27, metric = Metric.PHQ)
        renderBarChart(gadBarsContainer, historyData, maxScore = 21, metric = Metric.GAD)

        val phqTrend = trendFor(Metric.PHQ)
        val gadTrend = trendFor(Metric.GAD)

        tvPhqTrend.text = phqTrend.text
        tvPhqTrend.setTextColor(Color.parseColor(phqTrend.color))

        tvGadTrend.text = gadTrend.text
        tvGadTrend.setTextColor(Color.parseColor(gadTrend.color))
    }

    private fun patientCard(patient: PatientData): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.admin_bg_card)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = TextView(this).apply {
            text = initials(patient.name)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
            setTextColor(Color.parseColor("#0A5C36"))
            background = roundedDrawable("#F0FDF4")
        }

        row.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        info.addView(titleText(patient.name, 17f))
        info.addView(bodyText(patient.email))

        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)

        val button = primaryButton("View Trajectory Charts")
        button.setOnClickListener {
            selectedPatient = patient
            loadAssessments(patient.email)
        }
        card.addView(button)

        return card
    }

    private fun renderBarChart(
        container: LinearLayout,
        data: List<ChartData>,
        maxScore: Int,
        metric: Metric
    ) {
        container.removeAllViews()

        data.forEach { item ->
            val value = if (metric == Metric.PHQ) item.phq else item.gad
            val height = ((value.toFloat() / maxScore.toFloat()) * 150).toInt().coerceAtLeast(8)

            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), 0, dp(6), 0)
            }

            val valueText = TextView(this).apply {
                text = value.toString()
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#1E293B"))
            }

            val bar = View(this).apply {
                background = roundedDrawable(if (metric == Metric.PHQ) "#0A5C36" else "#7C3AED")
            }

            val date = TextView(this).apply {
                text = item.date
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64748B"))
                maxLines = 1
            }

            column.addView(valueText)
            column.addView(bar, LinearLayout.LayoutParams(dp(30), dp(height)).apply {
                topMargin = dp(4)
            })
            column.addView(date, LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })

            container.addView(column, LinearLayout.LayoutParams(dp(66), ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun trendFor(metric: Metric): Trend {
        if (historyData.size < 2) {
            return Trend("Need more data to establish trend", "#64748B")
        }

        val latest = if (metric == Metric.PHQ) historyData.last().phq else historyData.last().gad
        val previous = if (metric == Metric.PHQ) historyData[historyData.lastIndex - 1].phq else historyData[historyData.lastIndex - 1].gad

        return when {
            latest < previous -> Trend("↓ Trending down (Improvement)", "#0A5C36")
            latest > previous -> Trend("↑ Trending up (Worsening)", "#DC2626")
            else -> Trend("→ Stable", "#64748B")
        }
    }

    private fun parseAssessment(obj: JSONObject): AssessmentRecord {
        return AssessmentRecord(
            id = obj.optString("id"),
            createdAt = obj.optString("createdAt", obj.optString("date", "")),
            phq9Score = obj.optInt("phq9Score", 0),
            gad7Score = obj.optInt("gad7Score", 0)
        )
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

    private fun displayShortDate(value: String): String {
        val millis = parseCreatedAtMillis(value)

        if (millis <= 0L) {
            return value.take(6).ifBlank { "N/A" }
        }

        return SimpleDateFormat("MMM d", Locale.US).format(millis)
    }

    private fun showMessage(message: String) {
        tvProgressMessage.text = message
        tvProgressMessage.visibility = View.VISIBLE
    }

    private fun hideMessage() {
        tvProgressMessage.visibility = View.GONE
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun titleText(text: String, size: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor("#1E293B"))
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        }
    }

    private fun bodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
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
                topMargin = dp(14)
            }
        }
    }

    private fun roundedDrawable(color: String): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun initials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "?"
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class Metric {
        PHQ,
        GAD
    }

    private data class Trend(
        val text: String,
        val color: String
    )

    private data class UserData(
        val email: String,
        val role: String
    )

    private data class PatientData(
        val id: String,
        val name: String,
        val email: String
    )

    private data class AssessmentRecord(
        val id: String,
        val createdAt: String,
        val phq9Score: Int,
        val gad7Score: Int
    )

    private data class ChartData(
        val date: String,
        val phq: Int,
        val gad: Int
    )
}