package com.therapea.app.features.therapists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import coil.load
import com.google.android.flexbox.FlexboxLayout
import com.therapea.app.BuildConfig
import com.therapea.app.R
import com.therapea.app.features.checkout.CheckoutActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TherapistProfileActivity : Activity() {

    private val client = OkHttpClient()
    private val scope  = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var stateLoading: View
    private lateinit var stateError:   View
    private lateinit var stateContent: View

    private var therapistId   = ""
    private var therapistData = JSONObject()

    private val apiBaseUrl = BuildConfig.BASE_URL
        .removeSuffix("/api/")
        .removeSuffix("/api")
        .trimEnd('/') + "/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_therapist_profile)

        therapistId   = intent.getStringExtra("THERAPIST_ID") ?: ""
        stateLoading  = findViewById(R.id.stateLoading)
        stateError    = findViewById(R.id.stateError)
        stateContent  = findViewById(R.id.stateContent)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRetry).setOnClickListener      { loadProfile() }
        findViewById<Button>(R.id.btnErrorBack).setOnClickListener  { finish() }

        findViewById<Button>(R.id.btnBook).setOnClickListener {
            if (therapistData.length() == 0) {
                AlertDialog.Builder(this)
                    .setTitle("Not Ready")
                    .setMessage("Provider information is still loading. Please wait a moment.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            // ── Pass every field CheckoutActivity.therapistFromIntent() expects ──
            startActivity(
                Intent(this, CheckoutActivity::class.java).apply {
                    putExtra("therapistId",          therapistData.optString("id"))
                    putExtra("therapistName",        therapistData.optString("name", "Provider"))
                    putExtra("therapistEmail",       therapistData.optString("email"))
                    putExtra("therapistRate",        therapistData.optInt("rate", 1500))
                    putExtra("therapistSchedule",    therapistData.optString("availableSchedule", "Monday: 9:00 AM - 5:00 PM"))
                    putExtra("therapistWhatToExpect",therapistData.optString("whatToExpect"))
                }
            )
        }

        loadProfile()
    }

    private fun showState(view: View) {
        stateLoading.visibility = if (view == stateLoading) View.VISIBLE else View.GONE
        stateError.visibility   = if (view == stateError)   View.VISIBLE else View.GONE
        stateContent.visibility = if (view == stateContent) View.VISIBLE else View.GONE
    }

    private fun showError(title: String, message: String) {
        findViewById<TextView>(R.id.tvErrorTitle).text = title
        findViewById<TextView>(R.id.tvErrorMsg).text   = message
        showState(stateError)
    }

    private fun loadProfile() {
        if (therapistId.isBlank()) {
            showError("Provider Not Found", "No provider was selected.")
            return
        }

        // ── Use data passed directly from FindTherapistActivity ──
        val nameFromIntent = intent.getStringExtra("therapistName")
        if (!nameFromIntent.isNullOrBlank()) {
            val doc = JSONObject().apply {
                put("id",               therapistId)
                put("name",             nameFromIntent)
                put("title",            intent.getStringExtra("therapistTitle")      ?: "Licensed Professional")
                put("bio",              "")
                put("experience",       intent.getStringExtra("therapistExperience") ?: "Verified")
                put("rate",             intent.getDoubleExtra("therapistRate", 1500.0))
                put("rating",           intent.getDoubleExtra("therapistRating", 5.0))
                put("online",           intent.getBooleanExtra("therapistOnline", true))
                put("availableSchedule","")
                put("whatToExpect",     "")

                val specs = intent.getStringArrayListExtra("therapistSpecialties")
                val arr   = JSONArray()
                specs?.forEach { arr.put(it) }
                put("specialties", arr)
            }
            therapistData = doc
            populateUI(doc)
            showState(stateContent)

            // Still fetch full profile in background to fill in bio/schedule
            fetchFullProfile()
            return
        }

        // Fallback: fetch from API (original path)
        showState(stateLoading)
        fetchFullProfile()
    }

    private fun fetchFullProfile() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("${apiBaseUrl}api/doctors/list")
                        .build()
                ).execute()

                if (!response.isSuccessful) return@launch

                val json    = JSONObject(response.body?.string() ?: "{}")
                val doctors = json.optJSONArray("doctors") ?: JSONArray()
                var found: JSONObject? = null

                for (i in 0 until doctors.length()) {
                    val d = doctors.getJSONObject(i)
                    if (d.optString("id") == therapistId) { found = d; break }
                }

                withContext(Dispatchers.Main) {
                    if (found != null) {
                        therapistData = found
                        populateUI(found)
                        showState(stateContent)
                    } else if (stateContent.visibility != View.VISIBLE) {
                        showError("Provider Not Found", "We couldn't find the provider you were looking for.")
                    }
                    // If content was already showing from intent data, silently update
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (stateContent.visibility != View.VISIBLE) {
                        showError("Connection Error", "Please check your internet connection and try again.")
                    }
                }
            }
        }
    }

    private fun populateUI(doc: JSONObject) {
        val name       = doc.optString("name", "Provider")
        val cleanName  = name.replace("Dr. ", "").trim()
        val title      = doc.optString("title", "Licensed Professional")
        val bio        = doc.optString("bio", "Dedicated to providing compassionate, evidence-based mental health care.")
        val experience = doc.optString("experience", "Verified")
        val online     = doc.optBoolean("online", true)
        val rate       = doc.optDouble("rate", 1500.0)
        val whatToExpect = doc.optString("whatToExpect", "Your therapist will share what to expect shortly.")

        findViewById<TextView>(R.id.tvName).text    = name
        findViewById<TextView>(R.id.tvTitle).text   = title
        findViewById<TextView>(R.id.tvBio).text     = bio.ifBlank { "No bio available yet." }
        findViewById<TextView>(R.id.tvRate).text    = "₱${String.format("%,.0f", rate)}"
        findViewById<TextView>(R.id.tvWhatToExpect).text = whatToExpect.ifBlank {
            "Your therapist will share what to expect shortly."
        }

        // Meta line
        val metaParts = mutableListOf<String>()
        if (experience.isNotBlank()) metaParts.add("🎓 $experience")
        metaParts.add(if (online) "📱 Telehealth Available" else "🏥 Clinic Only")
        findViewById<TextView>(R.id.tvMeta).text = metaParts.joinToString("   ·   ")

        val scheduleRaw = doc.optString("availableSchedule", "")
        val llScheduleRows = findViewById<LinearLayout>(R.id.llScheduleRows)
        llScheduleRows.removeAllViews()

        if (scheduleRaw.isBlank()) {
            val tvEmpty = TextView(this).apply {
                text = "Schedule not set"
                setTextColor(android.graphics.Color.parseColor("#6B7A6E"))
                textSize = 14f
            }
            llScheduleRows.addView(tvEmpty)
        } else {
            // 1. Flatten the string to remove any hidden line breaks and standardize separators
            var normalized = scheduleRaw.replace("\n", ", ").replace(" | ", ", ")

            // 2. Insert a hidden delimiter ("|") strictly before the Days of the week
            val dayRegex = Regex(",?\\s*(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday):")
            normalized = normalized.replace(dayRegex, "|$1:")

            // 3. Split into clean chunks per day
            val daysList = normalized.split("|").map { it.trim() }.filter { it.isNotEmpty() }

            // 4. Build a clean row for each day
            for (dayString in daysList) {
                // Split ONLY at the first colon (the one right after the day name)
                val parts = dayString.split(":", limit = 2)
                val dayName = parts.getOrNull(0)?.trim() ?: ""

                // Stack multiple time slots neatly by turning commas into line breaks
                val times = parts.getOrNull(1)?.trim()?.replace(Regex(",\\s*"), "\n") ?: ""

                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, dp(12))
                    }
                }

                // Left side: The Day
                val tvDay = TextView(this).apply {
                    text = dayName
                    setTextColor(android.graphics.Color.parseColor("#6B7A6E"))
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(dp(85), LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                // Right side: The stacked Times
                val tvTimes = TextView(this).apply {
                    text = times
                    setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                    textSize = 13f
                    setLineSpacing(4f, 1f)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                rowLayout.addView(tvDay)
                rowLayout.addView(tvTimes)
                llScheduleRows.addView(rowLayout)
            }
        }

        // Initials
        val initials = cleanName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("").take(2).uppercase()
        findViewById<TextView>(R.id.tvInitials).text = initials

        val imageUrl  = doc.optString("profilePictureUrl")
        val imageView = findViewById<ImageView>(R.id.ivAvatar)
        val tvInitials = findViewById<TextView>(R.id.tvInitials)

        if (imageUrl.isNotBlank()) {
            // Apply circular clip
            imageView.setBackgroundResource(R.drawable.bg_circle_white)
            imageView.clipToOutline  = true
            imageView.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

            imageView.load(imageUrl) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        imageView.visibility  = View.VISIBLE
                        tvInitials.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        imageView.visibility  = View.GONE
                        tvInitials.visibility = View.VISIBLE
                    }
                )
            }
        } else {
            imageView.visibility  = View.GONE
            tvInitials.visibility = View.VISIBLE
        }

        // Specialties chips
        val flexbox     = findViewById<FlexboxLayout>(R.id.flexSpecialties)
        val specialties = doc.optJSONArray("specialties")
            ?: JSONArray().put("Mental Wellness")

        flexbox.removeAllViews()
        for (i in 0 until specialties.length()) {
            flexbox.addView(buildChip(specialties.optString(i)))
        }
    }

    private fun buildChip(text: String) = TextView(this).apply {
        layoutParams = FlexboxLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dp(10), dp(10)) }
        this.text = text
        gravity   = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(8))
        textSize  = 13f
        setTextColor(0xFF1C1F1A.toInt())
        setBackgroundResource(R.drawable.bg_card_bordered)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}