package com.therapea.app.features.home

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.therapea.app.BuildConfig
import com.therapea.app.R
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class NotificationsActivity : Activity() {

    private val API_BASE_URL = BuildConfig.BASE_URL.trimEnd('/')
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var userEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val sessionData = prefs.getString("user_data", null)

        if (sessionData != null) {
            userEmail = JSONObject(sessionData).optString("email")
            fetchNotifications()
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener {
            patchNotif("read-all?email=${URLEncoder.encode(userEmail, "UTF-8")}")
        }
    }

    private fun fetchNotifications() = scope.launch(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${API_BASE_URL}notifications?email=${URLEncoder.encode(userEmail, "UTF-8")}")
                .build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val data = JSONObject(res.body?.string() ?: "{}")
                val notifs = data.optJSONArray("notifications") ?: JSONArray()
                withContext(Dispatchers.Main) { renderNotifications(notifs) }
            }
        } catch (_: Exception) {}
    }

    private fun renderNotifications(notifs: JSONArray) {
        val list = findViewById<LinearLayout>(R.id.llNotificationsList)
        val emptyState = findViewById<TextView>(R.id.tvEmptyNotifs)
        val btnMarkAll = findViewById<TextView>(R.id.btnMarkAllRead)

        list.removeAllViews()

        if (notifs.length() == 0) {
            emptyState.visibility = View.VISIBLE
            btnMarkAll.visibility = View.GONE
            return
        }

        emptyState.visibility = View.GONE
        var hasUnread = false

        for (i in 0 until notifs.length()) {
            val n = notifs.getJSONObject(i)
            val isRead = n.optBoolean("read")
            if (!isRead) hasUnread = true

            // The main card container
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 36, 32, 36)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 24) } // 24dp margin between cards

                // Highlight unread with primary tinted border, otherwise subtle border
                background = createRoundedBorderBg(
                    if (!isRead) R.color.surface else R.color.bg,
                    if (!isRead) R.color.primary_light else R.color.border_subtle,
                    24f
                )

                setOnClickListener { if (!isRead) patchNotif("${n.optInt("id")}/read") }
            }

            // Left Icon Frame (Like the dashboard icons)
            val iconFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(110, 110).apply { marginEnd = 32 }
                background = createRoundedBg(if (!isRead) R.color.primary_light else R.color.border_subtle, 100f)
            }
            val iconView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(60, 60).apply { gravity = Gravity.CENTER }
                setImageResource(R.drawable.ic_bell)
                setColorFilter(getColor(if (!isRead) R.color.primary_dark else R.color.text_sub))
            }
            iconFrame.addView(iconView)

            // Right Text Column
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Title Row (Holds Title + Unread Green Dot)
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            titleRow.addView(TextView(this).apply {
                text = n.optString("title")
                setTextColor(getColor(R.color.text_main))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Unread Dot indicator
            if (!isRead) {
                titleRow.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(20, 20).apply { marginStart = 16 }
                    background = createRoundedBg(R.color.primary, 100f)
                })
            }

            textCol.addView(titleRow)

            // Message Body
            textCol.addView(TextView(this).apply {
                text = n.optString("message")
                setTextColor(getColor(R.color.text_sub))
                textSize = 14f
                setPadding(0, 8, 0, 12)
                setLineSpacing(4f, 1f)
            })

            // Formatted Date
            textCol.addView(TextView(this).apply {
                text = formatFriendlyDate(n.optString("createdAt"))
                setTextColor(getColor(R.color.text_muted))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            })

            row.addView(iconFrame)
            row.addView(textCol)
            list.addView(row)
        }

        btnMarkAll.visibility = if (hasUnread) View.VISIBLE else View.GONE
    }

    private fun formatFriendlyDate(raw: String): String {
        try {
            // Expected: 2026-05-23T22:55:35
            val parts = raw.split("T")
            if (parts.size != 2) return raw
            val dateP = parts[0].split("-")
            val timeP = parts[1].split(":")

            val months = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val m = months[dateP[1].toInt()]
            val d = dateP[2].toInt()

            var h = timeP[0].toInt()
            val ampm = if (h >= 12) "PM" else "AM"
            if (h > 12) h -= 12
            if (h == 0) h = 12

            return "$m $d, $h:${timeP[1]} $ampm"
        } catch (e: Exception) {
            return raw.replace("T", " ").substringBefore(".")
        }
    }

    private fun patchNotif(endpoint: String) = scope.launch(Dispatchers.IO) {
        try {
            client.newCall(
                Request.Builder()
                    .url("${API_BASE_URL}notifications/$endpoint")
                    .patch("".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
            ).execute()
            fetchNotifications() // Refresh UI
        } catch (_: Exception) {}
    }

    private fun createRoundedBg(colorRes: Int, r: Float) = GradientDrawable().apply { setColor(getColor(colorRes)); cornerRadius = r }
    private fun createRoundedBorderBg(fillRes: Int, strokeRes: Int, r: Float) = GradientDrawable().apply { setColor(getColor(fillRes)); setStroke(2, getColor(strokeRes)); cornerRadius = r }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}