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

class HomeActivity : Activity() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val API_BASE_URL = "http://10.0.2.2:8083"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_patient)

        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val userDataStr = prefs.getString("user_data", null) ?: prefs.getString("temp_session", null)

        if (userDataStr == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userObj = JSONObject(userDataStr)
        val role = userObj.optString("role", "PATIENT")

        if (role == "DOCTOR") {
            startActivity(Intent(this, DoctorHomeActivity::class.java))
            finish()
            return
        }

        val email = userObj.optString("email", "")
        val fullName = userObj.optString("fullName", "User")

        findViewById<TextView>(R.id.tvWelcomeName).text = "Good to see you, ${fullName.split(" ")[0]}"

        findViewById<ImageView>(R.id.btnLogout).setOnClickListener {
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadPatientData(email)
    }

    private fun loadPatientData(email: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val reqAss = Request.Builder().url("$API_BASE_URL/api/assessments/user?email=$email").build()
                val resAss = client.newCall(reqAss).execute()
                val dataAss = JSONObject(resAss.body?.string() ?: "{}")

                val reqApt = Request.Builder().url("$API_BASE_URL/api/appointments/user?email=$email").build()
                val resApt = client.newCall(reqApt).execute()
                val dataApt = JSONObject(resApt.body?.string() ?: "{}")

                withContext(Dispatchers.Main) {
                    if (dataAss.optBoolean("success", false)) {
                        val list = dataAss.optJSONArray("assessments") ?: JSONArray()
                        populateAssessments(list)
                    }
                    if (dataApt.optBoolean("success", false)) {
                        val list = dataApt.optJSONArray("appointments") ?: JSONArray()
                        populateNextSession(list)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun populateAssessments(list: JSONArray) {
        val container = findViewById<LinearLayout>(R.id.listAssessments)
        val emptyState = findViewById<TextView>(R.id.tvEmptyAssessments)
        container.removeAllViews()

        if (list.length() > 0) emptyState.visibility = View.GONE

        for (i in 0 until minOf(list.length(), 5)) {
            val item = list.getJSONObject(i)
            val tv = TextView(this).apply {
                text = "• ${item.optString("assessmentType")}  |  Risk: ${item.optString("riskLevel")}\n  Status: ${item.optString("status")}"
                setPadding(0, 16, 0, 16)
                setTextColor(Color.parseColor("#4A5047"))
            }
            container.addView(tv)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#ECEEE8"))
            }
            container.addView(divider)
        }
    }

    private fun populateNextSession(list: JSONArray) {
        val tvNext = findViewById<TextView>(R.id.tvNextSession)
        for (i in 0 until list.length()) {
            val apt = list.getJSONObject(i)
            if (apt.optString("status") == "Scheduled") {
                tvNext.text = "${apt.optString("date")} at ${apt.optString("time")}\nWith ${apt.optString("providerName")}"
                tvNext.setTextColor(Color.parseColor("#1D4ED8"))
                return
            }
        }
    }
}