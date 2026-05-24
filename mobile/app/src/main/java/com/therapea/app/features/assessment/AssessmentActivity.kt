package com.therapea.app.features.assessment

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import com.therapea.app.R
import com.therapea.app.features.emergencyMap.EmergencyMapActivity
import com.therapea.app.features.map.FindTherapistActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt


class AssessmentActivity : Activity() {

    private lateinit var tvEyebrow: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressMetaRow: LinearLayout
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvQuestionCount: TextView

    private lateinit var introContainer: LinearLayout
    private lateinit var quizContainer: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var quizActions: LinearLayout

    private lateinit var btnStartAssessment: Button
    private lateinit var tvQuestionSection: TextView
    private lateinit var tvQuestionText: TextView
    private lateinit var tvSensitiveAlert: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var tvQuizError: TextView
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    private lateinit var tvRiskLevel: TextView
    private lateinit var tvClinicalScore: TextView
    private lateinit var tvPhqScore: TextView
    private lateinit var tvPhqLabel: TextView
    private lateinit var tvGadScore: TextView
    private lateinit var tvGadLabel: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var tvSaveStatus: TextView
    private lateinit var btnFindTherapist: Button
    private lateinit var btnEmergencyMap: Button
    private lateinit var btnBackDashboard: Button

    private var screen = Screen.INTRO
    private var currentQuestion = 0
    private var selectedAnswer: Int? = null
    private var isSaving = false
    private var latestResult: AssessmentResult? = null

    private val answers = MutableList(QUESTIONS.size) { -1 }

