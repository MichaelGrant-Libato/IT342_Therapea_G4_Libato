package com.therapea.app.features.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.therapea.app.R
import com.therapea.app.features.appointments.AppointmentsActivity
import com.therapea.app.features.assessment.AssessmentActivity
import com.therapea.app.features.assessment.AssessmentHistoryActivity
import com.therapea.app.features.assessment.AssessmentResultActivity
import com.therapea.app.features.auth.LoginActivity
import com.therapea.app.features.emergencyMap.EmergencyMapActivity
import com.therapea.app.features.map.FindTherapistActivity
import com.therapea.app.features.messages.MessagesActivity
import com.therapea.app.features.profile.ProfileActivity
import com.therapea.app.features.progress.ProgressActivity
import com.therapea.app.features.video.VideoRoomActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class HomeActivity : Activity() {

    private val API_BASE_URL = "http://10.0.2.2:8083"
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var userEmail = ""
    private lateinit var drawerLayout: DrawerLayout

    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_patient)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)
        val btnMenu = findViewById<ImageView>(R.id.btnMenu)
        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        if (sessionData == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        try {
            val userJson = JSONObject(sessionData)
            userEmail = userJson.optString("email")
            val fullName = userJson.optString("fullName")
            val firstName = fullName.split(" ").firstOrNull() ?: "there"
            val picUrl = userJson.optString("profilePictureUrl", "")

            findViewById<TextView>(R.id.tvWelcomeName).text = "Good to see you, $firstName"
            findViewById<TextView>(R.id.tvAvatarInitials).apply {
                text = firstName.take(1).uppercase()
                background = createRoundedBg(R.color.primary, 100f)
            }

            val headerView = navView.getHeaderView(0)
            headerView.findViewById<TextView>(R.id.tvNavHeaderName).text = fullName
            headerView.findViewById<TextView>(R.id.tvNavHeaderEmail).text = userEmail
            val tvNavInitials = headerView.findViewById<TextView>(R.id.tvNavHeaderInitials)
            val ivNavAvatar = headerView.findViewById<ImageView>(R.id.ivNavHeaderAvatar)

            if (picUrl.isNotBlank()) {
                tvNavInitials.visibility = View.GONE
                ivNavAvatar.clipToOutline = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = client.newCall(Request.Builder().url(picUrl).get().build()).execute()
                        val bmp = android.graphics.BitmapFactory.decodeStream(res.body?.byteStream())
                        withContext(Dispatchers.Main) { ivNavAvatar.setImageBitmap(bmp) }
                    } catch (_: Exception) { }
                }
            } else {
                tvNavInitials.text = fullName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("").ifBlank { "?" }
                tvNavInitials.visibility = View.VISIBLE
            }

            findViewById<View>(R.id.btnNotifications).setOnClickListener {
                startActivity(Intent(this, NotificationsActivity::class.java))
            }

            fetchDashboardData()
            startNotifPolling()
        } catch (e: Exception) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {}
                R.id.nav_appointments -> startActivity(Intent(this, AppointmentsActivity::class.java))
                R.id.nav_therapists -> startActivity(Intent(this, FindTherapistActivity::class.java))
                R.id.nav_assessments -> startActivity(Intent(this, AssessmentHistoryActivity::class.java))
                R.id.nav_progress -> startActivity(Intent(this, ProgressActivity::class.java))
                R.id.nav_messages -> startActivity(Intent(this, MessagesActivity::class.java))
                R.id.nav_emergency -> startActivity(Intent(this, EmergencyMapActivity::class.java))
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_logout -> showLogoutConfirmation()
            }
            true
        }

        findViewById<TextView>(R.id.btnNewAssessment).apply {
            background = createRoundedBg(R.color.text_main, 100f)
            setOnClickListener { startActivity(Intent(this@HomeActivity, AssessmentActivity::class.java)) }
        }

        findViewById<TextView>(R.id.btnFindCare).apply {
            background = createRoundedBg(R.color.primary, 100f)
            setOnClickListener { startActivity(Intent(this@HomeActivity, FindTherapistActivity::class.java)) }
        }
    }

    private fun startNotifPolling() {
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val req = Request.Builder().url("$API_BASE_URL/api/notifications?email=${URLEncoder.encode(userEmail, "UTF-8")}").build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        JSONObject(res.body?.string() ?: "{}").optJSONArray("notifications")?.let {
                            withContext(Dispatchers.Main) { updateNotifBadge(it) }
                        }
                    }
                } catch (_: Exception) {}
                delay(8000)
            }
        }
    }

    private fun updateNotifBadge(notifs: JSONArray) {
        val unread = (0 until notifs.length()).count { !notifs.getJSONObject(it).optBoolean("read") }
        findViewById<TextView>(R.id.tvNotifBadge).apply {
            visibility = if (unread > 0) View.VISIBLE else View.GONE
            text = if (unread > 9) "9+" else unread.toString()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this).setTitle("Sign out of TheraPea?").setMessage("You will need to sign back in.")
            .setPositiveButton("Sign Out") { _, _ ->
                getSharedPreferences("TheraPeaSession", MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java)); finish()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun fetchDashboardData() = scope.launch(Dispatchers.IO) {
        try {
            val aptData = JSONObject(client.newCall(Request.Builder().url("$API_BASE_URL/api/appointments/user?email=$userEmail").build()).execute().body?.string() ?: "{}")
            val asmtData = JSONObject(client.newCall(Request.Builder().url("$API_BASE_URL/api/assessments/user?email=$userEmail").build()).execute().body?.string() ?: "{}")
            withContext(Dispatchers.Main) {
                if (aptData.optBoolean("success")) updateAppointmentsUI(aptData.optJSONArray("appointments"))
                if (asmtData.optBoolean("success")) updateAssessmentsUI(asmtData.optJSONArray("assessments"))
            }
        } catch (_: Exception) {}
    }

    private fun updateAppointmentsUI(appointments: JSONArray?) {
        val upcomingApt = (0 until (appointments?.length() ?: 0)).map { appointments!!.getJSONObject(it) }.firstOrNull { it.optString("status") == "Scheduled" }

        findViewById<TextView>(R.id.tvSessionBadge).apply {
            background = createRoundedBg(R.color.primary_light, 100f)
            setTextColor(getColor(R.color.primary_dark))
        }

        if (upcomingApt != null) {
            findViewById<View>(R.id.llEmptySession).visibility = View.GONE
            findViewById<View>(R.id.llSessionDetails).visibility = View.VISIBLE
            findViewById<View>(R.id.tvSessionBadge).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvProviderName).text = upcomingApt.optString("providerName", "Dr. Unknown")
            findViewById<TextView>(R.id.tvSessionTime).text = "${upcomingApt.optString("date")} at ${upcomingApt.optString("time")}"

            findViewById<TextView>(R.id.btnJoinSession).apply {
                background = createRoundedBg(R.color.primary, 100f)
                setOnClickListener { startActivity(Intent(this@HomeActivity, VideoRoomActivity::class.java).putExtra("appointmentId", upcomingApt.optString("id"))) }
            }
        } else {
            findViewById<View>(R.id.llSessionDetails).visibility = View.GONE
            findViewById<View>(R.id.tvSessionBadge).visibility = View.GONE
            findViewById<View>(R.id.llEmptySession).visibility = View.VISIBLE
            findViewById<TextView>(R.id.btnBookSession).apply {
                background = createRoundedBg(R.color.text_main, 100f)
                setOnClickListener { startActivity(Intent(this@HomeActivity, AppointmentsActivity::class.java)) }
            }
        }
    }

    private fun updateAssessmentsUI(assessments: JSONArray?) {
        val list = findViewById<LinearLayout>(R.id.llAssessmentsList)
        list.removeAllViews()
        if (assessments == null || assessments.length() == 0) {
            findViewById<View>(R.id.tvEmptyAssessments).visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        findViewById<View>(R.id.tvEmptyAssessments).visibility = View.GONE
        list.visibility = View.VISIBLE
        val limit = minOf(assessments.length(), 5)

        for (i in 0 until limit) {
            val a = assessments.getJSONObject(i)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 36, 0, 36)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { startActivity(Intent(this@HomeActivity, AssessmentResultActivity::class.java).putExtra("assessmentId", a.optString("id"))) }
            }

            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            textCol.addView(TextView(this).apply { text = a.optString("assessmentType", "Assessment"); setTextColor(getColor(R.color.text_main)); textSize = 15f; setTypeface(null, Typeface.BOLD) })
            textCol.addView(TextView(this).apply { text = a.optString("createdAt").split("T").firstOrNull() ?: ""; setTextColor(getColor(R.color.text_muted)); textSize = 13f; setPadding(0, 4, 0, 0) })

            val risk = a.optString("riskLevel", "Low")
            val (bg, txt, border) = when (risk) {
                "High" -> Triple(R.color.alert_bg, R.color.alert_text, R.color.alert_border)
                "Moderate", "Mild" -> Triple(R.color.amber_light, R.color.amber, R.color.amber_light)
                else -> Triple(R.color.primary_light, R.color.primary_dark, R.color.primary_light)
            }

            row.addView(textCol)
            row.addView(TextView(this).apply { text = "$risk Risk"; setTextColor(getColor(txt)); textSize = 12f; setTypeface(null, Typeface.BOLD); setPadding(24, 10, 24, 10); background = createRoundedBorderBg(bg, border, 100f) })
            list.addView(row)

            if (i < limit - 1) list.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2); setBackgroundColor(getColor(R.color.border_subtle)) })
        }
    }

    private fun createRoundedBg(colorRes: Int, radius: Float) = GradientDrawable().apply { setColor(getColor(colorRes)); cornerRadius = radius }
    private fun createRoundedBorderBg(fillRes: Int, strokeRes: Int, radius: Float) = GradientDrawable().apply { setColor(getColor(fillRes)); setStroke(2, getColor(strokeRes)); cornerRadius = radius }

    override fun onResume() { super.onResume(); startNotifPolling() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}