package com.therapea.app.features.profile

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.therapea.app.R
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class TimeBlock(
    val id: String = UUID.randomUUID().toString(),
    var start: String = "09:00",
    var end: String = "17:00"
)

data class DaySchedule(
    var active: Boolean = false,
    val timeBlocks: MutableList<TimeBlock> = mutableListOf(TimeBlock())
)

class ProfileActivity : Activity() {

    companion object {
        const val API_BASE = "http://10.0.2.2:8083"
        val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private const val PICK_IMAGE_REQUEST = 8110
    }

    private val httpClient = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val weeklySchedule = LinkedHashMap<String, DaySchedule>()

    private var userEmail = ""
    private var userFullName = ""
    private var userRole = "PATIENT"
    private var userPhone = ""
    private var userBio = ""
    private var userRate = 1500
    private var userWhatToExpect = ""
    private var userScheduleStr = ""
    private var userPicUrl = ""
    private var profileCompleted = true

    private var isEditing = false
    private var activeTab = "personal"
    private var selectedImageUri: Uri? = null

    private lateinit var scheduleAdapter: ScheduleDayAdapter

    private lateinit var ivAvatar: ImageView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var tvName: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvPrcBadge: TextView
    private lateinit var bannerMandatory: View
    private lateinit var bannerError: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSectionTitle: TextView

    private lateinit var btnTabPersonal: MaterialButton
    private lateinit var btnTabProfessional: MaterialButton
    private lateinit var btnTabSecurity: MaterialButton
    private lateinit var framePersonal: View
    private lateinit var frameProfessional: View
    private lateinit var frameSecurity: View

    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etRate: TextInputEditText
    private lateinit var etBio: TextInputEditText
    private lateinit var rvSchedule: RecyclerView
    private lateinit var tvScheduleError: TextView
    private lateinit var etExpect: TextInputEditText
    private lateinit var tvExpectError: TextView

    private lateinit var etCurrentPwd: TextInputEditText
    private lateinit var etNewPwd: TextInputEditText
    private lateinit var etConfirmPwd: TextInputEditText

    private lateinit var btnEdit: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnAvatarEdit: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        readSession()
        if (userEmail.isBlank()) {
            showInfoDialog("Session expired", "Please sign in again to manage your profile.") { finish() }
            return
        }