    // Emulator: 10.0.2.2 reaches your computer localhost.
    // Real phone: replace this with your computer LAN IP.
    private val apiBaseUrl = "http://10.0.2.2:8083"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assessment)

        bindViews()
        setupListeners()
        render()
    }

    private fun bindViews() {
        tvEyebrow = findViewById(R.id.tvAssessmentEyebrow)
        tvTitle = findViewById(R.id.tvAssessmentTitle)
        tvSubtitle = findViewById(R.id.tvAssessmentSubtitle)
        progress = findViewById(R.id.assessmentProgress)
        progressMetaRow = findViewById(R.id.progressMetaRow)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvQuestionCount = findViewById(R.id.tvQuestionCount)

        introContainer = findViewById(R.id.introContainer)
        quizContainer = findViewById(R.id.quizContainer)
        resultsContainer = findViewById(R.id.resultsContainer)
        quizActions = findViewById(R.id.quizActions)

        btnStartAssessment = findViewById(R.id.btnStartAssessment)
        tvQuestionSection = findViewById(R.id.tvQuestionSection)
        tvQuestionText = findViewById(R.id.tvQuestionText)
        tvSensitiveAlert = findViewById(R.id.tvSensitiveAlert)
        optionsContainer = findViewById(R.id.optionsContainer)
        tvQuizError = findViewById(R.id.tvQuizError)
        btnBack = findViewById(R.id.btnAssessmentBack)
        btnNext = findViewById(R.id.btnAssessmentNext)

        tvRiskLevel = findViewById(R.id.tvRiskLevel)
        tvClinicalScore = findViewById(R.id.tvClinicalScore)
        tvPhqScore = findViewById(R.id.tvPhqScore)
        tvPhqLabel = findViewById(R.id.tvPhqLabel)
        tvGadScore = findViewById(R.id.tvGadScore)
        tvGadLabel = findViewById(R.id.tvGadLabel)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        tvSaveStatus = findViewById(R.id.tvSaveStatus)
        btnFindTherapist = findViewById(R.id.btnFindTherapist)
        btnEmergencyMap = findViewById(R.id.btnEmergencyMap)
        btnBackDashboard = findViewById(R.id.btnBackDashboard)
    }

    private fun setupListeners() {
        btnStartAssessment.setOnClickListener {
            screen = Screen.QUIZ
            currentQuestion = 0
            selectedAnswer = if (answers[0] >= 0) answers[0] else null
            render()
        }

        btnBack.setOnClickListener { handleBack() }
        btnNext.setOnClickListener { handleNext() }

        btnFindTherapist.setOnClickListener {
            startActivity(Intent(this, FindTherapistActivity::class.java))
        }

        btnEmergencyMap.setOnClickListener {
            startActivity(Intent(this, EmergencyMapActivity::class.java))
        }

        btnBackDashboard.setOnClickListener { finish() }
    }

    private fun render() {
        introContainer.visibility = View.GONE
        quizContainer.visibility = View.GONE
        resultsContainer.visibility = View.GONE
        quizActions.visibility = View.GONE
        progress.visibility = View.GONE
        progressMetaRow.visibility = View.GONE

        when (screen) {
            Screen.INTRO -> renderIntro()
            Screen.QUIZ -> renderQuiz()
            Screen.RESULTS -> renderResults()
        }
    }

    private fun renderIntro() {
        tvEyebrow.text = "Clinical Screening"
        tvTitle.text = "Smart Triage Assessment"
        tvSubtitle.text = "Clinically validated PHQ-9 and GAD-7 questions to understand how you have been feeling."
        progress.progress = 0
        introContainer.visibility = View.VISIBLE
    }

    private fun renderQuiz() {
        val question = QUESTIONS[currentQuestion]
        val percent = ((currentQuestion.toDouble() / QUESTIONS.size.toDouble()) * 100).roundToInt()

        tvEyebrow.text = "Question ${currentQuestion + 1}"
        tvTitle.text = "Triage Assessment"
        tvSubtitle.text = "Choose the answer that best describes the last two weeks."

        progress.visibility = View.VISIBLE
        progressMetaRow.visibility = View.VISIBLE
        progress.progress = percent
        tvProgressPercent.text = "$percent% complete"
        tvQuestionCount.text = "Question ${currentQuestion + 1} of ${QUESTIONS.size}"

        quizContainer.visibility = View.VISIBLE
        quizActions.visibility = View.VISIBLE

        tvQuestionSection.text = question.section
        tvQuestionText.text = question.text
        tvSensitiveAlert.visibility = if (question.sensitive) View.VISIBLE else View.GONE
        tvQuizError.visibility = View.GONE

        btnNext.text = if (currentQuestion == QUESTIONS.lastIndex) "See Results" else "Next"

        selectedAnswer = if (answers[currentQuestion] >= 0) answers[currentQuestion] else selectedAnswer

        renderOptions()
    }

    private fun renderOptions() {
        optionsContainer.removeAllViews()

        OPTIONS.forEach { option ->
            val isSelected = selectedAnswer == option.value

            val button = Button(this).apply {
                text = if (isSelected) "✓ ${option.label}" else option.label
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                textSize = 14f
                setTextColor(
                    if (isSelected) Color.parseColor("#0A5C36")
                    else Color.parseColor("#1E293B")
                )
                setBackgroundResource(
                    if (isSelected) R.drawable.admin_bg_button_outline
                    else R.drawable.admin_bg_input
                )
                setOnClickListener {
                    selectedAnswer = option.value
                    tvQuizError.visibility = View.GONE
                    renderOptions()
                }
            }

            optionsContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(54)
                ).apply {
                    topMargin = dp(10)
                }
            )
        }
    }

    private fun handleBack() {
        when {
            screen == Screen.RESULTS -> {
                screen = Screen.QUIZ
                currentQuestion = QUESTIONS.lastIndex
                selectedAnswer = answers[currentQuestion].takeIf { it >= 0 }
                render()
            }

            currentQuestion == 0 -> {
                screen = Screen.INTRO
                render()
            }

            else -> {
                if (selectedAnswer != null) {
                    answers[currentQuestion] = selectedAnswer!!
                }

                currentQuestion--
                selectedAnswer = answers[currentQuestion].takeIf { it >= 0 }
                render()
            }
        }
    }

    private fun handleNext() {
        val answer = selectedAnswer

        if (answer == null) {
            tvQuizError.visibility = View.VISIBLE
            return
        }

        answers[currentQuestion] = answer

        if (currentQuestion == QUESTIONS.lastIndex) {
            finalizeAssessment()
        } else {
            currentQuestion++
            selectedAnswer = answers[currentQuestion].takeIf { it >= 0 }
            render()
        }
    }

    private fun finalizeAssessment() {
        val result = calculateResult(answers)
        latestResult = result
        screen = Screen.RESULTS
        render()
        saveAssessment(result)
    }

    private fun renderResults() {
        val result = latestResult ?: calculateResult(answers).also { latestResult = it }

        tvEyebrow.text = "Completed"
        tvTitle.text = "Assessment Results"
        tvSubtitle.text = "Your screening result is ready."

        progress.visibility = View.VISIBLE
        progress.progress = 100
        progressMetaRow.visibility = View.GONE

        resultsContainer.visibility = View.VISIBLE

        tvRiskLevel.text = result.level
        tvRiskLevel.setTextColor(riskColor(result.level))
        tvClinicalScore.text = "Clinical score: ${result.score} out of 100"

        tvPhqScore.text = result.phq9.toString()
        tvPhqLabel.text = "/ 27 • ${phq9Label(result.phq9)}"

        tvGadScore.text = result.gad7.toString()
        tvGadLabel.text = "/ 21 • ${gad7Label(result.gad7)}"

        tvRecommendation.text = recommendationFor(result.level)

        tvSaveStatus.text = when {
            isSaving -> "Saving assessment..."
            else -> ""
        }
    }
    private fun saveAssessment(result: AssessmentResult) {
        // ── Read from the same prefs key used by HomeActivity ──────────────
        val prefs       = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        var email    = ""
        var userId   = ""
        var fullName = "Patient"

        if (sessionData != null) {
            try {
                val json = JSONObject(sessionData)
                email    = json.optString("email")
                userId   = json.optString("id")
                fullName = json.optString("fullName", "Patient")
            } catch (_: Exception) { }
        }

        val assessmentId = "asm_${System.currentTimeMillis()}"

        val localRecord = JSONObject()
            .put("id",             assessmentId)
            .put("assessmentType", "Triage Assessment")
            .put("phq9Score",      result.phq9)
            .put("gad7Score",      result.gad7)
            .put("clinicalScore",  result.score)
            .put("riskLevel",      result.level)
            .put("status",         "Pending")
            .put("createdAt",      System.currentTimeMillis())

        saveLocalAssessment(email.ifBlank { "guest" }, localRecord)

        if (email.isBlank()) { tvSaveStatus.text = "Saved locally."; return }

        isSaving = true
        tvSaveStatus.text = "Saving assessment..."

        Thread {
            try {
                val body = JSONObject()
                    .put("email",          email)
                    .put("userId",         userId)
                    .put("patientName",    fullName)
                    .put("assessmentType", "Triage Assessment")
                    .put("phq9Score",      result.phq9)
                    .put("gad7Score",      result.gad7)
                    .put("totalScore",     result.total)
                    .put("clinicalScore",  result.score)
                    .put("riskLevel",      result.level)
                    .put("status",         "Pending")
                    .put("answers",        JSONArray(answers))
                    .toString()

                val json    = JSONObject(post("$apiBaseUrl/api/assessments/save", body))
                val success = json.optBoolean("success", true)

                runOnUiThread {
                    isSaving = false
                    tvSaveStatus.text = if (success) "Saved to your assessment history."
                    else "Saved locally. Backend sync failed."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isSaving = false
                    tvSaveStatus.text = "Saved locally. Backend sync failed."
                }
            }
        }.start()
    }

    private fun saveLocalAssessment(email: String, record: JSONObject) {
        val prefs = getSharedPreferences("therapea_assessments", MODE_PRIVATE)
        val key = "assessments_$email"
        val existing = prefs.getString(key, "[]") ?: "[]"

        val array = try {
            JSONArray(existing)
        } catch (e: Exception) {
            JSONArray()
        }

        val updated = JSONArray()
        updated.put(record)

        for (i in 0 until array.length()) {
            updated.put(array.getJSONObject(i))
        }

        prefs.edit().putString(key, updated.toString()).apply()
    }

    private fun calculateResult(values: List<Int>): AssessmentResult {
        val phq9 = values.take(9).sumOf { max(0, it) }
        val gad7 = values.drop(9).sumOf { max(0, it) }
        val total = phq9 + gad7
        val score = ((total / 48.0) * 100).roundToInt()

        val level = when {
            score >= 75 -> "High"
            score >= 50 -> "Moderate"
            score >= 25 -> "Mild"
            else -> "Low"
        }

        return AssessmentResult(phq9, gad7, total, score, level)
    }

    private fun post(urlString: String, body: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doInput = true
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        OutputStreamWriter(connection.outputStream).use {
            it.write(body)
            it.flush()
        }

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

    private fun recommendationFor(level: String): String {
        return when (level) {
            "High" -> "Your responses indicate a high level of distress. Please reach out to a mental health professional soon. A licensed clinician can create a personalized plan that addresses what you're going through."
            "Moderate" -> "Your responses point to a moderate level of distress. Support from a licensed therapist or counselor is recommended. Effective, evidence-based treatment options are available."
            "Mild" -> "Your responses suggest some mild symptoms. Talking with a therapist can give you useful tools for what you're experiencing."
            else -> "Your responses show minimal symptoms right now. Keep up the habits that support your wellbeing, and consider a check-in every few months."
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class Screen {
        INTRO,
        QUIZ,
        RESULTS
    }

    private data class AssessmentResult(
        val phq9: Int,
        val gad7: Int,
        val total: Int,
        val score: Int,
        val level: String
    )

    private data class Question(
        val section: String,
        val text: String,
        val sensitive: Boolean = false
    )

    private data class Option(
        val label: String,
        val value: Int
    )

    companion object {
        private val QUESTIONS = listOf(
            Question("Mood", "Over the last 2 weeks, how often have you had little interest or pleasure in doing things?"),
            Question("Mood", "Over the last 2 weeks, how often have you felt down, depressed, or hopeless?"),
            Question("Mood", "Over the last 2 weeks, how often have you had trouble falling or staying asleep, or sleeping too much?"),
            Question("Mood", "Over the last 2 weeks, how often have you felt tired or had little energy?"),
            Question("Mood", "Over the last 2 weeks, how often have you had poor appetite or been overeating?"),
            Question("Mood", "Over the last 2 weeks, how often have you felt bad about yourself — like you let yourself or your family down?"),
            Question("Mood", "Over the last 2 weeks, how often have you had trouble concentrating on things like reading or watching TV?"),
            Question("Mood", "Over the last 2 weeks, how often have you moved or spoken so slowly others might have noticed — or been unusually fidgety?"),
            Question("Mood", "Over the last 2 weeks, have you had thoughts of hurting yourself or that you would be better off dead?", true),
            Question("Anxiety", "Over the last 2 weeks, how often have you felt nervous, anxious, or on edge?"),
            Question("Anxiety", "Over the last 2 weeks, how often have you been unable to stop or control worrying?"),
            Question("Anxiety", "Over the last 2 weeks, how often have you worried too much about different things?"),
            Question("Anxiety", "Over the last 2 weeks, how often have you had trouble relaxing?"),
            Question("Anxiety", "Over the last 2 weeks, how often have you been so restless it was hard to sit still?"),
            Question("Anxiety", "Over the last 2 weeks, how often have you become easily annoyed or irritable?"),
            Question("Anxiety", "Over the last 2 weeks, how often have you felt afraid something awful might happen?")
        )

        private val OPTIONS = listOf(
            Option("Not at all", 0),
            Option("Several days", 1),
            Option("More than half the days", 2),
            Option("Nearly every day", 3)
        )
    }
}