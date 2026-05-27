// app/src/main/java/com/therapea/app/features/admin/AdminApprovalsActivity.kt
package com.therapea.app.features.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import com.therapea.app.features.auth.LoginActivity
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class AdminApprovalsActivity : Activity() {

    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/"
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private lateinit var tvPageTitle: TextView
    private lateinit var tvPageSubtitle: TextView
    private lateinit var tvPendingBadge: TextView
    private lateinit var tvAlert: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvApprovedCount: TextView
    private lateinit var tvRejectedCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var contentContainer: LinearLayout

    private lateinit var btnApprovals: Button
    private lateinit var btnDoctors: Button
    private lateinit var btnReports: Button
    private lateinit var btnSettings: Button
    private lateinit var btnLogout: TextView

    private lateinit var cardTotal: LinearLayout
    private lateinit var cardPending: LinearLayout
    private lateinit var cardApproved: LinearLayout
    private lateinit var cardRejected: LinearLayout

    private var activeSection = Section.APPROVALS
    private var activeFilter = FilterStatus.ALL
    private var searchQuery = ""
    private var isLoading = false
    private var actionLoadingKey: String? = null

    private val doctors = mutableListOf<DoctorRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_approvals)

        bindViews()
        setupListeners()
        render()
        loadDoctors()
        loadAdminSettings()
    }

    private fun bindViews() {
        tvPageTitle = findViewById(R.id.tvPageTitle)
        tvPageSubtitle = findViewById(R.id.tvPageSubtitle)
        tvPendingBadge = findViewById(R.id.tvPendingBadge)
        tvAlert = findViewById(R.id.tvAdminAlert)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvApprovedCount = findViewById(R.id.tvApprovedCount)
        tvRejectedCount = findViewById(R.id.tvRejectedCount)
        etSearch = findViewById(R.id.etSearch)
        contentContainer = findViewById(R.id.contentContainer)

        btnApprovals = findViewById(R.id.btnApprovals)
        btnDoctors = findViewById(R.id.btnDoctors)
        btnReports = findViewById(R.id.btnReports)
        btnSettings = findViewById(R.id.btnSettings)
        btnLogout = findViewById(R.id.btnLogout)

        cardTotal = findViewById(R.id.cardTotal)
        cardPending = findViewById(R.id.cardPending)
        cardApproved = findViewById(R.id.cardApproved)
        cardRejected = findViewById(R.id.cardRejected)
    }

    private fun setupListeners() {
        btnApprovals.setOnClickListener {
            activeSection = Section.APPROVALS
            activeFilter = FilterStatus.ALL
            render()
        }

        btnDoctors.setOnClickListener {
            activeSection = Section.DOCTORS
            render()
        }

        btnReports.setOnClickListener {
            activeSection = Section.REPORTS
            render()
        }

        btnSettings.setOnClickListener {
            activeSection = Section.SETTINGS
            render()
        }


        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        cardTotal.setOnClickListener {
            activeSection = Section.APPROVALS
            activeFilter = FilterStatus.ALL
            render()
        }

        cardPending.setOnClickListener {
            activeSection = Section.APPROVALS
            activeFilter = FilterStatus.PENDING
            render()
        }

        cardApproved.setOnClickListener {
            activeSection = Section.APPROVALS
            activeFilter = FilterStatus.APPROVED
            render()
        }

        cardRejected.setOnClickListener {
            activeSection = Section.APPROVALS
            activeFilter = FilterStatus.REJECTED
            render()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                renderContentOnly()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadDoctors() {
        isLoading = true
        renderContentOnly()

        scope.launch {
            try {
                val body = get("${apiBaseUrl}api/admin/doctors")
                val loadedDoctors = parseDoctors(body)

                doctors.clear()
                doctors.addAll(loadedDoctors)
                showBanner("Applications loaded successfully.", true)
            } catch (_: Exception) {
                showBanner("Could not load applications. Please check your server connection.", false)
            } finally {
                isLoading = false
                render()
            }
        }
    }

    private fun approveDoctor(doctor: DoctorRequest) {
        actionLoadingKey = "${doctor.id}_approve"
        renderContentOnly()

        scope.launch {
            try {
                post("${apiBaseUrl}api/admin/doctors/${doctor.id}/approve", null)
                doctor.status = Status.APPROVED
                showBanner("Doctor approved successfully.", true)
            } catch (_: Exception) {
                showBanner("Failed to approve doctor. Please try again.", false)
            } finally {
                actionLoadingKey = null
                render()
            }
        }
    }

    private fun rejectDoctor(doctor: DoctorRequest, reason: String) {
        actionLoadingKey = "${doctor.id}_reject"
        renderContentOnly()

        scope.launch {
            try {
                val payload = JSONObject().put("reason", reason)
                post("${apiBaseUrl}api/admin/doctors/${doctor.id}/reject", payload.toString())

                doctors.removeAll { it.id == doctor.id }
                showBanner("Application declined. Account has been removed.", true)
            } catch (_: Exception) {
                showBanner("Failed to reject application. Please try again.", false)
            } finally {
                actionLoadingKey = null
                render()
            }
        }
    }

    private fun render() {
        updateStats()
        updateHeader()
        updateNavButtons()
        updateStatCards()
        renderContentOnly()
    }

    private fun renderContentOnly() {
        contentContainer.removeAllViews()

        if (isLoading) {
            contentContainer.addView(emptyCard("Loading applications..."))
            return
        }

        when (activeSection) {
            Section.APPROVALS -> renderApprovals()
            Section.DOCTORS -> renderDoctors()
            Section.REPORTS -> renderReports()
            Section.SETTINGS -> renderSettings()
        }
    }

    private fun updateHeader() {
        when (activeSection) {
            Section.APPROVALS -> {
                tvPageTitle.text = "License Approvals"
                tvPageSubtitle.text = "Review doctor applications and approve or reject their credentials."
                etSearch.hint = "Search by name or email..."
                etSearch.visibility = View.VISIBLE
            }

            Section.DOCTORS -> {
                tvPageTitle.text = "Doctors"
                tvPageSubtitle.text = "All verified and active doctors on the platform."
                etSearch.hint = "Search doctors..."
                etSearch.visibility = View.VISIBLE
            }

            Section.REPORTS -> {
                tvPageTitle.text = "Reports"
                tvPageSubtitle.text = "An overview of platform activity and doctor applications."
                etSearch.visibility = View.GONE
            }

            Section.SETTINGS -> {
                tvPageTitle.text = "Settings"
                tvPageSubtitle.text = "Manage platform-wide configuration and preferences."
                etSearch.visibility = View.GONE
            }
        }
    }

    private fun updateStats() {
        val total = doctors.size
        val pending = doctors.count { it.status == Status.PENDING }
        val approved = doctors.count { it.status == Status.APPROVED }
        val rejected = doctors.count { it.status == Status.REJECTED }

        tvTotalCount.text = total.toString()
        tvPendingCount.text = pending.toString()
        tvApprovedCount.text = approved.toString()
        tvRejectedCount.text = rejected.toString()
        tvPendingBadge.text = "$pending Pending"
    }

    private fun updateNavButtons() {
        val items = listOf(
            btnApprovals to Section.APPROVALS,
            btnDoctors to Section.DOCTORS,
            btnReports to Section.REPORTS,
            btnSettings to Section.SETTINGS
        )

        items.forEach { (button, section) ->
            val selected = activeSection == section
            button.setBackgroundResource(
                if (selected) R.drawable.admin_bg_button_primary else R.drawable.admin_bg_button_outline
            )
            button.setTextColor(if (selected) Color.WHITE else Color.parseColor("#1C1F1A"))
        }
    }

    private fun updateStatCards() {
        val cards = listOf(
            cardTotal to FilterStatus.ALL,
            cardPending to FilterStatus.PENDING,
            cardApproved to FilterStatus.APPROVED,
            cardRejected to FilterStatus.REJECTED
        )

        cards.forEach { (card, filter) ->
            val selected = activeSection == Section.APPROVALS && activeFilter == filter
            card.background = roundedDrawable(
                color = "#FFFFFF",
                radius = 16,
                strokeColor = if (selected) "#0A5C36" else "#E8EDE8", // TheraPea dark green active state
                strokeWidth = if (selected) 2 else 1
            )
        }
    }

    private fun renderApprovals() {
        val filtered = filteredApprovals()

        if (filtered.isEmpty()) {
            contentContainer.addView(emptyCard("No applications match your filter."))
            return
        }

        filtered.forEach { doctor ->
            contentContainer.addView(doctorApprovalCard(doctor))
        }

        contentContainer.addView(footerText("Showing ${filtered.size} of ${doctors.size} applications"))
    }

    private fun filteredApprovals(): List<DoctorRequest> {
        return doctors.filter { doctor ->
            val matchesStatus = activeFilter == FilterStatus.ALL || doctor.status.name == activeFilter.name
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isBlank() ||
                    doctor.fullName.lowercase().contains(q) ||
                    doctor.email.lowercase().contains(q)

            matchesStatus && matchesSearch
        }
    }

    private fun renderDoctors() {
        val approved = doctors.filter { it.status == Status.APPROVED }
        val filtered = approved.filter {
            val q = searchQuery.trim().lowercase()
            q.isBlank() || it.fullName.lowercase().contains(q) || it.email.lowercase().contains(q)
        }

        if (filtered.isEmpty()) {
            contentContainer.addView(emptyCard(if (approved.isEmpty()) "No approved doctors yet." else "No doctors match your search."))
            return
        }

        filtered.forEach { doctor ->
            val card = baseCard()
            card.addView(doctorHeader(doctor))
            card.addView(pillText("Active Doctor", "#F0FDF4", "#0A5C36"))
            card.addView(bodyText(doctor.clinicalBio.ifBlank { "No biography provided." }))
            card.addView(divider())
            card.addView(labelText("₱${doctor.hourlyRate.toInt()} / hr"))
            card.addView(bodyText("Since ${formatDate(doctor.createdAt)}"))
            contentContainer.addView(card)
        }
    }

    private fun renderReports() {
        val total = doctors.size
        val approved = doctors.count { it.status == Status.APPROVED }
        val pending = doctors.count { it.status == Status.PENDING }
        val rejected = doctors.count { it.status == Status.REJECTED }
        val approvalRate = if (total == 0) 0 else approved * 100 / total

        val kpi = baseCard()
        kpi.addView(titleText("Platform Overview", 20f))
        kpi.addView(metricRow("Total Applications", total.toString()))
        kpi.addView(metricRow("Approval Rate", "$approvalRate%"))
        kpi.addView(metricRow("Active Doctors", approved.toString()))
        kpi.addView(metricRow("Pending Review", pending.toString()))
        contentContainer.addView(kpi)

        val breakdown = baseCard()
        breakdown.addView(titleText("Application Breakdown", 20f))
        breakdown.addView(progressRow("Approved", approved, total, "#10B981"))
        breakdown.addView(progressRow("Pending", pending, total, "#F59E0B"))
        breakdown.addView(progressRow("Rejected", rejected, total, "#EF4444"))

        val exportButton = primaryButton("Export CSV")
        exportButton.setOnClickListener { exportCsv() }
        breakdown.addView(exportButton)
        contentContainer.addView(breakdown)

        val recent = baseCard()
        recent.addView(titleText("Recent Applications", 20f))
        doctors.take(8).forEach {
            recent.addView(divider())
            recent.addView(bodyText("${it.fullName} • ${it.status.name.lowercase().replaceFirstChar { c -> c.uppercase() }}"))
            recent.addView(bodyText("${formatDate(it.createdAt)} • ₱${it.hourlyRate.toInt()} / hr"))
        }
        contentContainer.addView(recent)
    }

    private fun renderSettings() {
        val prefs = getSharedPreferences("AdminSettings", MODE_PRIVATE)

        val siteName = input("Platform Name", prefs.getString("siteName", "TheraPea") ?: "TheraPea")
        val supportEmail = input("Support Email", prefs.getString("supportEmail", "support@therapea.com") ?: "support@therapea.com")
        val emailNotifications = checkBox("Email notifications", prefs.getBoolean("emailNotifications", true))
        val autoLogout = checkBox("Auto-logout after inactivity", prefs.getBoolean("autoLogout", false))
        val newPassword = passwordInput("New Password", "")
        val confirmPassword = passwordInput("Confirm Password", "")
        val maxDays = input("Max review time days", prefs.getString("maxDays", "7") ?: "7")
        val rejectionTemplate = multilineInput(
            "Default rejection message",
            prefs.getString(
                "rejectionTemplate",
                "Thank you for applying to TheraPea. After reviewing your application, we are unable to approve it at this time."
            ) ?: ""
        )

        val general = baseCard()
        general.addView(titleText("General", 20f))
        general.addView(siteName)
        general.addView(supportEmail)
        contentContainer.addView(general)

        val notifications = baseCard()
        notifications.addView(titleText("Notifications", 20f))
        notifications.addView(emailNotifications)
        notifications.addView(autoLogout)
        contentContainer.addView(notifications)

        val security = baseCard()
        security.addView(titleText("Security", 20f))
        security.addView(newPassword)
        security.addView(confirmPassword)
        contentContainer.addView(security)

        val policy = baseCard()
        policy.addView(titleText("Approval Policy", 20f))
        policy.addView(maxDays)
        policy.addView(rejectionTemplate)

        val saveButton = primaryButton("Save Changes")
        saveButton.setOnClickListener {
            if (newPassword.text.toString().isNotBlank() &&
                newPassword.text.toString() != confirmPassword.text.toString()
            ) {
                showBanner("Passwords do not match.", false)
                return@setOnClickListener
            }

            val payload = JSONObject()
                .put("siteName", siteName.text.toString())
                .put("supportEmail", supportEmail.text.toString())
                .put("emailNotifications", emailNotifications.isChecked)
                .put("autoLogout", autoLogout.isChecked)
                .put("maxDays", maxDays.text.toString())
                .put("rejectionTemplate", rejectionTemplate.text.toString())

            if (newPassword.text.toString().isNotBlank()) {
                payload.put("newPassword", newPassword.text.toString())
            }

            saveAdminSettings(payload)
        }
        policy.addView(saveButton)
        contentContainer.addView(policy)
    }

    private fun loadAdminSettings() {
        scope.launch {
            try {
                val body = get("${apiBaseUrl}api/admin/settings")
                val data = JSONObject(body)
                saveSettingsLocal(data)
                if (activeSection == Section.SETTINGS) renderContentOnly()
            } catch (_: Exception) {
                // Local defaults remain available.
            }
        }
    }

    private fun saveAdminSettings(payload: JSONObject) {
        saveSettingsLocal(payload)
        showBanner("Saving settings...", true)

        scope.launch {
            try {
                put("${apiBaseUrl}api/admin/settings", payload.toString())
                showBanner("Settings saved successfully.", true)
            } catch (_: Exception) {
                showBanner("Settings saved on this device. Backend sync is unavailable right now.", true)
            }
        }
    }

    private fun saveSettingsLocal(data: JSONObject) {
        getSharedPreferences("AdminSettings", MODE_PRIVATE)
            .edit()
            .putString("siteName", data.optString("siteName", "TheraPea"))
            .putString("supportEmail", data.optString("supportEmail", "support@therapea.com"))
            .putBoolean("emailNotifications", data.optBoolean("emailNotifications", true))
            .putBoolean("autoLogout", data.optBoolean("autoLogout", false))
            .putString("maxDays", data.optString("maxDays", "7"))
            .putString("rejectionTemplate", data.optString("rejectionTemplate", ""))
            .apply()
    }

    private fun doctorApprovalCard(doctor: DoctorRequest): LinearLayout {
        val card = baseCard()

        card.addView(doctorHeader(doctor))
        card.addView(rowText("Rate", "₱${doctor.hourlyRate.toInt()} / hr"))
        card.addView(rowText("Submitted", formatDate(doctor.createdAt)))
        card.addView(statusPill(doctor.status))
        card.addView(bodyText(doctor.clinicalBio.ifBlank { "No biography provided." }))

        val viewButton = outlineButton("View Application")
        viewButton.setOnClickListener { showDoctorDetails(doctor) }
        card.addView(viewButton)

        if (doctor.status == Status.PENDING) {
            val approveKey = "${doctor.id}_approve"
            val rejectKey = "${doctor.id}_reject"
            val isBusy = actionLoadingKey == approveKey || actionLoadingKey == rejectKey

            val approveButton = primaryButton(if (actionLoadingKey == approveKey) "Approving..." else "Approve Doctor")
            approveButton.isEnabled = !isBusy
            approveButton.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Approve Doctor?")
                    .setMessage("This will verify ${doctor.fullName} and allow the doctor to appear on the platform.")
                    .setPositiveButton("Approve") { _, _ -> approveDoctor(doctor) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            card.addView(approveButton)

            val rejectButton = dangerButton(if (actionLoadingKey == rejectKey) "Rejecting..." else "Reject Application")
            rejectButton.isEnabled = !isBusy
            rejectButton.setOnClickListener { showRejectDialog(doctor) }
            card.addView(rejectButton)
        }

        return card
    }

    private fun showDoctorDetails(doctor: DoctorRequest) {
        val message = """
            Email: ${doctor.email}
            Rate: ₱${doctor.hourlyRate.toInt()} / hr
            Status: ${doctor.status.name}
            
            Clinical Biography:
            ${doctor.clinicalBio.ifBlank { "No biography provided." }}
            
            PRC License:
            ${doctor.prcLicenseUrl.ifBlank { "${apiBaseUrl}api/admin/doctors/${doctor.id}/prc-license" }}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(doctor.fullName)
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setPositiveButton("View PRC License") { _, _ -> openPrcLicense(doctor) }
            .show()
    }

    private fun openPrcLicense(doctor: DoctorRequest) {
        val link = doctor.prcLicenseUrl.ifBlank {
            "${apiBaseUrl}api/admin/doctors/${doctor.id}/prc-license"
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (_: Exception) {
            showInfoDialog("Document unavailable", "No app was found to open this document.")
        }
    }

    private fun showRejectDialog(doctor: DoctorRequest) {
        val input = EditText(this).apply {
            hint = "Reason for rejection"
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.admin_bg_input)
        }

        AlertDialog.Builder(this)
            .setTitle("Reject Application")
            .setMessage("You are rejecting ${doctor.fullName}'s application. The doctor will see this reason.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm Rejection") { _, _ ->
                val reason = input.text.toString().trim()
                    .ifBlank { "Your application was declined by administration." }
                rejectDoctor(doctor, reason)
            }
            .show()
    }

    private fun exportCsv() {
        val csv = buildString {
            appendLine("Doctor,Email,Rate,Submitted,Status")
            doctors.forEach {
                appendLine("\"${it.fullName}\",\"${it.email}\",\"${it.hourlyRate}\",\"${it.createdAt}\",\"${it.status.name}\"")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "TheraPea Admin Applications")
            putExtra(Intent.EXTRA_TEXT, csv)
        }

        try {
            startActivity(Intent.createChooser(intent, "Export CSV"))
        } catch (_: Exception) {
            showInfoDialog("Export unavailable", "No app was found to share the CSV.")
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out of the admin panel?")
            .setPositiveButton("Sign Out") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        getSharedPreferences("TheraPeaSession", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("therapea_session", MODE_PRIVATE).edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException(body)
        body
    }

    private suspend fun post(url: String, body: String?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post((body ?: "{}").toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException(responseBody)
        responseBody
    }

    private suspend fun put(url: String, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .put(body.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException(responseBody)
        responseBody
    }

    private fun parseDoctors(raw: String): List<DoctorRequest> {
        val parsed = JSONTokener(raw).nextValue()
        val array = when (parsed) {
            is JSONArray -> parsed
            is JSONObject -> parsed.optJSONArray("doctors")
                ?: parsed.optJSONArray("requests")
                ?: parsed.optJSONArray("data")
                ?: JSONArray()
            else -> JSONArray()
        }

        val result = mutableListOf<DoctorRequest>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                DoctorRequest(
                    id = obj.optString("id"),
                    fullName = obj.optString("fullName", obj.optString("name", "Unknown Doctor")),
                    email = obj.optString("email"),
                    clinicalBio = obj.optString("clinicalBio", obj.optString("bio", "")),
                    hourlyRate = obj.optDouble("hourlyRate", obj.optDouble("rate", 0.0)),
                    prcLicenseUrl = obj.optString("prcLicenseUrl", ""),
                    status = Status.from(obj.optString("status", "PENDING")),
                    createdAt = obj.optString("createdAt", "")
                )
            )
        }

        return result
    }

    private fun doctorHeader(doctor: DoctorRequest): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val avatar = TextView(this).apply {
            text = initials(doctor.fullName)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            setTextColor(Color.parseColor("#475569"))
            background = roundedDrawable("#E2E8F0", 100)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(12)
            }
        }

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        texts.addView(titleText(doctor.fullName, 18f))
        texts.addView(bodyText(doctor.email))

        row.addView(avatar)
        row.addView(texts)
        return row
    }

    private fun baseCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable("#FFFFFF", 16, "#E8EDE8", 1)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
    }

    private fun emptyCard(message: String): LinearLayout {
        return baseCard().apply {
            gravity = Gravity.CENTER
            addView(bodyText(message))
        }
    }

    private fun titleText(text: String, size: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor("#1C1F1A"))
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        }
    }

    private fun bodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#7A8077"))
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun labelText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, dp(8), 0, dp(4))
        }
    }

    private fun footerText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.END
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(8), 0, dp(16))
        }
    }

    private fun rowText(label: String, value: String): TextView {
        return bodyText("$label: $value")
    }

    private fun metricRow(label: String, value: String): TextView {
        return labelText("$label: $value")
    }

    private fun progressRow(label: String, count: Int, total: Int, color: String): LinearLayout {
        val pct = if (total == 0) 0 else count * 100 / total

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        container.addView(bodyText("$label: $count ($pct%)"))

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = pct
            progressDrawable.setTint(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply {
                topMargin = dp(6)
            }
        }

        container.addView(bar)
        return container
    }

    private fun statusPill(status: Status): TextView {
        return when (status) {
            Status.PENDING -> pillText("Pending", "#FEF3C7", "#D97706")
            Status.APPROVED -> pillText("Approved", "#E6F4EE", "#0A5C36") // Standard TheraPea green
            Status.REJECTED -> pillText("Rejected", "#FEF2F2", "#DC2626")
        }
    }

    private fun pillText(text: String, bgColor: String, textColor: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textColor))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedDrawable(bgColor, 100)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
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
                dp(48)
            ).apply {
                topMargin = dp(12)
            }
        }
    }

    private fun outlineButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1C1F1A"))
            setBackgroundResource(R.drawable.admin_bg_button_outline)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun dangerButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#DC2626"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(4)
            }
        }
    }

    private fun input(hint: String, value: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(value)
            textSize = 14f
            setSingleLine(true)
            setTextColor(Color.parseColor("#1E293B"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.admin_bg_input)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun passwordInput(hint: String, value: String): EditText {
        return input(hint, value).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }

    private fun multilineInput(hint: String, value: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(value)
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 14f
            setTextColor(Color.parseColor("#1E293B"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.admin_bg_input)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun checkBox(text: String, checked: Boolean): CheckBox {
        return CheckBox(this).apply {
            this.text = text
            isChecked = checked
            textSize = 14f
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private fun divider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        }
    }

    private fun showBanner(message: String, success: Boolean) {
        tvAlert.text = message
        tvAlert.setTextColor(Color.parseColor(if (success) "#065F46" else "#991B1B"))
        tvAlert.background = roundedDrawable(
            color = if (success) "#DCFCE7" else "#FEE2E2",
            radius = 10,
            strokeColor = if (success) "#86EFAC" else "#F87171",
            strokeWidth = 1
        )
        tvAlert.visibility = View.VISIBLE

        scope.launch {
            delay(3500)
            tvAlert.visibility = View.GONE
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Okay", null)
            .show()
    }

    private fun roundedDrawable(
        color: String,
        radius: Int,
        strokeColor: String? = null,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null && strokeWidth > 0) {
                setStroke(dp(strokeWidth), Color.parseColor(strokeColor))
            }
        }
    }

    private fun initials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .take(2)
            .joinToString("")
            .ifBlank { "DR" }
    }

    private fun formatDate(value: String): String {
        if (value.isBlank()) return "—"
        return value.substringBefore("T").ifBlank { value }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class Section {
        APPROVALS,
        DOCTORS,
        REPORTS,
        SETTINGS
    }

    private enum class FilterStatus {
        ALL,
        PENDING,
        APPROVED,
        REJECTED
    }

    private enum class Status {
        PENDING,
        APPROVED,
        REJECTED;

        companion object {
            fun from(value: String): Status {
                return when (value.uppercase()) {
                    "APPROVED" -> APPROVED
                    "REJECTED" -> REJECTED
                    else -> PENDING
                }
            }
        }
    }

    private data class DoctorRequest(
        val id: String,
        val fullName: String,
        val email: String,
        val clinicalBio: String,
        val hourlyRate: Double,
        val prcLicenseUrl: String,
        var status: Status,
        val createdAt: String
    )
}