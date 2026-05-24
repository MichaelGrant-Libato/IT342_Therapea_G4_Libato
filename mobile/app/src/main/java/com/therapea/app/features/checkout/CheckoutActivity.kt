package com.therapea.app.features.checkout

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import com.therapea.app.features.home.DoctorHomeActivity
import com.therapea.app.features.home.HomeActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CheckoutActivity : Activity() {

    private val PAYMONGO_SECRET_KEY = BuildConfig.PAYMONGO_SECRET_KEY
    private val PAYMONGO_LINKS_URL  = "https://api.paymongo.com/v1/links"

    private lateinit var tvStep:        TextView
    private lateinit var tvTitle:       TextView
    private lateinit var tvSubtitle:    TextView
    private lateinit var progress:      ProgressBar
    private lateinit var cardContainer: LinearLayout
    private lateinit var btnBack:       Button
    private lateinit var btnNext:       Button

    private var step      = 1
    private var isLoading = false

    private var therapist:   TherapistData?          = null
    private val bookedSlots: MutableList<BookedSlot> = mutableListOf()

    private var userEmail   = ""
    private var patientName = "Patient"
    private var userRole    = "PATIENT"

    private var assessmentType = ""
    private var notes          = ""
    private var selectedDate   = ""   // always stored as "EEE, MMM d, yyyy"
    private var selectedTime   = ""   // always stored as "h:mm AM/PM"
    private var sessionFormat  = "Telehealth"
    private var paymentMethod  = "FULL"

    private var paymongoReferenceNumber = ""

    private var receiptReference = ""
    private var receiptPaid      = 0
    private var receiptBalance   = 0

    private val currentMonth = Calendar.getInstance()
    private val apiBaseUrl   = "http://10.0.2.2:8083"

    // ── Canonical date format used everywhere on mobile ───────────────────
    private val DATE_FMT = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)

    // ── All formats the backend might send ───────────────────────────────
    private val BACKEND_DATE_FMTS = listOf(
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US),  // "Sun, May 25, 2026"
        SimpleDateFormat("MMM d, yyyy",      Locale.US),  // "May 25, 2026"
        SimpleDateFormat("MMMM d, yyyy",     Locale.US),  // "May 25, 2026" (full month)
        SimpleDateFormat("yyyy-MM-dd",       Locale.US),  // "2026-05-25"
        SimpleDateFormat("MM/dd/yyyy",       Locale.US),  // "05/25/2026"
        SimpleDateFormat("dd/MM/yyyy",       Locale.US)   // "25/05/2026"
    )

    // NOTE: We intentionally avoid SimpleDateFormat for time normalization
    // because Android's locale-aware formatters can output narrow no-break spaces
    // in "9:00 AM" on some devices/locales, causing string comparisons to silently fail.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        bindViews()
        readUserSession()
        setupListeners()

        val uri = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && uri?.scheme == "therapea" && uri.host == "checkout") {
            when (uri.lastPathSegment) {
                "success" -> { restoreAndFinalize(); return }
                "failed"  -> { restoreAndShowFailure(); return }
            }
        }

        loadTherapist()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        val uri = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && uri?.scheme == "therapea" && uri.host == "checkout") {
            when (uri.lastPathSegment) {
                "success" -> restoreAndFinalize()
                "failed"  -> restoreAndShowFailure()
            }
        }
    }

    private fun bindViews() {
        tvStep        = findViewById(R.id.tvCheckoutStep)
        tvTitle       = findViewById(R.id.tvCheckoutTitle)
        tvSubtitle    = findViewById(R.id.tvCheckoutSubtitle)
        progress      = findViewById(R.id.checkoutProgress)
        cardContainer = findViewById(R.id.checkoutCardContainer)
        btnBack       = findViewById(R.id.btnCheckoutBack)
        btnNext       = findViewById(R.id.btnCheckoutNext)
    }

    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        for (fmt in BACKEND_DATE_FMTS) {
            try {
                val parsed = fmt.parse(trimmed) ?: continue
                return DATE_FMT.format(parsed)
            } catch (_: Exception) { }
        }
        android.util.Log.w("Checkout", "Could not normalize date: '$raw'")
        return trimmed
    }

    private fun normalizeTime(raw: String): String {
        // Strip any weird whitespace, normalize to plain ASCII spaces
        val t = raw.trim().replace(Regex("\\s+"), " ").uppercase(Locale.US)

        // Already in "h:mm AM/PM" or "hh:mm AM/PM" form — just strip leading zero
        val amPmMatch = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)$""").find(t)
        if (amPmMatch != null) {
            val hour   = amPmMatch.groupValues[1].trimStart('0').ifEmpty { "0" }.toInt()
            val minute = amPmMatch.groupValues[2]
            val period = amPmMatch.groupValues[3]
            return "$hour:$minute $period"
        }

        // 24-hour form "HH:mm" or "H:mm" — convert to 12h AM/PM
        val h24Match = Regex("""^(\d{1,2}):(\d{2})$""").find(t)
        if (h24Match != null) {
            val h24    = h24Match.groupValues[1].toInt()
            val minute = h24Match.groupValues[2]
            val period = if (h24 < 12) "AM" else "PM"
            val h12    = when {
                h24 == 0  -> 12
                h24 > 12  -> h24 - 12
                else      -> h24
            }
            return "$h12:$minute $period"
        }

        android.util.Log.w("Checkout", "Could not normalize time: '$raw'")
        return t  // fallback — return uppercased trimmed value
    }

    // ── Session ───────────────────────────────────────────────────────────
    private fun readUserSession() {
        val emailOverride = intent.getStringExtra("email")
        val nameOverride  = intent.getStringExtra("patientName")

        if (!emailOverride.isNullOrBlank()) {
            userEmail   = emailOverride
            patientName = nameOverride ?: "Patient"
            return
        }

        val prefs       = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        if (sessionData != null) {
            try {
                val json    = JSONObject(sessionData)
                userEmail   = json.optString("email")
                patientName = json.optString("fullName", "Patient")
                userRole    = json.optString("role", "PATIENT").uppercase()
                return
            } catch (_: Exception) { }
        }

        userEmail   = ""
        patientName = "Patient"
    }

    private fun restoreAndFinalize() {
        val prefs = getSharedPreferences("TheraPeaCheckout", MODE_PRIVATE)

        if (!prefs.getBoolean("pending_exists", false)) {
            loadTherapist()
            return
        }

        therapist = TherapistData(
            id                = "",
            name              = prefs.getString("pending_therapistName",  "Provider") ?: "Provider",
            email             = prefs.getString("pending_therapistEmail", "") ?: "",
            rate              = prefs.getInt("pending_therapistRate", 1500),
            availableSchedule = "",
            whatToExpect      = ""
        )

        userEmail               = prefs.getString("pending_userEmail",     "") ?: ""
        patientName             = prefs.getString("pending_patientName",   "Patient") ?: "Patient"
        selectedDate            = prefs.getString("pending_date",          "") ?: ""
        selectedTime            = prefs.getString("pending_time",          "") ?: ""
        sessionFormat           = prefs.getString("pending_format",        "Telehealth") ?: "Telehealth"
        assessmentType          = prefs.getString("pending_assessment",    "") ?: ""
        notes                   = prefs.getString("pending_notes",         "") ?: ""
        paymentMethod           = prefs.getString("pending_paymentMethod", "FULL") ?: "FULL"
        paymongoReferenceNumber = prefs.getString("pending_paymongoRef",   "") ?: ""
        receiptPaid             = prefs.getInt("pending_amountPaid", 0)
        receiptBalance          = prefs.getInt("pending_balance",    0)
        receiptReference        = "THP-${paymongoReferenceNumber.ifBlank { System.currentTimeMillis().toString() }}"

        prefs.edit().clear().apply()
        saveBookingAndShowTimer()
    }

    private fun saveBookingAndShowTimer() {
        val t = therapist ?: return
        showProcessingThenReceipt()

        Thread {
            try {
                val payload = JSONObject()
                    .put("email",             userEmail)
                    .put("patientName",       patientName)
                    .put("providerName",      t.name)
                    .put("providerEmail",     t.email)
                    .put("date",              selectedDate)
                    .put("time",              selectedTime)
                    .put("type",              sessionFormat)
                    .put("assessmentType",    assessmentType)
                    .put("notes",             notes)
                    .put("amountPaid",        receiptPaid)
                    .put("paymongoReference", paymongoReferenceNumber)
                    .put("status",            "Scheduled")
                    .toString()

                post("$apiBaseUrl/api/appointments/book", payload)
            } catch (e: Exception) {
                android.util.Log.e("Checkout", "Post-payment save failed: ${e.message}")
            }
        }.start()
    }

    private fun restoreAndShowFailure() {
        getSharedPreferences("TheraPeaCheckout", MODE_PRIVATE).edit().clear().apply()
        bindViews()
        step = 3
        renderError("Payment was not completed. Please try again.")
        btnBack.text = "Back"
        btnBack.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            when {
                step == 1 -> finish()
                step == 4 -> navigateToDashboard()
                else      -> { step--; render() }
            }
        }
        btnNext.setOnClickListener { handleNext() }
    }

    // ── Therapist loading ─────────────────────────────────────────────────
    private fun loadTherapist() {
        val fromIntent = therapistFromIntent()

        if (fromIntent != null) {
            therapist = fromIntent
            loadBookedSlots()
            return
        }

        isLoading = true
        renderLoading("Loading therapist...")

        Thread {
            try {
                val json    = JSONObject(get("$apiBaseUrl/api/doctors/list"))
                val doctors = json.optJSONArray("doctors") ?: JSONArray()
                val first   = if (doctors.length() > 0) parseTherapist(doctors.getJSONObject(0)) else null

                runOnUiThread {
                    therapist = first
                    isLoading = false
                    if (therapist == null) renderError("No therapist found. Please return to the directory.")
                    else loadBookedSlots()
                }
            } catch (e: Exception) {
                runOnUiThread { isLoading = false; renderError("Failed to load therapist.") }
            }
        }.start()
    }

    private fun therapistFromIntent(): TherapistData? {
        val name = intent.getStringExtra("therapistName") ?: return null
        return TherapistData(
            id                = intent.getStringExtra("therapistId")           ?: "",
            name              = name,
            email             = intent.getStringExtra("therapistEmail")        ?: "",
            rate              = intent.getIntExtra("therapistRate", 1500),
            availableSchedule = intent.getStringExtra("therapistSchedule")     ?: "Monday: 9:00 AM - 5:00 PM",
            whatToExpect      = intent.getStringExtra("therapistWhatToExpect") ?: ""
        )
    }

    private fun loadBookedSlots() {
        val t = therapist ?: return
        isLoading = true
        renderLoading("Syncing availability...")

        Thread {
            try {
                val url     = "$apiBaseUrl/api/appointments/provider/${encode(t.name)}"
                val rawJson = get(url)

                android.util.Log.d("Checkout", "loadBookedSlots URL: $url")
                android.util.Log.d("Checkout", "loadBookedSlots RAW: $rawJson")

                // Handle both array root [ ] and object-wrapped { "appointments": [...] }
                val appointments: JSONArray = when {
                    rawJson.trimStart().startsWith("[") -> JSONArray(rawJson)
                    else -> {
                        val obj = JSONObject(rawJson)
                        obj.optJSONArray("appointments")
                            ?: obj.optJSONArray("data")
                            ?: obj.optJSONArray("result")
                            ?: JSONArray()
                    }
                }

                android.util.Log.d("Checkout", "Appointments count: ${appointments.length()}")

                val loadedSlots = mutableListOf<BookedSlot>()
                for (i in 0 until appointments.length()) {
                    val obj    = appointments.getJSONObject(i)
                    val status = obj.optString("status")
                    val rawDate = obj.optString("date")
                    val rawTime = obj.optString("time")

                    android.util.Log.d("Checkout",
                        "Appt[$i] status='$status' date='$rawDate' time='$rawTime'")

                    if (!status.equals("Canceled", true) && !status.equals("Cancelled", true)) {
                        val normalizedDate = normalizeDate(rawDate)
                        val normalizedTime = normalizeTime(rawTime)

                        android.util.Log.d("Checkout",
                            "  normalized → date='$normalizedDate' time='$normalizedTime'")

                        loadedSlots.add(BookedSlot(normalizedDate, normalizedTime))
                    }
                }

                runOnUiThread {
                    bookedSlots.clear()
                    bookedSlots.addAll(loadedSlots)

                    if (selectedTime.isNotBlank() &&
                        bookedSlots.any { it.date == selectedDate && it.time == selectedTime }) {
                        selectedTime = ""
                    }

                    android.util.Log.d("Checkout",
                        "✅ bookedSlots final (${bookedSlots.size}): $bookedSlots")

                    isLoading = false
                    render()
                }
            } catch (e: Exception) {
                android.util.Log.e("Checkout",
                    "❌ loadBookedSlots FAILED ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                runOnUiThread { isLoading = false; render() }
            }
        }.start()
    }

    // ── Render ────────────────────────────────────────────────────────────
    private fun render() {
        if (isLoading) return
        updateHeader()
        cardContainer.removeAllViews()
        when (step) {
            1 -> renderIntake()
            2 -> renderSchedule()
            3 -> renderReviewPay()
            4 -> renderReceipt()
        }
    }

    private fun updateHeader() {
        progress.progress = step
        tvStep.text = "Step $step of 4"

        when (step) {
            1 -> { tvTitle.text = "Booking Intake";    tvSubtitle.text = "Help your therapist understand your needs.";      btnBack.text = "Cancel"; btnNext.text = "Continue"; btnNext.visibility = View.VISIBLE }
            2 -> { tvTitle.text = "Schedule Session";  tvSubtitle.text = "Choose an available date and time.";              btnBack.text = "Back";   btnNext.text = "Review";   btnNext.visibility = View.VISIBLE }
            3 -> { tvTitle.text = "Review & Pay";      tvSubtitle.text = "Confirm your booking and choose a payment plan."; btnBack.text = "Back";   btnNext.text = "Pay";      btnNext.visibility = View.VISIBLE }
            4 -> { tvTitle.text = "Booking Confirmed"; tvSubtitle.text = "Your appointment has been created.";              btnBack.text = "Done";                              btnNext.visibility = View.GONE }
        }
    }

    private fun renderLoading(message: String) {
        updateHeader()
        cardContainer.removeAllViews()
        cardContainer.addView(bodyText(message))
        btnNext.isEnabled = false
    }

    private fun renderError(message: String) {
        cardContainer.removeAllViews()
        cardContainer.addView(titleText("Unable to Continue", 22f))
        cardContainer.addView(bodyText(message))
        btnNext.visibility = View.GONE
        btnBack.text = "Back"
    }

    // ── Step 1: Intake ────────────────────────────────────────────────────
    private fun renderIntake() {
        btnNext.isEnabled = true
        val t = therapist ?: return

        cardContainer.addView(titleText("What brings you here?", 22f))
        cardContainer.addView(bodyText("Help ${t.name} understand what you'd like to focus on."))

        listOf("Anxiety & Stress", "Relationship Issues", "Depression", "Personal Growth", "Career Counseling").forEach { option ->
            cardContainer.addView(
                outlineButton(if (assessmentType == option) "✓ $option" else option).also {
                    it.setOnClickListener { assessmentType = option; render() }
                }
            )
        }

        val notesInput = EditText(this).apply {
            hint = "Message for the therapist (optional)"
            minLines = 4
            setText(notes)
            setBackgroundResource(R.drawable.admin_bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(Color.parseColor("#1E293B"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) notes = text.toString() }
        }
        cardContainer.addView(notesInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
    }

    // ── Step 2: Schedule ──────────────────────────────────────────────────
    private fun renderSchedule() {
        val t = therapist ?: return

        cardContainer.addView(titleText("Schedule Session", 22f))
        cardContainer.addView(bodyText("📅  ${t.availableSchedule.ifBlank { "Contact provider for availability" }}"))

        // ── Month nav ─────────────────────────────────────────────────────
        val monthLabel = TextView(this).apply {
            text     = SimpleDateFormat("MMMM yyyy", Locale.US).format(currentMonth.time)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity  = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#1C1F1A"))
        }
        val monthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(16); bottomMargin = dp(8) }
        }
        fun navBtn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 18f; isAllCaps = false
            setTextColor(Color.parseColor("#0A5C36"))
            setBackgroundResource(R.drawable.admin_bg_input)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { onClick() }
        }
        monthRow.addView(navBtn("‹") { currentMonth.add(Calendar.MONTH, -1); render() })
        monthRow.addView(monthLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        monthRow.addView(navBtn("›") { currentMonth.add(Calendar.MONTH, 1); render() })
        cardContainer.addView(monthRow)

        // ── Calendar grid ─────────────────────────────────────────────────
        val calendarRoot = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val headerRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
        }
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
            headerRow.addView(TextView(this).apply {
                text     = d; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                gravity  = android.view.Gravity.CENTER
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            })
        }
        calendarRoot.addView(headerRow)

        val activeDays  = parseAvailableDays(t.availableSchedule)
        val today       = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        val month       = currentMonth.clone() as Calendar
        month.set(Calendar.DAY_OF_MONTH, 1)
        val firstDOW    = month.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)

        var dayOfMonth = 1
        var row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) }
        }
        calendarRoot.addView(row)
        repeat(firstDOW) {
            row.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }

        var cellsInRow = firstDOW
        while (dayOfMonth <= daysInMonth) {
            if (cellsInRow == 7) {
                row = LinearLayout(this).apply {
                    orientation  = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) }
                }
                calendarRoot.addView(row)
                cellsInRow = 0
            }

            val date        = (currentMonth.clone() as Calendar).also { it.set(Calendar.DAY_OF_MONTH, dayOfMonth) }
            val dayName     = SimpleDateFormat("EEEE", Locale.US).format(date.time)
            val dateText    = formatAppointmentDate(date)   // "EEE, MMM d, yyyy"
            val isPast      = date.before(today)
            val isDoctorDay = activeDays.contains(dayName)
            val fullyBooked = isDoctorDay && isDateFullyBooked(dateText, t.availableSchedule)
            val isEnabled   = !isPast && isDoctorDay && !fullyBooked
            val isSel       = selectedDate == dateText

            row.addView(TextView(this).apply {
                text     = dayOfMonth.toString()
                textSize = 13f
                typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity  = android.view.Gravity.CENTER

                setTextColor(when {
                    isSel      -> Color.WHITE
                    !isEnabled -> Color.parseColor("#CBD5E1")
                    else       -> Color.parseColor("#1C1F1A")
                })
                background = if (isSel) getDrawable(R.drawable.admin_bg_button_primary) else null
                if (fullyBooked) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }

                if (isEnabled) setOnClickListener {
                    selectedDate = dateText
                    selectedTime = ""
                    render()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })

            cellsInRow++
            dayOfMonth++
        }
        while (cellsInRow < 7) {
            row.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            cellsInRow++
        }

        cardContainer.addView(calendarRoot)

        // ── Legend ────────────────────────────────────────────────────────
        val legend = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12); bottomMargin = dp(4) }
        }
        fun legendItem(color: String, label: String) {
            legend.addView(View(this).apply {
                setBackgroundColor(Color.parseColor(color))
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(4) }
            })
            legend.addView(TextView(this).apply {
                text = label; textSize = 11f
                setTextColor(Color.parseColor("#7A8077"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(16) }
            })
        }
        legendItem("#0A5C36", "Selected")
        legendItem("#CBD5E1", "Unavailable")
        cardContainer.addView(legend)

        // ── Time slots ────────────────────────────────────────────────────
        if (selectedDate.isNotBlank()) {
            cardContainer.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#F1F5F0"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(16); bottomMargin = dp(16) }
            })
            cardContainer.addView(labelText("Select Time Slot"))

            val slots = generateSlotsForDate(parseAppointmentDate(selectedDate), t.availableSchedule)

            if (slots.isEmpty()) {
                cardContainer.addView(bodyText("No time slots available for this date."))
            } else {
                var slotRow: LinearLayout? = null
                slots.forEachIndexed { index, slot ->
                    // Normalize slot label to match how booked slots are stored
                    val normalizedSlot = normalizeTime(slot)

                    if (index % 2 == 0) {
                        slotRow = LinearLayout(this).apply {
                            orientation  = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = dp(10) }
                        }
                        cardContainer.addView(slotRow)
                    }

                    // ── Key fix: compare normalized slot against normalized booked slots ──
                    val taken     = bookedSlots.any { it.date == selectedDate && it.time == normalizedSlot }
                    val isSelTime = selectedTime == normalizedSlot && !taken

                    android.util.Log.d("Checkout",
                        "Slot '$normalizedSlot' on '$selectedDate' — taken=$taken, selected=$isSelTime")

                    val slotCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity     = android.view.Gravity.CENTER
                        setPadding(dp(12), dp(14), dp(12), dp(14))
                        background  = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            when {
                                isSelTime -> { setColor(Color.parseColor("#0A5C36")); setStroke(dp(2), Color.parseColor("#052E16")) }
                                taken     -> { setColor(Color.parseColor("#F8FAFC")); setStroke(dp(1), Color.parseColor("#E2E8F0")) }
                                else      -> { setColor(Color.WHITE);                setStroke(dp(1), Color.parseColor("#E2E8F0")) }
                            }
                        }
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { if (index % 2 == 0) marginEnd = dp(8) }
                    }

                    slotCard.addView(TextView(this).apply {
                        text     = normalizedSlot
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity  = android.view.Gravity.CENTER
                        setTextColor(when {
                            isSelTime -> Color.WHITE
                            taken     -> Color.parseColor("#CBD5E1")
                            else      -> Color.parseColor("#1C1F1A")
                        })
                    })

                    if (taken) {
                        slotCard.addView(TextView(this).apply {
                            text     = "BOOKED"
                            textSize = 9f
                            typeface = Typeface.DEFAULT_BOLD
                            gravity  = android.view.Gravity.CENTER
                            setTextColor(Color.parseColor("#94A3B8"))
                            setPadding(0, dp(3), 0, 0)
                        })
                    }

                    if (!taken) {
                        slotCard.setOnClickListener {
                            selectedTime = normalizedSlot
                            render()
                        }
                    }

                    slotRow?.addView(slotCard)

                    if (index == slots.size - 1 && index % 2 == 0) {
                        slotRow?.addView(View(this), LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                }
            }
        } else {
            cardContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = android.view.Gravity.CENTER
                setPadding(dp(20), dp(24), dp(20), dp(24))
                background  = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#F8FAFC"))
                    setStroke(dp(1), Color.parseColor("#E2E8F0"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
                addView(TextView(this@CheckoutActivity).apply {
                    text = "⏰"; textSize = 28f; gravity = android.view.Gravity.CENTER
                })
                addView(TextView(this@CheckoutActivity).apply {
                    text = "Select a date to see available times"
                    textSize = 13f; gravity = android.view.Gravity.CENTER
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(6), 0, 0)
                })
            })
        }
    }

    private fun isDateFullyBooked(dateText: String, schedule: String): Boolean {
        val cal   = parseAppointmentDate(dateText) ?: return false
        val slots = generateSlotsForDate(cal, schedule)
        if (slots.isEmpty()) return false
        return slots.all { slot ->
            val normalizedSlot = normalizeTime(slot)
            bookedSlots.any { it.date == dateText && it.time == normalizedSlot }
        }
    }

    // ── Step 3: Review & Pay ──────────────────────────────────────────────
    private fun renderReviewPay() {
        val t       = therapist ?: return
        val deposit = if (paymentMethod == "FULL") t.rate else t.rate / 2
        val balance = t.rate - deposit

        // ── Therapist & time summary cards ──
        val summaryRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        fun summaryCard(label: String, value: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background  = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#F8FAFC"))
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@CheckoutActivity).apply {
                text = label; textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
            })
            addView(TextView(this@CheckoutActivity).apply {
                text = value; textSize = 15f
                setTextColor(Color.parseColor("#1E293B"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, 0)
            })
        }

        summaryRow.addView(summaryCard("THERAPIST", t.name))
        summaryRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), 0)
        })
        summaryRow.addView(summaryCard("TIME", "$selectedDate\n@ $selectedTime"))
        cardContainer.addView(summaryRow)

        cardContainer.addView(labelText("Session Format"))
        cardContainer.addView(outlineButton(if (sessionFormat == "Telehealth") "✓ Telehealth" else "Telehealth").also {
            it.setOnClickListener { sessionFormat = "Telehealth"; render() }
        })
        cardContainer.addView(outlineButton(if (sessionFormat == "In-Person") "✓ In-Person" else "In-Person").also {
            it.setOnClickListener { sessionFormat = "In-Person"; render() }
        })

        cardContainer.addView(labelText("Select Payment Plan"))

        fun paymentCard(title: String, subtitle: String, amount: Int, method: String) =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                val isSelected = paymentMethod == method
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (isSelected) Color.parseColor("#F0FDF4") else Color.WHITE)
                    setStroke(
                        dp(if (isSelected) 2 else 1),
                        Color.parseColor(if (isSelected) "#0A5C36" else "#E2E8F0")
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }

                val titleRow = LinearLayout(this@CheckoutActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = android.view.Gravity.CENTER_VERTICAL
                }
                titleRow.addView(TextView(this@CheckoutActivity).apply {
                    text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                titleRow.addView(TextView(this@CheckoutActivity).apply {
                    text = "₱$amount"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0A5C36"))
                })
                addView(titleRow)
                addView(TextView(this@CheckoutActivity).apply {
                    text = subtitle; textSize = 12f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(4), 0, 0)
                })
                setOnClickListener { paymentMethod = method; render() }
            }

        cardContainer.addView(paymentCard(
            "Full Session Fee", "Pay total amount now for a smooth experience", t.rate, "FULL"
        ))
        cardContainer.addView(paymentCard(
            "50% Downpayment", "Pay the other half at the clinic location", t.rate / 2, "PARTIAL"
        ))

        // ── Separator ──
        cardContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(8); bottomMargin = dp(12) }
            setBackgroundColor(Color.parseColor("#E2E8F0"))
        })

        // ── To pay row ──
        val toPayRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        toPayRow.addView(TextView(this).apply {
            text = "To pay"; textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        toPayRow.addView(TextView(this).apply {
            text = "₱$deposit"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0A5C36"))
        })
        cardContainer.addView(toPayRow)

        if (balance > 0) {
            cardContainer.addView(bodyText("Remaining balance at session: ₱$balance").apply {
                gravity = android.view.Gravity.END
            })
        }
    }

    // ── Step 4: Receipt ───────────────────────────────────────────────────
    private fun renderReceipt() {
        val t = therapist ?: return

        cardContainer.addView(TextView(this).apply {
            text = "✓"; textSize = 48f; gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#16A34A")); setPadding(0, 0, 0, dp(4))
        })
        cardContainer.addView(titleText("Booking Confirmed!", 26f).apply {
            gravity = android.view.Gravity.CENTER
        })
        cardContainer.addView(bodyText("We've saved your appointment. See you soon!").apply {
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, dp(20))
        })

        val receiptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.admin_bg_card)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        fun receiptRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            row.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = value; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1E293B")); gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            receiptCard.addView(row)
        }

        receiptRow("Reference ID",   receiptReference)
        receiptRow("Therapist",      t.name)
        receiptRow("Date & Time",    "$selectedDate at $selectedTime")
        receiptRow("Session Format", sessionFormat)
        receiptRow("Focus Area",     assessmentType)

        receiptCard.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(8); bottomMargin = dp(12) }
            setBackgroundColor(Color.parseColor("#E2E8F0"))
        })

        val amtRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        amtRow.addView(TextView(this).apply {
            text = "Amount Paid"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        amtRow.addView(TextView(this).apply {
            text = "₱$receiptPaid"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0A5C36")); gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        receiptCard.addView(amtRow)

        if (receiptBalance > 0) {
            receiptCard.addView(TextView(this).apply {
                text = "Remaining balance at session: ₱$receiptBalance"
                textSize = 12f; setTextColor(Color.parseColor("#B91C1C"))
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }

        if (paymongoReferenceNumber.isNotBlank()) {
            receiptCard.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(12); bottomMargin = dp(10) }
                setBackgroundColor(Color.parseColor("#F1F5F9"))
            })
            receiptRow("PayMongo Ref", paymongoReferenceNumber)
        }

        cardContainer.addView(receiptCard)

        cardContainer.addView(primaryButton("⬇  Download Receipt (Share)").also {
            it.setOnClickListener { shareReceipt(t) }
        })
        cardContainer.addView(outlineButton("Return to Dashboard").also {
            it.setOnClickListener { navigateToDashboard() }
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
        })
    }

    private fun shareReceipt(t: TherapistData) {
        val receiptText = """
        ╔══════════════════════════════╗
              TheraPea — E-Receipt
        ╚══════════════════════════════╝
        
        Reference ID  : $receiptReference
        Date Issued   : ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(java.util.Date())}
        
        Therapist     : ${t.name}
        Date & Time   : $selectedDate at $selectedTime
        Session Format: $sessionFormat
        Focus Area    : $assessmentType
        
        ─────────────────────────────
        Amount Paid   : ₱$receiptPaid
        ${if (receiptBalance > 0) "Balance Due   : ₱$receiptBalance" else ""}
        ${if (paymongoReferenceNumber.isNotBlank()) "PayMongo Ref  : $paymongoReferenceNumber" else ""}
        ─────────────────────────────
        
        Thank you for choosing TheraPea.
        Your mental health matters to us.
        """.trimIndent()

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "TheraPea Booking Receipt — $receiptReference")
                    putExtra(Intent.EXTRA_TEXT, receiptText)
                },
                "Save or Share Receipt"
            )
        )
    }

    private fun handleNext() {
        when (step) {
            1 -> {
                notes = collectEditTextValue(cardContainer)
                if (assessmentType.isBlank()) {
                    Toast.makeText(this, "Please choose a focus area.", Toast.LENGTH_SHORT).show()
                    return
                }
                step = 2; render()
            }
            2 -> {
                if (selectedDate.isBlank() || selectedTime.isBlank()) {
                    Toast.makeText(this, "Please choose a date and time.", Toast.LENGTH_SHORT).show()
                    return
                }
                step = 3; render()
            }
            3 -> createPayMongoLink()
        }
    }

    private fun createPayMongoLink() {
        val t = therapist ?: return

        btnNext.isEnabled = false
        btnNext.text      = "Creating link…"

        val amountPhp   = if (paymentMethod == "FULL") t.rate else t.rate / 2
        val description = "Therapy Session — ${t.name} ($selectedDate)"

        getSharedPreferences("TheraPeaCheckout", MODE_PRIVATE).edit().apply {
            putString("pending_therapistName",  t.name)
            putString("pending_therapistEmail", t.email)
            putInt("pending_therapistRate",     t.rate)
            putString("pending_userEmail",      userEmail)
            putString("pending_patientName",    patientName)
            putString("pending_date",           selectedDate)
            putString("pending_time",           selectedTime)
            putString("pending_format",         sessionFormat)
            putString("pending_assessment",     assessmentType)
            putString("pending_notes",          notes)
            putString("pending_paymentMethod",  paymentMethod)
            putInt("pending_amountPaid",        amountPhp)
            putInt("pending_balance",           t.rate - amountPhp)
            putBoolean("pending_exists",        true)
            apply()
        }

        Thread {
            try {
                val payload = JSONObject()
                    .put("amount",      amountPhp)
                    .put("description", description)
                    .put("email",       userEmail)
                    .put("source",      "mobile")
                    .toString()

                val responseStr = post("$apiBaseUrl/api/payments/create-link", payload)
                android.util.Log.d("PAYMONGO_RESPONSE", responseStr)

                val json        = JSONObject(responseStr)
                val checkoutUrl = json.optString("checkoutUrl")
                val refNum      = json.optString("referenceNumber", "")

                runOnUiThread {
                    btnNext.isEnabled = true
                    btnNext.text = "Pay"

                    if (checkoutUrl.isNotBlank()) {
                        paymongoReferenceNumber = refNum
                        getSharedPreferences("TheraPeaCheckout", MODE_PRIVATE)
                            .edit().putString("pending_paymongoRef", refNum).apply()
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                    } else {
                        Toast.makeText(this, "Could not create payment link.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnNext.isEnabled = true
                    btnNext.text = "Pay"
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun confirmFinalizeBooking() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Booking")
            .setMessage("Only continue after your PayMongo payment page shows a successful transaction.")
            .setNegativeButton("Not Yet", null)
            .setPositiveButton("Confirm") { _, _ -> finalizeBooking() }
            .show()
    }

    private fun finalizeBooking() {
        val t   = therapist ?: return
        val paid = if (paymentMethod == "FULL") t.rate else t.rate / 2
        val bal  = t.rate - paid

        btnNext.isEnabled = false
        btnNext.text      = "Saving…"

        Thread {
            try {
                val payload = JSONObject()
                    .put("email",             userEmail)
                    .put("patientName",       patientName)
                    .put("providerName",      t.name)
                    .put("providerEmail",     t.email)
                    .put("date",              selectedDate)
                    .put("time",              selectedTime)
                    .put("type",              sessionFormat)
                    .put("assessmentType",    assessmentType)
                    .put("notes",             notes)
                    .put("amountPaid",        paid)
                    .put("paymongoReference", paymongoReferenceNumber)
                    .put("status",            "Scheduled")
                    .toString()

                post("$apiBaseUrl/api/appointments/book", payload)

                runOnUiThread {
                    receiptReference  = "THP-${paymongoReferenceNumber.ifBlank { System.currentTimeMillis().toString() }}"
                    receiptPaid       = paid
                    receiptBalance    = bal
                    btnNext.isEnabled = true
                    showProcessingThenReceipt()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnNext.isEnabled = true
                    btnNext.text = "Pay"
                    Toast.makeText(this,
                        "Booking save failed. Payment went through — contact support with ref: $paymongoReferenceNumber",
                        Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showProcessingThenReceipt() {
        btnNext.visibility = View.GONE
        btnBack.visibility = View.GONE

        step              = 4
        progress.progress = 4
        tvStep.text       = "Booking Complete"
        tvTitle.text      = "Payment Received!"
        tvSubtitle.text   = "Your session has been confirmed."

        cardContainer.removeAllViews()
        cardContainer.gravity = android.view.Gravity.CENTER

        cardContainer.addView(TextView(this).apply {
            text = "✓"; textSize = 64f; gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#16A34A")); setPadding(0, dp(16), 0, dp(8))
        })
        cardContainer.addView(TextView(this).apply {
            text = "QRPh Payment Received!"; textSize = 22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER; setTextColor(Color.parseColor("#1E293B"))
            setPadding(dp(16), 0, dp(16), dp(8))
        })
        cardContainer.addView(TextView(this).apply {
            text = "You will be redirected to your receipt automatically."
            textSize = 14f; gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#64748B")); setPadding(dp(24), 0, dp(24), dp(24))
        })

        var countdown = 5
        val tvTimer = TextView(this).apply {
            text = "$countdown"; textSize = 48f; typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER; setTextColor(Color.parseColor("#0A5C36"))
            setBackgroundResource(R.drawable.admin_bg_pill_green)
            setPadding(dp(28), dp(12), dp(28), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; bottomMargin = dp(20) }
        }
        cardContainer.addView(tvTimer)

        val btnProceed = Button(this).apply {
            text = "Proceed to Receipt →"; textSize = 15f; isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.admin_bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { leftMargin = dp(16); rightMargin = dp(16) }
        }
        cardContainer.addView(btnProceed)

        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                countdown--
                tvTimer.text = "$countdown"
                if (countdown > 0) handler.postDelayed(this, 1000) else goToReceipt()
            }
        }
        handler.postDelayed(tick, 1000)

        btnProceed.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            goToReceipt()
        }
    }

    private fun goToReceipt() {
        cardContainer.gravity = android.view.Gravity.TOP
        cardContainer.removeAllViews()
        btnBack.visibility = View.VISIBLE
        btnBack.text       = "Done"
        btnNext.visibility = View.GONE
        renderReceipt()
    }

    // ── Navigation ────────────────────────────────────────────────────────
    private fun navigateToDashboard() {
        val targetClass = if (userRole == "DOCTOR") DoctorHomeActivity::class.java
        else HomeActivity::class.java
        startActivity(Intent(this, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun parseAvailableDays(schedule: String): Set<String> {
        if (schedule.isBlank() || schedule == "Not currently available")
            return setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

        return schedule.split(",")
            .mapNotNull { it.substringBefore(":").trim().takeIf { d -> d.isNotBlank() } }
            .toSet()
            .ifEmpty { setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday") }
    }

    private fun generateSlotsForDate(date: Calendar?, schedule: String): List<String> {
        if (date == null || schedule.isBlank()) return emptyList()
        val dayName  = SimpleDateFormat("EEEE", Locale.US).format(date.time)
        val dayEntry = schedule.split(",").map { it.trim() }
            .firstOrNull { it.startsWith("$dayName:", ignoreCase = true) } ?: return emptyList()
        val blocks   = dayEntry.substringAfter(":", "").trim().let {
            when {
                it.contains("|")     -> it.split("|")
                it.contains(" and ") -> it.split(" and ")
                else                 -> listOf(it)
            }
        }
        return blocks.mapNotNull {
            it.split(" - ").firstOrNull()?.trim()?.takeIf { s -> s.isNotBlank() }
        }.distinct()
    }

    /** Mobile canonical: "EEE, MMM d, yyyy" e.g. "Sun, May 25, 2026" */
    private fun formatAppointmentDate(c: Calendar): String = DATE_FMT.format(c.time)

    private fun parseAppointmentDate(value: String): Calendar? =
        try { Calendar.getInstance().apply { time = DATE_FMT.parse(value)!! } }
        catch (_: Exception) { null }

    private fun collectEditTextValue(parent: ViewGroup): String {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is EditText) return child.text.toString()
        }
        return notes
    }

    private fun parseTherapist(obj: JSONObject) = TherapistData(
        id                = obj.optString("id"),
        name              = obj.optString("name", obj.optString("fullName", "Provider")),
        email             = obj.optString("email"),
        rate              = obj.optInt("rate", obj.optInt("hourlyRate", 1500)),
        availableSchedule = obj.optString("availableSchedule", "Monday: 9:00 AM - 5:00 PM"),
        whatToExpect      = obj.optString("whatToExpect")
    )

    // ── HTTP ──────────────────────────────────────────────────────────────
    private fun get(urlString: String): String {
        val c = URL(urlString).openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.connectTimeout = 15_000; c.readTimeout = 15_000
        return readResponse(c)
    }

    private fun post(urlString: String, body: String): String {
        val c = URL(urlString).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 15_000
        c.doInput = true; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(c.outputStream).use { it.write(body); it.flush() }
        return readResponse(c)
    }

    private fun readResponse(c: HttpURLConnection): String {
        val code   = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text   = stream.bufferedReader().use(BufferedReader::readText)
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $text")
        return text
    }

    // URLEncoder uses + for spaces; replace with %20 so the backend path param matches correctly
    private fun encode(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    // ── View factories ────────────────────────────────────────────────────
    private fun titleText(text: String, size: Float) = TextView(this).apply {
        this.text = text; textSize = size
        setTextColor(Color.parseColor("#1E293B"))
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f
        setTextColor(Color.parseColor("#64748B"))
        setPadding(0, dp(3), 0, dp(6))
    }

    private fun labelText(text: String) = TextView(this).apply {
        this.text = text; textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#1E293B"))
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun primaryButton(text: String) = Button(this).apply {
        this.text = text; isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        setBackgroundResource(R.drawable.admin_bg_button_primary)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
            .apply { topMargin = dp(10) }
    }

    private fun outlineButton(text: String) = Button(this).apply {
        this.text = text; isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#1E293B"))
        setBackgroundResource(R.drawable.admin_bg_button_outline)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
            .apply { topMargin = dp(8) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class TherapistData(
        val id:                String,
        val name:              String,
        val email:             String,
        val rate:              Int,
        val availableSchedule: String,
        val whatToExpect:      String
    )

    private data class BookedSlot(val date: String, val time: String)
}