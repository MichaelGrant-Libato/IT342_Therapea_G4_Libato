// app/src/main/java/com/therapea/app/features/settings/SettingsActivity.kt
package com.therapea.app.features.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.therapea.app.R
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.net.URLEncoder

class SettingsActivity : Activity() {

    private val apiBaseUrl = "http://10.0.2.2:8083"
    private val httpClient = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var userEmail = ""
    private var activeTab = SettingsTab.PREFERENCES
    private var isSaving = false

    private val settings = UserSettings()

    private lateinit var root: LinearLayout
    private lateinit var tvAlert: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvCardTitle: TextView
    private lateinit var tvCardSubtitle: TextView

    private lateinit var btnBack: TextView
    private lateinit var btnNotifications: MaterialButton
    private lateinit var btnPreferences: MaterialButton
    private lateinit var btnSaveNotifications: MaterialButton
    private lateinit var btnSavePreferences: MaterialButton

    private lateinit var cardNotifications: View
    private lateinit var cardPreferences: View
    private lateinit var progressBar: View

    private lateinit var swEmailAlerts: SwitchMaterial
    private lateinit var swSmsAlerts: SwitchMaterial
    private lateinit var swMarketingEmails: SwitchMaterial

    private lateinit var spLanguage: Spinner
    private lateinit var spTimezone: Spinner
    private lateinit var spTheme: Spinner

    private val languages = listOf("English (US)", "Tagalog", "Cebuano")
    private val timezones = listOf("Asia/Manila (PHT)", "America/New_York (EST)", "Europe/London (GMT)")
    private val themes = listOf("Light", "Dark")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        readSession()
        if (userEmail.isBlank()) {
            showDialog(
                title = "Session expired",
                message = "Please sign in again before changing your settings.",
                onClose = { finish() }
            )
            return
        }

