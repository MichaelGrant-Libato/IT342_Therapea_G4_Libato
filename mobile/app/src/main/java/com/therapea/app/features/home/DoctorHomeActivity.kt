package com.therapea.app.features.home

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.therapea.app.R
import com.therapea.app.features.auth.LoginActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class DoctorHomeActivity : Activity() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val API_BASE_URL = "http://10.0.2.2:8083"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_doctor)

        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val userDataStr = prefs.getString("user_data", null) ?: prefs.getString("temp_session", null)

        if (userDataStr == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userObj = JSONObject(userDataStr)
        val role = userObj.optString("role", "PATIENT")

        if (role != "DOCTOR") {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        val email = userObj.optString("email", "")
        val fullName = userObj.optString("fullName", "Doctor")

        findViewById<TextView>(R.id.tvWelcomeName).text = "Good to see you, Dr. ${fullName.split(" ")[0]}"

        findViewById<ImageView>(R.id.btnLogout).setOnClickListener {
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadDoctorData(email)
    }

    private fun loadDoctorData(email: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val reqAss = Request.Builder().url("$API_BASE_URL/api/assessments/doctor-queue?doctorEmail=$email").build()
                val reqApt = Request.Builder().url("$API_BASE_URL/api/appointments/user?email=$email").build()
                val reqPat = Request.Builder().url("$API_BASE_URL/api/patients/doctor?email=$email").build()

                val resAss = client.newCall(reqAss).execute()
                val resApt = client.newCall(reqApt).execute()
                val resPat = client.newCall(reqPat).execute()

                val dataAss = JSONObject(resAss.body?.string() ?: "{}")
                val dataApt = JSONObject(resApt.body?.string() ?: "{}")
                val dataPat = JSONObject(resPat.body?.string() ?: "{}")

                withContext(Dispatchers.Main) {
                    val assessments = dataAss.optJSONArray("assessments") ?: JSONArray()
                    val appointments = dataApt.optJSONArray("appointments") ?: JSONArray()
                    val patients = dataPat.optJSONArray("patients") ?: JSONArray()

                    updateStats(assessments, appointments, patients)
                    populateTriageQueue(assessments)
                }
            } catch (e: Exception) { }
        }
    }

    private fun updateStats(assessments: JSONArray, appointments: JSONArray, patients: JSONArray) {
        var pendingCount = 0
        var urgentCount = 0
        var upcomingCount = 0

        for (i in 0 until assessments.length()) {
            val item = assessments.getJSONObject(i)
            if (item.optString("status") == "Pending") {
                pendingCount++
                val risk = item.optString("riskLevel")
                if (risk == "High" || risk == "Moderate") urgentCount++
            }
        }

        for (i in 0 until appointments.length()) {
            if (appointments.getJSONObject(i).optString("status") == "Scheduled") upcomingCount++
        }

        findViewById<TextView>(R.id.tvStatAppointments).text = upcomingCount.toString()
        findViewById<TextView>(R.id.tvStatPatients).text = patients.length().toString()
        findViewById<TextView>(R.id.tvStatPending).text = pendingCount.toString()
        findViewById<TextView>(R.id.tvStatAlerts).text = urgentCount.toString()
    }

    private fun populateTriageQueue(list: JSONArray) {
        val container = findViewById<LinearLayout>(R.id.listTriage)
        val emptyState = findViewById<TextView>(R.id.tvEmptyTriage)
        container.removeAllViews()

        var pendingShown = 0

        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            if (item.optString("status") == "Pending" && pendingShown < 5) {
                emptyState.visibility = View.GONE

                val risk = item.optString("riskLevel")
                val colorStr = if (risk == "High") "#991B1B" else "#1C1F1A"

                val tv = TextView(this).apply {
                    text = "• Patient: ${item.optString("patientName", "Unknown")}\n  Risk: $risk  |  Type: ${item.optString("assessmentType")}"
                    setPadding(0, 16, 0, 16)
                    setTextColor(Color.parseColor(colorStr))
                }
                container.addView(tv)
                pendingShown++

                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#ECEEE8"))
                }
                container.addView(divider)
            }
        }
    }
}