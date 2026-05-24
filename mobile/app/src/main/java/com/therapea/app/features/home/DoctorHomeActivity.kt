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
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.therapea.app.R
import com.therapea.app.features.appointments.AppointmentsActivity
import com.therapea.app.features.assessment.AssessmentResultActivity
import com.therapea.app.features.auth.LoginActivity
import com.therapea.app.features.emergencyMap.EmergencyMapActivity
import com.therapea.app.features.messages.MessagesActivity
import com.therapea.app.features.profile.ProfileActivity
import com.therapea.app.features.progress.ProgressActivity
import com.therapea.app.features.therapists.PatientsActivity
import com.therapea.app.features.therapists.TherapistProfileActivity
import com.therapea.app.features.video.VideoRoomActivity
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class DoctorHomeActivity : Activity() {

    private val API_BASE_URL = "http://10.0.2.2:8083"
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var doctorEmail = ""
    private lateinit var drawerLayout: DrawerLayout

    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_doctor)

        drawerLayout = findViewById(R.id.drawerLayoutDoctor)
        val navView = findViewById<NavigationView>(R.id.navViewDoctor)
        val btnMenu = findViewById<ImageView>(R.id.btnMenuDoctor)
        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        if (sessionData == null) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }

        try {
            val userJson = JSONObject(sessionData)
            doctorEmail = userJson.optString("email")
            val fullName = userJson.optString("fullName")
            val firstName = fullName.split(" ").firstOrNull() ?: "Doctor"
            val picUrl = userJson.optString("profilePictureUrl", "")

            findViewById<TextView>(R.id.tvDocWelcomeName).text = "Good to see you, Dr. $firstName"

            // ── AVATAR & INITIALS SETUP ──
            val tvDocInitials = findViewById<TextView>(R.id.tvDocAvatarInitials)
            val ivDocAvatar = findViewById<ImageView?>(R.id.ivDocAvatar)

            val headerView = navView.getHeaderView(0)
            headerView.findViewById<TextView>(R.id.tvNavHeaderName).text = "Dr. $fullName"
            headerView.findViewById<TextView>(R.id.tvNavHeaderEmail).text = doctorEmail

            val tvNavInitials = headerView.findViewById<TextView>(R.id.tvNavHeaderInitials)
            val ivNavAvatar = headerView.findViewById<ImageView>(R.id.ivNavHeaderAvatar)

            // Set default initials & backgrounds first
            tvDocInitials.apply {
                text = firstName.take(1).uppercase()
                background = createRoundedBg(R.color.secondary, 100f)
            }
            tvNavInitials.background = createRoundedBg(R.color.secondary, 100f)

            // Fetch and crop the image if a URL exists
            if (picUrl.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = client.newCall(Request.Builder().url(picUrl).get().build()).execute()
                        val bmp = android.graphics.BitmapFactory.decodeStream(res.body?.byteStream())

                        withContext(Dispatchers.Main) {
                            if (bmp != null) {
                                // Create perfectly circular drawable
                                val circularDrawable = RoundedBitmapDrawableFactory.create(resources, bmp)
                                circularDrawable.isCircular = true

                                // Apply to Navigation Drawer
                                ivNavAvatar.setImageDrawable(circularDrawable)
                                ivNavAvatar.visibility = View.VISIBLE
                                tvNavInitials.visibility = View.GONE

                                // Apply to Top Dashboard Header
                                ivDocAvatar?.setImageDrawable(circularDrawable)
                                ivDocAvatar?.visibility = View.VISIBLE
                                tvDocInitials.visibility = View.GONE
                            }
                        }
                    } catch (_: Exception) {
                        // Silently fail and leave the initials visible if the download fails
                    }
                }
            } else {
                // No picture URL provided, just show initials
                val navInitialsStr = fullName.split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .take(2)
                    .joinToString("")
                    .ifBlank { "D" }

                tvNavInitials.text = navInitialsStr
                tvNavInitials.visibility = View.VISIBLE
                ivNavAvatar.visibility = View.GONE

                tvDocInitials.visibility = View.VISIBLE
                ivDocAvatar?.visibility = View.GONE
            }

            findViewById<View>(R.id.btnNotifications).setOnClickListener {
                startActivity(Intent(this, NotificationsActivity::class.java))
            }

            fetchDoctorDashboardData()
            startNotifPolling()
        } catch (e: Exception) {
            startActivity(Intent(this, LoginActivity::class.java)); finish()
        }

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_doc_dashboard -> {}
                R.id.nav_doc_appointments -> startActivity(Intent(this, AppointmentsActivity::class.java))
                R.id.nav_doc_patients -> startActivity(Intent(this, PatientsActivity::class.java))
                R.id.nav_doc_progress -> startActivity(Intent(this, ProgressActivity::class.java))
                R.id.nav_doc_messages -> startActivity(Intent(this, MessagesActivity::class.java))
                R.id.nav_doc_emergency -> startActivity(Intent(this, EmergencyMapActivity::class.java))
                R.id.nav_doc_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_doc_logout -> showLogoutConfirmation()
            }
            true
        }

        findViewById<View>(R.id.btnViewAllSchedule).setOnClickListener { startActivity(Intent(this, AppointmentsActivity::class.java)) }
        findViewById<View>(R.id.btnViewAllPatients).setOnClickListener { startActivity(Intent(this, PatientsActivity::class.java)) }
    }

    private fun startNotifPolling() {
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val req = Request.Builder().url("$API_BASE_URL/api/notifications?email=${URLEncoder.encode(doctorEmail, "UTF-8")}").build()
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

    private fun fetchDoctorDashboardData() = scope.launch(Dispatchers.IO) {
        try {
            val triageData = JSONObject(client.newCall(Request.Builder().url("$API_BASE_URL/api/assessments/doctor-queue?doctorEmail=$doctorEmail").build()).execute().body?.string() ?: "{}")
            val aptData = JSONObject(client.newCall(Request.Builder().url("$API_BASE_URL/api/appointments/user?email=$doctorEmail").build()).execute().body?.string() ?: "{}")
            val patData = JSONObject(client.newCall(Request.Builder().url("$API_BASE_URL/api/patients/doctor?email=$doctorEmail").build()).execute().body?.string() ?: "{}")

            withContext(Dispatchers.Main) {
                val tList = triageData.optJSONArray("assessments") ?: JSONArray()
                val aList = aptData.optJSONArray("appointments") ?: JSONArray()
                val pList = patData.optJSONArray("patients") ?: JSONArray()

                updateStatsUI(tList, aList, pList)
                updateTriageUI(tList)
                updateScheduleUI(aList)
                updatePatientsUI(pList)
            }
        } catch (_: Exception) {}
    }

    private fun updateStatsUI(triageList: JSONArray, aptList: JSONArray, patList: JSONArray) {
        val pendingCount = (0 until triageList.length()).count { triageList.getJSONObject(it).optString("status") == "Pending" }
        val urgentCount = (0 until triageList.length()).count { val o = triageList.getJSONObject(it); o.optString("status") == "Pending" && (o.optString("riskLevel") == "High" || o.optString("riskLevel") == "Moderate") }
        val upcomingCount = (0 until aptList.length()).count { aptList.getJSONObject(it).optString("status") == "Scheduled" }

        findViewById<TextView>(R.id.tvStatAppts).text = upcomingCount.toString()
        findViewById<TextView>(R.id.tvStatPending).text = pendingCount.toString()
        findViewById<TextView>(R.id.tvStatPatients).text = patList.length().toString()
        findViewById<TextView>(R.id.tvStatUrgent).text = urgentCount.toString()
    }

    private fun updateTriageUI(assessments: JSONArray) {
        val list = findViewById<LinearLayout>(R.id.llTriageList)
        val tvBadge = findViewById<TextView>(R.id.tvPendingBadge)
        list.removeAllViews()

        val pending = (0 until assessments.length()).map { assessments.getJSONObject(it) }.filter { it.optString("status") == "Pending" }
        if (pending.isEmpty()) {
            tvBadge.visibility = View.GONE
            findViewById<View>(R.id.tvEmptyTriage).visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        tvBadge.apply { visibility = View.VISIBLE; text = "${pending.size} pending"; background = createRoundedBg(R.color.alert_bg, 100f) }
        findViewById<View>(R.id.tvEmptyTriage).visibility = View.GONE
        list.visibility = View.VISIBLE

        for (i in 0 until minOf(pending.size, 5)) {
            val a = pending[i]; val isUrgent = a.optString("riskLevel") == "High"

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
                background = createRoundedBorderBg(if (isUrgent) R.color.alert_bg else R.color.bg, if (isUrgent) R.color.alert_border else R.color.border_subtle, 12f)
            }

            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
            header.addView(TextView(this).apply { text = a.optString("createdAt").split("T").firstOrNull() ?: ""; setTextColor(getColor(R.color.text_muted)); textSize = 12f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            header.addView(TextView(this).apply { text = "${a.optString("riskLevel")} Risk"; setTextColor(getColor(if (isUrgent) R.color.alert_text else R.color.primary_dark)); textSize = 12f; setTypeface(null, Typeface.BOLD) })

            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btnRow.addView(TextView(this).apply { text = "View Results"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.text_sub)); textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(24, 16, 24, 16); background = createRoundedBorderBg(R.color.surface, R.color.border_main, 12f); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 16, 0) }; setOnClickListener { startActivity(Intent(this@DoctorHomeActivity, AssessmentResultActivity::class.java).putExtra("assessmentId", a.optString("id"))) } })
            btnRow.addView(TextView(this).apply { text = "Mark Reviewed"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.white)); textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(24, 16, 24, 16); background = createRoundedBg(R.color.text_main, 12f); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); setOnClickListener { markAsReviewed(a.optString("id")) } })

            row.addView(header)
            row.addView(TextView(this).apply { text = "${a.optString("patientName")} • ${a.optString("assessmentType")}"; setTextColor(getColor(R.color.text_main)); textSize = 15f; setTypeface(null, Typeface.BOLD); setPadding(0, 12, 0, 24) })
            row.addView(btnRow)
            list.addView(row)
        }
    }

    private fun markAsReviewed(id: String) = scope.launch(Dispatchers.IO) {
        try { if (client.newCall(Request.Builder().url("$API_BASE_URL/api/assessments/$id/review").patch("".toRequestBody("application/json".toMediaTypeOrNull())).build()).execute().isSuccessful) withContext(Dispatchers.Main) { fetchDoctorDashboardData() } } catch (_: Exception) { }
    }

    private fun updateScheduleUI(appointments: JSONArray) {
        val list = findViewById<LinearLayout>(R.id.llScheduleList)
        list.removeAllViews()
        val sched = (0 until appointments.length()).map { appointments.getJSONObject(it) }.filter { it.optString("status") == "Scheduled" }

        if (sched.isEmpty()) {
            findViewById<View>(R.id.tvEmptySchedule).visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        findViewById<View>(R.id.tvEmptySchedule).visibility = View.GONE
        list.visibility = View.VISIBLE

        for (i in 0 until minOf(sched.size, 3)) {
            val apt = sched[i]
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(32, 32, 32, 32); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; background = createRoundedBorderBg(R.color.surface, R.color.border_main, 20f) }

            val timeCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(250, LinearLayout.LayoutParams.WRAP_CONTENT) }
            timeCol.addView(TextView(this).apply { text = apt.optString("date"); setTextColor(getColor(R.color.text_muted)); textSize = 12f })
            timeCol.addView(TextView(this).apply { text = apt.optString("time"); setTextColor(getColor(R.color.text_main)); textSize = 14f; setTypeface(null, Typeface.BOLD) })

            val detailsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            detailsCol.addView(TextView(this).apply { text = apt.optString("patientName"); setTextColor(getColor(R.color.text_main)); textSize = 15f; setTypeface(null, Typeface.BOLD) })
            detailsCol.addView(TextView(this).apply { text = apt.optString("type"); setTextColor(getColor(R.color.text_muted)); textSize = 13f })

            row.addView(timeCol); row.addView(detailsCol)

            if (apt.optString("type") == "Telehealth") {
                row.addView(TextView(this).apply { text = "Join"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.white)); setTypeface(null, Typeface.BOLD); background = createRoundedBg(R.color.text_main, 100f); setPadding(32, 12, 32, 12); setOnClickListener { startActivity(Intent(this@DoctorHomeActivity, VideoRoomActivity::class.java).putExtra("appointmentId", apt.optString("id"))) } })
            } else {
                row.addView(TextView(this).apply { text = "Clinic"; setTextColor(getColor(R.color.text_sub)); setTypeface(null, Typeface.BOLD); background = createRoundedBg(R.color.bg_alt, 100f); setPadding(24, 10, 24, 10) })
            }
            list.addView(row)
        }
    }

    private fun updatePatientsUI(patients: JSONArray) {
        val list = findViewById<LinearLayout>(R.id.llPatientsList)
        list.removeAllViews()

        if (patients.length() == 0) {
            findViewById<View>(R.id.tvEmptyPatients).visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        findViewById<View>(R.id.tvEmptyPatients).visibility = View.GONE
        list.visibility = View.VISIBLE
        val limit = minOf(patients.length(), 3)

        for (i in 0 until limit) {
            val p = patients.getJSONObject(i)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 32, 0, 32); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }

            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            col.addView(TextView(this).apply { text = p.optString("name"); setTextColor(getColor(R.color.text_main)); textSize = 15f; setTypeface(null, Typeface.BOLD) })
            col.addView(TextView(this).apply { text = p.optString("email"); setTextColor(getColor(R.color.text_muted)); textSize = 13f })

            row.addView(col)
            row.addView(TextView(this).apply { text = "Profile"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.text_sub)); setTypeface(null, Typeface.BOLD); background = createRoundedBorderBg(R.color.surface, R.color.border_main, 12f); setPadding(32, 12, 32, 12); setOnClickListener { startActivity(Intent(this@DoctorHomeActivity, ProfileActivity::class.java).putExtra("patientEmail", p.optString("email"))) } })
            list.addView(row)

            if (i < limit - 1) list.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2); setBackgroundColor(getColor(R.color.border_subtle)) })
        }
    }

    private fun createRoundedBg(colorRes: Int, r: Float) = GradientDrawable().apply { setColor(getColor(colorRes)); cornerRadius = r }
    private fun createRoundedBorderBg(fillRes: Int, strokeRes: Int, r: Float) = GradientDrawable().apply { setColor(getColor(fillRes)); setStroke(3, getColor(strokeRes)); cornerRadius = r }

    override fun onResume() { super.onResume(); startNotifPolling() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}