        bindViews()
        setupSpinners()
        setupListeners()
        loadLocalSettings()
        renderSettings()
        loadRemoteSettings()
    }

    private fun bindViews() {
        root = findViewById(R.id.settingsRoot)
        tvAlert = findViewById(R.id.tvSettingsAlert)
        tvTitle = findViewById(R.id.tvSettingsTitle)
        tvSubtitle = findViewById(R.id.tvSettingsSubtitle)
        tvCardTitle = findViewById(R.id.tvCardTitle)
        tvCardSubtitle = findViewById(R.id.tvCardSubtitle)

        btnBack = findViewById(R.id.btnSettingsBack)
        btnNotifications = findViewById(R.id.btnTabNotifications)
        btnPreferences = findViewById(R.id.btnTabPreferences)
        btnSaveNotifications = findViewById(R.id.btnSaveNotifications)
        btnSavePreferences = findViewById(R.id.btnSavePreferences)

        cardNotifications = findViewById(R.id.cardNotifications)
        cardPreferences = findViewById(R.id.cardPreferences)
        progressBar = findViewById(R.id.settingsProgress)

        swEmailAlerts = findViewById(R.id.swEmailAlerts)
        swSmsAlerts = findViewById(R.id.swSmsAlerts)
        swMarketingEmails = findViewById(R.id.swMarketingEmails)

        spLanguage = findViewById(R.id.spLanguage)
        spTimezone = findViewById(R.id.spTimezone)
        spTheme = findViewById(R.id.spTheme)
    }

    private fun setupSpinners() {
        spLanguage.adapter = spinnerAdapter(languages)
        spTimezone.adapter = spinnerAdapter(timezones)
        spTheme.adapter = spinnerAdapter(themes)

        spLanguage.onItemSelectedListener = spinnerListener { settings.language = languages[it] }
        spTimezone.onItemSelectedListener = spinnerListener { settings.timezone = timezones[it] }
        spTheme.onItemSelectedListener = spinnerListener {
            settings.theme = themes[it]
            applyTheme()
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnNotifications.setOnClickListener {
            activeTab = SettingsTab.NOTIFICATIONS
            updateTabs()
        }

        btnPreferences.setOnClickListener {
            activeTab = SettingsTab.PREFERENCES
            updateTabs()
        }

        swEmailAlerts.setOnCheckedChangeListener { _, checked -> settings.emailAlerts = checked }
        swSmsAlerts.setOnCheckedChangeListener { _, checked -> settings.smsAlerts = checked }
        swMarketingEmails.setOnCheckedChangeListener { _, checked -> settings.marketingEmails = checked }

        btnSaveNotifications.setOnClickListener { saveSettings("Notification settings saved.") }
        btnSavePreferences.setOnClickListener { saveSettings("App preferences saved.") }
    }

    private fun readSession() {
        val sessionPrefs = getSharedPreferences("TheraPeaSession", Context.MODE_PRIVATE)
        val raw = sessionPrefs.getString("user_data", null)
            ?: sessionPrefs.getString("temp_session", null)

        if (!raw.isNullOrBlank()) {
            userEmail = JSONObject(raw).optString("email", "")
        }

        if (userEmail.isBlank()) {
            userEmail = getSharedPreferences("therapea_session", Context.MODE_PRIVATE)
                .getString("email", "") ?: ""
        }
    }

    private fun loadLocalSettings() {
        val raw = settingsPrefs().getString(settingsKey(), null)
        if (!raw.isNullOrBlank()) {
            settings.applyJson(JSONObject(raw))
        }
    }

    private fun loadRemoteSettings() {
        setBusy(true)

        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$apiBaseUrl/api/settings?email=${userEmail.encodeUrl()}")
                    .get()
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val body = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }

                if (response.isSuccessful && body.isNotBlank()) {
                    val json = JSONObject(body)
                    val data = if (json.has("settings")) json.getJSONObject("settings") else json
                    settings.applyJson(data)
                    saveLocalSettings()
                    renderSettings()
                }
            } catch (_: Exception) {
                // Web version stores locally; Android keeps that same behavior if backend settings are unavailable.
            } finally {
                setBusy(false)
            }
        }
    }

    private fun saveSettings(successMessage: String) {
        if (isSaving) return

        isSaving = true
        setBusy(true)
        setSaveButtons(false)
        tvAlert.isVisible = false

        saveLocalSettings()
        applyTheme()

        scope.launch {
            val synced = syncSettingsToBackend()

            setBusy(false)
            setSaveButtons(true)
            isSaving = false

            if (synced) {
                showInlineMessage(successMessage, success = true)
            } else {
                showInlineMessage(
                    "Saved on this device. Backend sync is unavailable right now.",
                    success = true
                )
            }
        }
    }

    private suspend fun syncSettingsToBackend(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = settings.toJson().apply {
                    put("email", userEmail)
                }

                val request = Request.Builder()
                    .url("$apiBaseUrl/api/settings")
                    .put(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().isSuccessful
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun renderSettings() {
        swEmailAlerts.isChecked = settings.emailAlerts
        swSmsAlerts.isChecked = settings.smsAlerts
        swMarketingEmails.isChecked = settings.marketingEmails

        spLanguage.setSelection(languages.indexOf(settings.language).coerceAtLeast(0))
        spTimezone.setSelection(timezones.indexOf(settings.timezone).coerceAtLeast(0))
        spTheme.setSelection(themes.indexOf(settings.theme).coerceAtLeast(0))

        updateTabs()
        applyTheme()
    }

    private fun updateTabs() {
        val showNotifications = activeTab == SettingsTab.NOTIFICATIONS

        cardNotifications.isVisible = showNotifications
        cardPreferences.isVisible = !showNotifications

        tvCardTitle.text = if (showNotifications) "Notification Preferences" else "Application Preferences"
        tvCardSubtitle.text = if (showNotifications) {
            "Choose how you want to be alerted about sessions and messages."
        } else {
            "Customize your regional settings and visual theme."
        }

        styleTab(btnNotifications, showNotifications)
        styleTab(btnPreferences, !showNotifications)
    }

    private fun styleTab(button: MaterialButton, selected: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (selected) "#E6F4EA" else "#FFFFFF")
        )
        button.setTextColor(Color.parseColor(if (selected) "#0A5C36" else "#64748B"))
        button.strokeColor = ColorStateList.valueOf(
            Color.parseColor(if (selected) "#BBF7D0" else "#E2E8F0")
        )
    }

    private fun applyTheme() {
        val dark = settings.theme == "Dark"

        root.setBackgroundColor(Color.parseColor(if (dark) "#0F172A" else "#F8FAFC"))
        tvTitle.setTextColor(Color.parseColor(if (dark) "#F8FAFC" else "#1E293B"))
        tvSubtitle.setTextColor(Color.parseColor(if (dark) "#CBD5E1" else "#64748B"))
        tvCardTitle.setTextColor(Color.parseColor(if (dark) "#F8FAFC" else "#1E293B"))
        tvCardSubtitle.setTextColor(Color.parseColor(if (dark) "#CBD5E1" else "#64748B"))

        settingsPrefs().edit()
            .putString("theme_${userEmail}", settings.theme)
            .putString("app_theme", settings.theme)
            .apply()
    }

    private fun saveLocalSettings() {
        settingsPrefs().edit()
            .putString(settingsKey(), settings.toJson().toString())
            .apply()
    }

    private fun showInlineMessage(message: String, success: Boolean) {
        tvAlert.text = message
        tvAlert.setBackgroundResource(
            if (success) R.drawable.settings_bg_alert_success else R.drawable.settings_bg_alert_error
        )
        tvAlert.setTextColor(Color.parseColor(if (success) "#166534" else "#991B1B"))
        tvAlert.isVisible = true
    }

    private fun showDialog(title: String, message: String, onClose: (() -> Unit)? = null) {
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

    private fun setSaveButtons(enabled: Boolean) {
        btnSaveNotifications.isEnabled = enabled
        btnSavePreferences.isEnabled = enabled
        btnSaveNotifications.text = if (enabled) "Save Notification Settings" else "Saving..."
        btnSavePreferences.text = if (enabled) "Save App Preferences" else "Saving..."
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun spinnerListener(onSelected: (Int) -> Unit): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun settingsPrefs() = getSharedPreferences("TheraPeaSettings", Context.MODE_PRIVATE)

    private fun settingsKey() = "settings_$userEmail"

    private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private enum class SettingsTab {
        NOTIFICATIONS,
        PREFERENCES
    }

    private data class UserSettings(
        var emailAlerts: Boolean = true,
        var smsAlerts: Boolean = false,
        var marketingEmails: Boolean = false,
        var language: String = "English (US)",
        var timezone: String = "Asia/Manila (PHT)",
        var theme: String = "Light"
    ) {
        fun applyJson(json: JSONObject) {
            emailAlerts = json.optBoolean("emailAlerts", emailAlerts)
            smsAlerts = json.optBoolean("smsAlerts", smsAlerts)
            marketingEmails = json.optBoolean("marketingEmails", marketingEmails)
            language = json.optString("language", language)
            timezone = json.optString("timezone", timezone)
            theme = json.optString("theme", theme)
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("emailAlerts", emailAlerts)
                put("smsAlerts", smsAlerts)
                put("marketingEmails", marketingEmails)
                put("language", language)
                put("timezone", timezone)
                put("theme", theme)
            }
        }
    }
}