        bindViews()
        initDefaultSchedule()
        setupScheduleList()
        setupListeners()
        setupInitialUi()
        loadProfile()
    }

    private fun bindViews() {
        ivAvatar = findViewById(R.id.iv_avatar)
        tvAvatarInitials = findViewById(R.id.tv_avatar_initials)
        tvName = findViewById(R.id.tv_name)
        tvRole = findViewById(R.id.tv_role)
        tvPrcBadge = findViewById(R.id.tv_prc_badge)
        bannerMandatory = findViewById(R.id.banner_mandatory)
        bannerError = findViewById(R.id.tv_banner_error)
        progressBar = findViewById(R.id.progress_bar)
        tvSectionTitle = findViewById(R.id.tv_section_title)

        btnTabPersonal = findViewById(R.id.btn_tab_personal)
        btnTabProfessional = findViewById(R.id.btn_tab_professional)
        btnTabSecurity = findViewById(R.id.btn_tab_security)

        framePersonal = findViewById(R.id.frame_personal)
        frameProfessional = findViewById(R.id.frame_professional)
        frameSecurity = findViewById(R.id.frame_security)

        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etPhone = findViewById(R.id.et_phone)
        etRate = findViewById(R.id.et_rate)
        etBio = findViewById(R.id.et_bio)
        rvSchedule = findViewById(R.id.rv_schedule)
        tvScheduleError = findViewById(R.id.tv_schedule_error)
        etExpect = findViewById(R.id.et_expect)
        tvExpectError = findViewById(R.id.tv_expect_error)

        etCurrentPwd = findViewById(R.id.et_current_pwd)
        etNewPwd = findViewById(R.id.et_new_pwd)
        etConfirmPwd = findViewById(R.id.et_confirm_pwd)

        btnEdit = findViewById(R.id.btn_edit)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        btnAvatarEdit = findViewById(R.id.btn_avatar_edit)
    }

    private fun setupInitialUi() {
        btnTabProfessional.isVisible = userRole == "DOCTOR"
        tvPrcBadge.isVisible = userRole == "DOCTOR"
        switchToTab(if (userRole == "DOCTOR" && isMandatorySetup()) "professional" else "personal")
        setEditMode(false)
    }

    private fun setupScheduleList() {
        scheduleAdapter = ScheduleDayAdapter(weeklySchedule)
        rvSchedule.layoutManager = LinearLayoutManager(this)
        rvSchedule.adapter = scheduleAdapter
        rvSchedule.isNestedScrollingEnabled = false
    }

    private fun setupListeners() {
        findViewById<TextView>(R.id.btn_back).setOnClickListener {
            if (isMandatorySetup()) {
                showInfoDialog(
                    "Complete setup first",
                    "Your professional schedule and session expectations are required before leaving this page."
                )
            } else finish()
        }

        ivAvatar.setOnClickListener { if (isEditing) openImagePicker() }
        btnAvatarEdit.setOnClickListener { if (isEditing) openImagePicker() }

        btnTabPersonal.setOnClickListener {
            if (!isMandatorySetup()) switchToTab("personal")
        }
        btnTabProfessional.setOnClickListener { switchToTab("professional") }
        btnTabSecurity.setOnClickListener {
            if (!isMandatorySetup()) switchToTab("security")
        }

        btnEdit.setOnClickListener { setEditMode(true) }
        btnCancel.setOnClickListener { cancelEdit() }
        btnSave.setOnClickListener { initiateSave() }
    }

    private fun readSession() {
        val sessionPrefs = getSharedPreferences("TheraPeaSession", Context.MODE_PRIVATE)
        val raw = sessionPrefs.getString("user_data", null)
            ?: sessionPrefs.getString("temp_session", null)

        if (!raw.isNullOrBlank()) {
            val json = JSONObject(raw)
            userEmail = json.optString("email")
            userFullName = json.optString("fullName", json.optString("name"))
            userRole = json.optString("role", "PATIENT").uppercase()
        }

        val mobilePrefs = getSharedPreferences("therapea_session", Context.MODE_PRIVATE)
        if (userEmail.isBlank()) userEmail = mobilePrefs.getString("email", "") ?: ""
        if (userFullName.isBlank()) userFullName = mobilePrefs.getString("fullName", "") ?: ""
        userRole = mobilePrefs.getString("role", userRole)?.uppercase() ?: userRole

        val legacyPrefs = getSharedPreferences("therapea_prefs", Context.MODE_PRIVATE)
        if (userEmail.isBlank()) userEmail = legacyPrefs.getString("email", "") ?: ""
        if (userFullName.isBlank()) userFullName = legacyPrefs.getString("fullName", "") ?: ""
        userRole = legacyPrefs.getString("role", userRole)?.uppercase() ?: userRole
    }

    private fun loadProfile() {
        setBusy(true)

        scope.launch {
            try {
                val url = "$API_BASE/api/dashboard/profile?email=${userEmail.encodeUrl()}"
                val request = Request.Builder().url(url).get().build()
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val body = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }

                if (response.isSuccessful && body.isNotBlank()) {
                    val json = JSONObject(body)
                    userFullName = json.optString("fullName", userFullName)
                    userPhone = json.optString("phone", "")
                    userBio = json.optString("clinicalBio", "")
                    userRate = json.optInt("hourlyRate", 1500)
                    userWhatToExpect = json.optString("whatToExpect", "")
                    userScheduleStr = json.optString("availableSchedule", "")
                    userPicUrl = json.optString("profilePictureUrl", "")
                    profileCompleted = json.optBoolean("profileCompleted", true)
                    saveSessionSnapshot()
                }
            } catch (_: Exception) {
                showError("Could not refresh your profile. Cached information is shown.")
            } finally {
                setBusy(false)
                populateUi()
            }
        }
    }

    private fun populateUi() {
        tvName.text = if (userRole == "DOCTOR") "Dr. $userFullName" else userFullName
        tvRole.text = if (userRole == "DOCTOR") "Licensed Provider" else "Patient"

        etName.setText(userFullName)
        etEmail.setText(userEmail)
        etPhone.setText(userPhone)
        etRate.setText(userRate.toString())
        etBio.setText(userBio)
        etExpect.setText(userWhatToExpect)

        parseScheduleFromString(userScheduleStr)
        scheduleAdapter.notifyDataSetChanged()

        if (userPicUrl.isNotBlank()) loadAvatarFromUrl(userPicUrl) else showInitials()

        if (isMandatorySetup()) {
            bannerMandatory.isVisible = true
            switchToTab("professional")
            setEditMode(true)
        } else {
            bannerMandatory.isVisible = false
        }
    }

    private fun switchToTab(tab: String) {
        activeTab = tab

        framePersonal.isVisible = tab == "personal"
        frameProfessional.isVisible = tab == "professional"
        frameSecurity.isVisible = tab == "security"

        tvSectionTitle.text = when (tab) {
            "professional" -> "Professional Profile"
            "security" -> "Security Settings"
            else -> "Personal Details"
        }

        val buttons = listOf(btnTabPersonal, btnTabProfessional, btnTabSecurity)
        buttons.forEach {
            it.setBackgroundColor(Color.TRANSPARENT)
            it.setTextColor(Color.parseColor("#64748B"))
        }

        val selected = when (tab) {
            "professional" -> btnTabProfessional
            "security" -> btnTabSecurity
            else -> btnTabPersonal
        }
        selected.setBackgroundColor(Color.parseColor("#0A5C36"))
        selected.setTextColor(Color.WHITE)
    }

    private fun setEditMode(editing: Boolean) {
        isEditing = editing

        btnEdit.isVisible = !editing
        btnSave.isVisible = editing
        btnCancel.isVisible = editing && !isMandatorySetup()
        btnAvatarEdit.isVisible = editing

        btnSave.text = if (isMandatorySetup()) "Complete Setup" else "Save Changes"

        listOf(etName, etPhone, etRate, etBio, etExpect, etCurrentPwd, etNewPwd, etConfirmPwd).forEach {
            it.isEnabled = editing
        }
        etEmail.isEnabled = false

        scheduleAdapter.setEditable(editing)
        scheduleAdapter.notifyDataSetChanged()
    }

    private fun cancelEdit() {
        selectedImageUri = null
        bannerError.isVisible = false
        tvScheduleError.isVisible = false
        tvExpectError.isVisible = false
        populateUi()
        setEditMode(false)
    }

    private fun initiateSave() {
        bannerError.isVisible = false
        tvScheduleError.isVisible = false
        tvExpectError.isVisible = false

        if (etName.text.toString().trim().isBlank()) {
            showError("Full name is required.")
            return
        }

        if (userRole == "DOCTOR") {
            val scheduleIssue = validateTimeBlocks()
            if (scheduleIssue != null) {
                showError(scheduleIssue)
                tvScheduleError.isVisible = true
                return
            }

            val hasSchedule = weeklySchedule.any { it.value.active && it.value.timeBlocks.isNotEmpty() }
            val hasExpectations = etExpect.text.toString().trim().isNotBlank()

            if (isMandatorySetup() && (!hasSchedule || !hasExpectations)) {
                tvScheduleError.isVisible = !hasSchedule
                tvExpectError.isVisible = !hasExpectations
                showError("Please complete the highlighted professional fields.")
                return
            }
        }

        val passwordIssue = validatePasswordFields()
        if (passwordIssue != null) {
            showError(passwordIssue)
            return
        }

        if (userRole == "DOCTOR" && activeTab == "professional") {
            AlertDialog.Builder(this)
                .setTitle("Review Profile Updates")
                .setMessage("Your professional profile, schedule, and session expectations will be updated immediately.")
                .setPositiveButton("Confirm & Save") { _, _ -> executeSave() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            executeSave()
        }
    }

    private fun executeSave() {
        setBusy(true)
        btnSave.isEnabled = false
        btnSave.text = if (isMandatorySetup()) "Completing..." else "Saving..."

        scope.launch {
            try {
                if (selectedImageUri != null) {
                    val uploadedUrl = uploadProfileImage(selectedImageUri!!)
                    if (uploadedUrl != null) userPicUrl = uploadedUrl
                    else {
                        showError("Profile picture upload is not available right now. Try saving without changing the photo.")
                        resetSaveState()
                        return@launch
                    }
                }

                val scheduleString = formatScheduleToString()
                val completed = if (userRole == "DOCTOR") {
                    etExpect.text.toString().trim().isNotBlank() && scheduleString != "Not currently available"
                } else true

                val payload = JSONObject().apply {
                    put("email", userEmail)
                    put("fullName", etName.text.toString().trim())
                    put("phone", etPhone.text.toString().trim())
                    put("clinicalBio", etBio.text.toString().trim())
                    put("hourlyRate", etRate.text.toString().toIntOrNull() ?: 1500)
                    put("availableSchedule", scheduleString)
                    put("whatToExpect", etExpect.text.toString().trim())
                    put("profilePictureUrl", userPicUrl)
                    put("profileCompleted", completed)
                }

                val request = Request.Builder()
                    .url("$API_BASE/api/users/update")
                    .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

                if (!response.isSuccessful) {
                    showError("Failed to save profile changes.")
                    resetSaveState()
                    return@launch
                }

                val passwordChanged = savePasswordIfNeeded()
                if (!passwordChanged && etNewPwd.text.toString().isNotBlank()) {
                    showError("Profile saved, but password update failed.")
                    resetSaveState()
                    return@launch
                }

                userFullName = etName.text.toString().trim()
                userPhone = etPhone.text.toString().trim()
                userBio = etBio.text.toString().trim()
                userRate = etRate.text.toString().toIntOrNull() ?: 1500
                userWhatToExpect = etExpect.text.toString().trim()
                userScheduleStr = scheduleString
                profileCompleted = completed

                etCurrentPwd.setText("")
                etNewPwd.setText("")
                etConfirmPwd.setText("")
                selectedImageUri = null

                saveSessionSnapshot()
                populateUi()
                setEditMode(false)
                showSuccessDialog()
            } catch (_: Exception) {
                showError("Network error. Please check your connection.")
            } finally {
                resetSaveState()
            }
        }
    }

    private suspend fun savePasswordIfNeeded(): Boolean {
        val newPassword = etNewPwd.text.toString()
        if (newPassword.isBlank()) return true

        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("email", userEmail)
                    put("currentPassword", etCurrentPwd.text.toString())
                    put("newPassword", newPassword)
                }

                val request = Request.Builder()
                    .url("$API_BASE/api/auth/change-password")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().isSuccessful
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun uploadProfileImage(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val type = contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("email", userEmail)
                    .addFormDataPart(
                        "file",
                        "profile-${System.currentTimeMillis()}.jpg",
                        bytes.toRequestBody(type.toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url("$API_BASE/api/users/profile-picture")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(bodyText)
                json.optString("profilePictureUrl", json.optString("url", ""))
                    .takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun validatePasswordFields(): String? {
        val current = etCurrentPwd.text.toString()
        val next = etNewPwd.text.toString()
        val confirm = etConfirmPwd.text.toString()

        if (current.isBlank() && next.isBlank() && confirm.isBlank()) return null
        if (current.isBlank()) return "Enter your current password."
        if (next.length < 8) return "New password must be at least 8 characters."
        if (next != confirm) return "New passwords do not match."
        return null
    }

    private fun initDefaultSchedule() {
        weeklySchedule.clear()
        DAYS.forEach { day ->
            val weekend = day == "Saturday" || day == "Sunday"
            weeklySchedule[day] = DaySchedule(
                active = false,
                timeBlocks = mutableListOf(
                    TimeBlock(
                        start = if (weekend) "10:00" else "09:00",
                        end = if (weekend) "14:00" else "17:00"
                    )
                )
            )
        }
    }

    private fun parseScheduleFromString(value: String) {
        initDefaultSchedule()
        if (value.isBlank() || value == "Not currently available") return

        value.split(", ").forEach { dayPart ->
            val colon = dayPart.indexOf(": ")
            if (colon == -1) return@forEach

            val day = dayPart.substring(0, colon)
            val times = dayPart.substring(colon + 2)
            val daySchedule = weeklySchedule[day] ?: return@forEach

            daySchedule.active = true
            daySchedule.timeBlocks.clear()

            times.split(" | ").forEach { block ->
                val parts = block.split(" - ")
                if (parts.size == 2) {
                    daySchedule.timeBlocks.add(
                        TimeBlock(
                            start = parseTime12hTo24h(parts[0].trim()),
                            end = parseTime12hTo24h(parts[1].trim())
                        )
                    )
                }
            }
        }
    }

    private fun formatScheduleToString(): String {
        val activeDays = weeklySchedule.filter { it.value.active }
        if (activeDays.isEmpty()) return "Not currently available"

        return activeDays.entries.joinToString(", ") { (day, schedule) ->
            val slots = schedule.timeBlocks.joinToString(" | ") {
                "${formatTime24hTo12h(it.start)} - ${formatTime24hTo12h(it.end)}"
            }
            "$day: $slots"
        }
    }

    private fun validateTimeBlocks(): String? {
        weeklySchedule.entries.filter { it.value.active }.forEach { (_, schedule) ->
            val blocks = schedule.timeBlocks

            blocks.forEach {
                if (timeToMins(it.start) >= timeToMins(it.end)) {
                    return "End time must be after start time."
                }
            }

            for (i in blocks.indices) {
                for (j in i + 1 until blocks.size) {
                    val a = blocks[i]
                    val b = blocks[j]
                    if (timeToMins(a.start) < timeToMins(b.end) && timeToMins(a.end) > timeToMins(b.start)) {
                        return "Some schedule time slots overlap."
                    }
                }
            }
        }
        return null
    }

    private fun loadAvatarFromUrl(url: String) {
        ivAvatar.clipToOutline = true

        scope.launch(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
                val bytes    = response.body?.bytes()
                val bitmap   = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        ivAvatar.setImageBitmap(bitmap)
                        tvAvatarInitials.isVisible = false
                    } else {
                        showInitials()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { showInitials() }
            }
        }
    }

    private fun showInitials() {
        val initials = userFullName.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .take(2)
            .joinToString("")
            .ifBlank { "?" }

        tvAvatarInitials.text      = initials
        tvAvatarInitials.isVisible = true
        ivAvatar.setImageDrawable(null)
        ivAvatar.clipToOutline     = true
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "Choose profile photo"), PICK_IMAGE_REQUEST)
    }

    @Deprecated("Used for Activity() image picker compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            selectedImageUri = data?.data
            if (selectedImageUri != null) {
                ivAvatar.clipToOutline = true
                ivAvatar.setImageURI(selectedImageUri)
                tvAvatarInitials.isVisible = false
            }
        }
    }

    private fun isMandatorySetup(): Boolean {
        return userRole == "DOCTOR" && !profileCompleted
    }

    private fun saveSessionSnapshot() {
        val json = JSONObject().apply {
            put("email", userEmail)
            put("fullName", userFullName)
            put("role", userRole)
            put("phone", userPhone)
            put("clinicalBio", userBio)
            put("hourlyRate", userRate)
            put("whatToExpect", userWhatToExpect)
            put("availableSchedule", userScheduleStr)
            put("profilePictureUrl", userPicUrl)
            put("profileCompleted", profileCompleted)
        }

        getSharedPreferences("TheraPeaSession", Context.MODE_PRIVATE)
            .edit()
            .putString("user_data", json.toString())
            .apply()

        getSharedPreferences("therapea_session", Context.MODE_PRIVATE)
            .edit()
            .putString("email", userEmail)
            .putString("fullName", userFullName)
            .putString("role", userRole)
            .apply()
    }

    private fun showError(message: String) {
        bannerError.text = message
        bannerError.isVisible = true
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Changes Saved")
            .setMessage("Your profile has been successfully updated.")
            .setPositiveButton("Okay", null)
            .show()
    }

    private fun showInfoDialog(title: String, message: String, onClose: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Okay") { dialog, _ ->
                dialog.dismiss()
                onClose?.invoke()
            }
            .show()
    }

    private fun setBusy(busy: Boolean) {
        progressBar.isVisible = busy
    }

    private fun resetSaveState() {
        setBusy(false)
        btnSave.isEnabled = true
        btnSave.text = if (isMandatorySetup()) "Complete Setup" else "Save Changes"
    }

    private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")

    private fun timeToMins(value: String): Int {
        val parts = value.split(":")
        return ((parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60) +
                (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatTime24hTo12h(time24: String): String {
        val parts = time24.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val min = parts.getOrNull(1) ?: "00"
        val suffix = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "$hour12:$min $suffix"
    }

    private fun parseTime12hTo24h(time12: String): String {
        val match = Regex("""(\d+):(\d+)\s*(AM|PM)""", RegexOption.IGNORE_CASE).find(time12)
            ?: return "09:00"

        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2]
        val suffix = match.groupValues[3].uppercase()

        if (suffix == "PM" && hour != 12) hour += 12
        if (suffix == "AM" && hour == 12) hour = 0

        return "%02d:%s".format(hour, minute)
    }

    override fun onBackPressed() {
        if (isMandatorySetup()) {
            showInfoDialog(
                "Complete setup first",
                "Your professional profile must be completed before returning to the dashboard."
            )
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

class ScheduleDayAdapter(
    private val scheduleMap: LinkedHashMap<String, DaySchedule>
) : RecyclerView.Adapter<ScheduleDayAdapter.DayViewHolder>() {

    private var editable = false

    fun setEditable(value: Boolean) {
        editable = value
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val switchDay: Switch = itemView.findViewById(R.id.switch_day)
        val tvDayName: TextView = itemView.findViewById(R.id.tv_day_name)
        val containerBlocks: LinearLayout = itemView.findViewById(R.id.container_time_blocks)
        val btnAddBlock: MaterialButton = itemView.findViewById(R.id.btn_add_block)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_day, parent, false)
        return DayViewHolder(view)
    }

    override fun getItemCount(): Int = scheduleMap.size

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = ProfileActivity.DAYS[position]
        val schedule = scheduleMap[day] ?: return

        holder.tvDayName.text = day
        holder.switchDay.setOnCheckedChangeListener(null)
        holder.switchDay.isChecked = schedule.active
        holder.switchDay.isEnabled = editable
        holder.itemView.alpha = if (schedule.active) 1f else 0.55f
        holder.btnAddBlock.isVisible = editable && schedule.active

        holder.switchDay.setOnCheckedChangeListener { _, checked ->
            if (!editable) return@setOnCheckedChangeListener
            schedule.active = checked
            holder.itemView.alpha = if (checked) 1f else 0.55f
            holder.btnAddBlock.isVisible = checked
            rebuildTimeBlocks(holder, schedule)
        }

        holder.btnAddBlock.setOnClickListener {
            schedule.timeBlocks.add(TimeBlock())
            rebuildTimeBlocks(holder, schedule)
        }

        rebuildTimeBlocks(holder, schedule)
    }

    private fun rebuildTimeBlocks(holder: DayViewHolder, schedule: DaySchedule) {
        holder.containerBlocks.removeAllViews()
        val ctx = holder.itemView.context

        schedule.timeBlocks.forEachIndexed { index, block ->
            val row = LayoutInflater.from(ctx)
                .inflate(R.layout.item_time_block_row, holder.containerBlocks, false)

            val btnStart = row.findViewById<MaterialButton>(R.id.btn_time_start)
            val btnEnd = row.findViewById<MaterialButton>(R.id.btn_time_end)
            val btnRemove = row.findViewById<MaterialButton>(R.id.btn_remove_block)

            btnStart.text = formatDisplay(block.start)
            btnEnd.text = formatDisplay(block.end)

            btnStart.isEnabled = editable && schedule.active
            btnEnd.isEnabled = editable && schedule.active
            btnRemove.isVisible = editable && schedule.active && schedule.timeBlocks.size > 1

            btnStart.setOnClickListener {
                showTimePicker(ctx, block.start) { h, m ->
                    block.start = "%02d:%02d".format(h, m)
                    btnStart.text = formatDisplay(block.start)
                }
            }

            btnEnd.setOnClickListener {
                showTimePicker(ctx, block.end) { h, m ->
                    block.end = "%02d:%02d".format(h, m)
                    btnEnd.text = formatDisplay(block.end)
                }
            }

            btnRemove.setOnClickListener {
                if (schedule.timeBlocks.size > 1) {
                    schedule.timeBlocks.removeAt(index)
                    rebuildTimeBlocks(holder, schedule)
                }
            }

            holder.containerBlocks.addView(row)
        }
    }

    private fun showTimePicker(ctx: Context, current: String, onSet: (Int, Int) -> Unit) {
        val parts = current.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(ctx, { _, h, m -> onSet(h, m) }, hour, minute, false).show()
    }

    private fun formatDisplay(time24: String): String {
        val parts = time24.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val min = parts.getOrNull(1) ?: "00"
        val suffix = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "$hour12:$min $suffix"
    }
}