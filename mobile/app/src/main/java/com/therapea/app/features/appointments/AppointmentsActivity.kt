package com.therapea.app.features.appointments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.therapea.app.R
import com.therapea.app.features.map.FindTherapistActivity
import com.therapea.app.features.video.VideoRoomActivity
import com.therapea.app.ui.TheraPeaDialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppointmentsActivity : Activity() {

    private lateinit var tvTitle:                  TextView
    private lateinit var tvSubtitle:               TextView
    private lateinit var btnBookSession:           Button
    private lateinit var btnUpcoming:              Button
    private lateinit var btnPast:                  Button
    private lateinit var btnCalendar:              Button
    private lateinit var tvMessage:                TextView
    private lateinit var appointmentsListContainer: LinearLayout
    private lateinit var calendarContainer:        LinearLayout
    private lateinit var tvCalendarMonth:          TextView
    private lateinit var btnPrevMonth:             Button
    private lateinit var btnNextMonth:             Button
    private lateinit var calendarGrid:             GridLayout
    private lateinit var selectedDayContainer:     LinearLayout

    private var activeTab       = Tab.UPCOMING
    private var isLoading       = false
    private var isActionLoading = false
    private var userEmail       = ""
    private var userRole        = "PATIENT"

    private val appointments = mutableListOf<AppointmentData>()
    private val currentMonth = Calendar.getInstance()
    private val apiBaseUrl   = "http://10.0.2.2:8083"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointments)
        bindViews()
        readUserSession()
        setupListeners()
        render()
        loadAppointments()
    }

    private fun bindViews() {
        tvTitle                  = findViewById(R.id.tvAppointmentsTitle)
        tvSubtitle               = findViewById(R.id.tvAppointmentsSubtitle)
        btnBookSession           = findViewById(R.id.btnBookSession)
        btnUpcoming              = findViewById(R.id.btnUpcoming)
        btnPast                  = findViewById(R.id.btnPast)
        btnCalendar              = findViewById(R.id.btnCalendar)
        tvMessage                = findViewById(R.id.tvAppointmentsMessage)
        appointmentsListContainer = findViewById(R.id.appointmentsListContainer)
        calendarContainer        = findViewById(R.id.calendarContainer)
        tvCalendarMonth          = findViewById(R.id.tvCalendarMonth)
        btnPrevMonth             = findViewById(R.id.btnPrevMonth)
        btnNextMonth             = findViewById(R.id.btnNextMonth)
        calendarGrid             = findViewById(R.id.calendarGrid)
        selectedDayContainer     = findViewById(R.id.selectedDayContainer)
    }

    private fun readUserSession() {
        val emailOverride = intent.getStringExtra("email")
        val roleOverride  = intent.getStringExtra("role")

        if (!emailOverride.isNullOrBlank()) {
            userEmail = emailOverride
            userRole  = (roleOverride ?: "PATIENT").uppercase()
            return
        }

        val prefs       = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        if (sessionData != null) {
            try {
                val json  = JSONObject(sessionData)
                userEmail = json.optString("email")
                userRole  = json.optString("role", "PATIENT").uppercase()
                return
            } catch (_: Exception) { }
        }

        userEmail = "patient@example.com"
        userRole  = "PATIENT"
    }

    private fun setupListeners() {
        btnUpcoming.setOnClickListener  { activeTab = Tab.UPCOMING;  render() }
        btnPast.setOnClickListener      { activeTab = Tab.PAST;      render() }
        btnCalendar.setOnClickListener  { activeTab = Tab.CALENDAR;  render() }
        btnBookSession.setOnClickListener {
            startActivity(Intent(this, FindTherapistActivity::class.java))
        }
        btnPrevMonth.setOnClickListener { currentMonth.add(Calendar.MONTH, -1); renderCalendar() }
        btnNextMonth.setOnClickListener { currentMonth.add(Calendar.MONTH,  1); renderCalendar() }
    }

    private fun loadAppointments() {
        isLoading = true; render()
        Thread {
            try {
                val loaded = parseAppointments(JSONObject(
                    get("$apiBaseUrl/api/appointments/user?email=${encode(userEmail)}")
                ))
                runOnUiThread { appointments.clear(); appointments.addAll(loaded); isLoading = false; render() }
            } catch (_: Exception) {
                runOnUiThread {
                    isLoading = false; appointments.clear()
                    Toast.makeText(this, "Failed to load appointments.", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }.start()
    }

    private fun cancelAppointment(appointment: AppointmentData, reason: String) {
        isActionLoading = true; render()
        Thread {
            try {
                patch("$apiBaseUrl/api/appointments/${appointment.id}/cancel", JSONObject().put("reason", reason).toString())
                runOnUiThread {
                    appointment.status = "Canceled"; isActionLoading = false
                    Toast.makeText(this, "Appointment canceled.", Toast.LENGTH_SHORT).show(); render()
                }
            } catch (_: Exception) {
                runOnUiThread { isActionLoading = false; Toast.makeText(this, "Failed to cancel.", Toast.LENGTH_SHORT).show(); render() }
            }
        }.start()
    }

    private fun deleteAppointment(appointment: AppointmentData) {
        isActionLoading = true; render()
        Thread {
            try {
                delete("$apiBaseUrl/api/appointments/${appointment.id}")
                runOnUiThread {
                    appointments.removeAll { it.id == appointment.id }; isActionLoading = false
                    Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show(); render()
                }
            } catch (_: Exception) {
                runOnUiThread { isActionLoading = false; Toast.makeText(this, "Failed to delete.", Toast.LENGTH_SHORT).show(); render() }
            }
        }.start()
    }

    // ── Render ─────────────────────────────────────────────────────────────
    private fun render() {
        updateHeader()
        updateTabs()

        tvMessage.visibility              = View.GONE
        appointmentsListContainer.visibility = View.GONE
        calendarContainer.visibility      = View.GONE
        selectedDayContainer.visibility   = View.GONE
        appointmentsListContainer.removeAllViews()
        selectedDayContainer.removeAllViews()

        if (isLoading) { showMessage("Loading appointments…"); return }

        when (activeTab) {
            Tab.UPCOMING -> renderAppointmentList(upcomingAppointments(), "No upcoming appointments.")
            Tab.PAST     -> renderAppointmentList(pastAppointments(), "No past sessions.")
            Tab.CALENDAR -> renderCalendar()
        }
    }

    private fun updateHeader() {
        val isDoctor = userRole == "DOCTOR"
        tvTitle.text    = if (isDoctor) "Your Schedule" else "Your Appointments"
        tvSubtitle.text = if (isDoctor) "Manage your upcoming sessions." else "View and manage your therapy sessions."
        btnBookSession.visibility = if (isDoctor) View.GONE else View.VISIBLE
        btnCalendar.visibility    = if (isDoctor) View.VISIBLE else View.GONE
        if (!isDoctor && activeTab == Tab.CALENDAR) activeTab = Tab.UPCOMING
    }

    private fun updateTabs() {
        listOf(btnUpcoming to Tab.UPCOMING, btnPast to Tab.PAST, btnCalendar to Tab.CALENDAR)
            .forEach { (btn, tab) ->
                val active = activeTab == tab
                btn.setBackgroundResource(if (active) R.drawable.admin_bg_button_primary else R.drawable.admin_bg_button_outline)
                btn.setTextColor(if (active) Color.WHITE else Color.parseColor("#1C1F1A"))
            }
    }

    private fun renderAppointmentList(list: List<AppointmentData>, emptyMsg: String) {
        appointmentsListContainer.visibility = View.VISIBLE
        if (list.isEmpty()) { appointmentsListContainer.addView(emptyState(emptyMsg)); return }
        list.forEach { appointmentsListContainer.addView(appointmentCard(it)) }
    }

    // ── Appointment card — web-style ──────────────────────────────────────
    private fun appointmentCard(apt: AppointmentData): LinearLayout {
        val isDoctor    = userRole == "DOCTOR"
        val displayName = if (isDoctor) apt.patientName.ifBlank { "Patient" }
        else           apt.providerName.ifBlank { "Provider" }

        // ── Determine session type ────────────────────────────────────────
        // "Telehealth" / "Online" → video call eligible
        // "In-Person" / "Clinic" / anything else → no video
        val isTelehealth = apt.type.contains("telehealth", ignoreCase = true) ||
                apt.type.contains("online",     ignoreCase = true) ||
                apt.type.contains("video",      ignoreCase = true)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = cardDrawable()
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        // ── Row 1: name + status badge ────────────────────────────────────
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        row1.addView(TextView(this).apply {
            text     = displayName
            textSize = 17f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1F1A"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row1.addView(statusBadge(apt.status))
        card.addView(row1)

        // ── Row 2: date + time ────────────────────────────────────────────
        card.addView(TextView(this).apply {
            text     = "📅  ${apt.date} at ${apt.time}"
            textSize = 13f
            setTextColor(Color.parseColor("#7A8077"))
            setPadding(0, dp(8), 0, 0)
        })

        // ── Row 3: type badge ─────────────────────────────────────────────
        card.addView(TextView(this).apply {
            text     = apt.type.ifBlank { "General Consultation" }
            textSize = 12f
            setTextColor(Color.parseColor("#4A5047"))
            setPadding(0, dp(4), 0, dp(2))
        })

        // ── Divider ───────────────────────────────────────────────────────
        card.addView(divider())

        // ── Action row: View Details + conditional right-side action ──────
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }

        // View Details — always shown
        actionRow.addView(ghostButton("View Details").also {
            it.setOnClickListener { showDetailsDialog(apt) }
        })

        actionRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        if (activeTab == Tab.UPCOMING) {
            if (isTelehealth) {
                // ── Telehealth: show Join Video ───────────────────────────
                actionRow.addView(videoButton("Join Video").also {
                    it.setOnClickListener {
                        startActivity(
                            Intent(this@AppointmentsActivity, VideoRoomActivity::class.java)
                                .putExtra("appointmentId", apt.id)
                        )
                    }
                })
            } else {
                actionRow.addView(clinicBadge(apt.type))
            }
        }

        card.addView(actionRow)

        // ── Cancel button (patient upcoming only) ─────────────────────────
        if (activeTab == Tab.UPCOMING && userRole == "PATIENT") {
            card.addView(dangerTextButton(if (isActionLoading) "Processing…" else "Cancel Appointment").also {
                it.isEnabled = !isActionLoading
                it.setOnClickListener { showCancelDialog(apt) }
            })
        }

        // ── Delete button (past only) ─────────────────────────────────────
        if (activeTab == Tab.PAST) {
            card.addView(dangerTextButton(if (isActionLoading) "Deleting…" else "Delete from History").also {
                it.isEnabled = !isActionLoading
                it.setOnClickListener {
                    TheraPeaDialog.show(
                        context  = this@AppointmentsActivity,
                        title    = "Delete Session?",
                        message  = "This will permanently remove the record from your history. This cannot be undone.",
                        icon     = "🗑️",
                        accent   = "#FEF2F2",
                        actions  = listOf(
                            TheraPeaDialog.Action("Cancel", TheraPeaDialog.Style.GHOST),
                            TheraPeaDialog.Action("Delete", TheraPeaDialog.Style.DANGER) {
                                deleteAppointment(apt)
                            }
                        )
                    )
                }
            })
        }

        return card
    }

    private fun renderCalendar() {
        calendarContainer.visibility = View.VISIBLE
        calendarGrid.removeAllViews()
        selectedDayContainer.removeAllViews()
        selectedDayContainer.visibility = View.GONE

        tvCalendarMonth.text = SimpleDateFormat("MMMM yyyy", Locale.US).format(currentMonth.time)

        listOf("Su","Mo","Tu","We","Th","Fr","Sa").forEach { day ->
            calendarGrid.addView(TextView(this).apply {
                text = day; gravity = Gravity.CENTER; textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#64748B"))
            }, ViewGroup.LayoutParams(dp(42), dp(30)))
        }

        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        repeat(cal.get(Calendar.DAY_OF_WEEK) - 1) {
            calendarGrid.addView(TextView(this), ViewGroup.LayoutParams(dp(42), dp(42)))
        }

        for (day in 1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val dayCalendar = (currentMonth.clone() as Calendar).also { it.set(Calendar.DAY_OF_MONTH, day) }
            val dateString  = formatAppointmentDate(dayCalendar)
            val dayApts     = appointments.filter { it.status.equals("Scheduled", true) && it.date == dateString }

            calendarGrid.addView(Button(this).apply {
                text = if (dayApts.isNotEmpty()) "$day\n•" else day.toString()
                textSize = 12f; isAllCaps = false
                setTextColor(if (dayApts.isNotEmpty()) Color.parseColor("#0A5C36") else Color.parseColor("#1C1F1A"))
                setBackgroundResource(if (dayApts.isNotEmpty()) R.drawable.admin_bg_button_outline else R.drawable.admin_bg_input)
                setOnClickListener { showSelectedDay(dateString, dayApts) }
            }, ViewGroup.LayoutParams(dp(42), dp(46)))
        }
    }

    private fun showSelectedDay(date: String, dayApts: List<AppointmentData>) {
        selectedDayContainer.removeAllViews()
        selectedDayContainer.visibility = View.VISIBLE

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = cardDrawable()
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        card.addView(TextView(this).apply {
            text = "Appointments for $date"; textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1F1A"))
        })

        if (dayApts.isEmpty()) {
            card.addView(bodyText("No scheduled appointments on this date."))
        } else {
            dayApts.forEach { apt ->
                card.addView(divider())
                val isTelehealth = apt.type.contains("telehealth", ignoreCase = true) ||
                        apt.type.contains("online", ignoreCase = true)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(this).apply {
                    text = if (userRole == "DOCTOR") apt.patientName.ifBlank { "Patient" } else apt.providerName.ifBlank { "Provider" }
                    textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#1C1F1A"))
                })
                info.addView(TextView(this).apply {
                    text = "${apt.time} · ${apt.type}"
                    textSize = 12f; setTextColor(Color.parseColor("#7A8077"))
                })
                row.addView(info)

                if (isTelehealth) {
                    row.addView(videoButton("Join").also {
                        it.setOnClickListener {
                            startActivity(Intent(this@AppointmentsActivity, VideoRoomActivity::class.java)
                                .putExtra("appointmentId", apt.id))
                        }
                    })
                } else {
                    row.addView(clinicBadge(apt.type))
                }
                card.addView(row)
            }
        }
        selectedDayContainer.addView(card)
    }

    private fun showDetailsDialog(apt: AppointmentData) {
        val name = if (userRole == "DOCTOR") apt.patientName.ifBlank { "Patient" }
        else apt.providerName.ifBlank { "Provider" }

        TheraPeaDialog.showDetails(
            context = this,
            title   = "Appointment Details",
            icon    = "📅",
            rows    = listOf(
                "With"       to name,
                "Status"     to apt.status,
                "Date"       to apt.date,
                "Time"       to apt.time,
                "Type"       to apt.type,
                "Focus Area" to apt.assessmentType.ifBlank { "General Consultation" },
                "Notes"      to apt.notes.ifBlank { "None" }
            )
        )
    }

    private fun showCancelDialog(apt: AppointmentData) {
        TheraPeaDialog.showInput(
            context      = this,
            title        = "Cancel Appointment?",
            message      = "Please provide a reason. Our 24-hour cancellation policy applies.",
            hint         = "Reason for cancellation…",
            icon         = "⚠️",
            confirmLabel = "Confirm Cancellation",
            cancelLabel  = "Keep It",
            multiLine    = true
        ) { reason -> cancelAppointment(apt, reason) }
    }

    private fun showNotesDialog(apt: AppointmentData) {
        TheraPeaDialog.show(
            context  = this,
            title    = "Session Notes",
            message  = apt.notes.ifBlank { "No notes recorded for this session." },
            icon     = "📝",
            accent   = "#EFF6FF",
            actions  = listOf(TheraPeaDialog.Action("Close", TheraPeaDialog.Style.GHOST))
        )
    }

    // ── Filters ───────────────────────────────────────────────────────────
    private fun upcomingAppointments() = appointments.filter { it.status.equals("Scheduled", true) }
    private fun pastAppointments()     = appointments.filter {
        it.status.equals("Completed", true) || it.status.equals("Canceled", true)
    }

    // ── Parse ─────────────────────────────────────────────────────────────
    private fun parseAppointments(response: JSONObject): List<AppointmentData> {
        val array = response.optJSONArray("appointments") ?: JSONArray()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            AppointmentData(
                id             = obj.optString("id"),
                providerId     = obj.optString("providerId"),
                providerName   = obj.optString("providerName"),
                patientId      = obj.optString("patientId"),
                patientName    = obj.optString("patientName"),
                date           = obj.optString("date"),
                time           = obj.optString("time"),
                type           = obj.optString("type"),
                status         = obj.optString("status", "Scheduled"),
                assessmentType = obj.optString("assessmentType"),
                notes          = obj.optString("notes")
            )
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.connectTimeout = 15000; c.readTimeout = 15000
        return readResponse(c)
    }
    private fun patch(url: String, body: String) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "PATCH"; c.connectTimeout = 15000; c.readTimeout = 15000
        c.doInput = true; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(c.outputStream).use { it.write(body); it.flush() }
        readResponse(c)
    }
    private fun delete(url: String) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "DELETE"; c.connectTimeout = 15000; c.readTimeout = 15000
        readResponse(c)
    }
    private fun readResponse(c: HttpURLConnection): String {
        val code   = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text   = stream.bufferedReader().use(BufferedReader::readText)
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $text")
        return text
    }
    private fun encode(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
    private fun formatAppointmentDate(c: Calendar) =
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(c.time)

    // ── View helpers ──────────────────────────────────────────────────────
    private fun showMessage(msg: String) { tvMessage.text = msg; tvMessage.visibility = View.VISIBLE }

    private fun cardDrawable() = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.parseColor("#E8EDE8"))
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(12); bottomMargin = dp(4) }
        setBackgroundColor(Color.parseColor("#F1F5F0"))
    }

    private fun emptyState(msg: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity     = Gravity.CENTER
        setPadding(dp(20), dp(48), dp(20), dp(48))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(TextView(this@AppointmentsActivity).apply {
            text = msg; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#7A8077"))
        })
    }

    private fun statusBadge(status: String): TextView {
        val (bg, fg) = when (status.uppercase()) {
            "SCHEDULED"            -> "#E6F4EE" to "#0A5C36"
            "COMPLETED"            -> "#EFF6FF" to "#1D4ED8"
            "CANCELED","CANCELLED" -> "#FEF2F2" to "#DC2626"
            else                   -> "#F1F5F0" to "#7A8077"
        }
        return TextView(this).apply {
            text     = status.lowercase().replaceFirstChar { it.uppercase() }
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(fg))
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bg)); cornerRadius = dp(100).toFloat()
            }
        }
    }

    /**
     * Green "Join Video" pill — only shown for Telehealth appointments.
     */
    private fun videoButton(label: String) = Button(this).apply {
        text     = "▶  $label"
        isAllCaps = false
        typeface  = Typeface.DEFAULT_BOLD
        textSize  = 13f
        setTextColor(Color.WHITE)
        setBackgroundResource(R.drawable.admin_bg_button_primary)
        setPadding(dp(18), 0, dp(18), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)
        )
    }

    private fun clinicBadge(type: String) = TextView(this).apply {
        text     = if (type.contains("person", ignoreCase = true) ||
            type.contains("clinic", ignoreCase = true)) "Clinic / On-Site"
        else type
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#4A5047"))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F1F5F0"))
            setStroke(dp(1), Color.parseColor("#D1D5D0"))
            cornerRadius = dp(100).toFloat()
        }
    }

    private fun ghostButton(label: String) = Button(this).apply {
        text      = label
        isAllCaps = false
        typeface  = Typeface.DEFAULT_BOLD
        textSize  = 13f
        setTextColor(Color.parseColor("#4A5047"))
        setBackgroundResource(R.drawable.admin_bg_button_outline)
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)
        )
    }

    private fun dangerTextButton(label: String) = Button(this).apply {
        text      = label
        isAllCaps = false
        typeface  = Typeface.DEFAULT_BOLD
        textSize  = 13f
        setTextColor(Color.parseColor("#DC2626"))
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { topMargin = dp(4) }
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f
        setTextColor(Color.parseColor("#7A8077"))
        setPadding(0, dp(8), 0, 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private enum class Tab { UPCOMING, PAST, CALENDAR }

    private data class AppointmentData(
        val id: String, val providerId: String, val providerName: String,
        val patientId: String, val patientName: String,
        val date: String, val time: String, val type: String,
        var status: String, val assessmentType: String, val notes: String
    )
}