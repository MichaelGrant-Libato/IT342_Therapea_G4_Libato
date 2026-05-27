package com.therapea.app.features.messages

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.*
import com.therapea.app.BuildConfig
import com.therapea.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class ChatActivity : Activity() {

    private lateinit var btnBack:                 TextView
    private lateinit var tvChatAvatar:            TextView
    private lateinit var ivChatAvatar:            android.widget.ImageView
    private lateinit var tvChatName:              TextView
    private lateinit var tvChatRole:              TextView
    private lateinit var messageHistoryScroll:    ScrollView
    private lateinit var messageHistoryContainer: LinearLayout
    private lateinit var etMessageInput:          EditText
    private lateinit var btnSendMessage:          Button

    private val handler    = Handler(Looper.getMainLooper())
    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/"
    private val messages   = mutableListOf<MessageData>()

    private var contactEmail          = ""
    private var contactName           = ""
    private var contactRole           = ""
    private var contactProfilePicture = ""
    private var myEmail               = ""
    private var myProfilePicture      = ""
    private var isSending             = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            fetchMessages(showErrors = false)
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // ── Read intent extras ────────────────────────────────────────
        contactEmail          = intent.getStringExtra("contactEmail")          ?: ""
        contactName           = intent.getStringExtra("contactName")           ?: "Contact"
        contactRole           = intent.getStringExtra("contactRole")           ?: "PATIENT"
        contactProfilePicture = intent.getStringExtra("contactProfilePicture") ?: ""
        myEmail               = intent.getStringExtra("currentEmail")          ?: ""
        myProfilePicture      = intent.getStringExtra("currentProfilePicture") ?: ""

        btnBack               = findViewById(R.id.btnChatBack)
        tvChatAvatar          = findViewById(R.id.tvChatAvatar)
        ivChatAvatar          = findViewById(R.id.ivChatAvatar)
        tvChatName            = findViewById(R.id.tvChatName)
        tvChatRole            = findViewById(R.id.tvChatRole)
        messageHistoryScroll  = findViewById(R.id.messageHistoryScroll)
        messageHistoryContainer = findViewById(R.id.messageHistoryContainer)
        etMessageInput        = findViewById(R.id.etMessageInput)
        btnSendMessage        = findViewById(R.id.btnSendMessage)

        // ── Contact header ────────────────────────────────────────────
        tvChatName.text = contactName
        tvChatRole.text = if (contactRole == "DOCTOR") "Licensed Therapist" else "Patient"

        if (contactProfilePicture.isNotBlank()) {
            tvChatAvatar.visibility = View.GONE
            ivChatAvatar.visibility = View.VISIBLE
            loadImageInto(ivChatAvatar, contactProfilePicture)
        } else {
            tvChatAvatar.visibility = View.VISIBLE
            ivChatAvatar.visibility = View.GONE
            tvChatAvatar.text = initials(contactName)
        }

        btnBack.setOnClickListener { finish() }
        btnSendMessage.setOnClickListener { sendMessage() }

        fetchMessages(showErrors = true)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(pollRunnable, 3000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
        getSharedPreferences("therapea_messages", MODE_PRIVATE)
            .edit().putLong("last_read_$contactEmail", System.currentTimeMillis()).apply()
    }

    private fun fetchMessages(showErrors: Boolean) {
        Thread {
            try {
                val json = JSONObject(get(
                    "${apiBaseUrl}api/messages?user1=${encode(myEmail)}&user2=${encode(contactEmail)}"
                ))
                val arr     = json.optJSONArray("messages") ?: JSONArray()
                val fetched = (0 until arr.length()).map { i ->
                    val m = arr.getJSONObject(i)
                    MessageData(
                        id            = m.optString("id", i.toString()),
                        senderEmail   = m.optString("senderEmail"),
                        receiverEmail = m.optString("receiverEmail"),
                        content       = m.optString("content"),
                        timestamp     = m.optString("timestamp")
                    )
                }

                runOnUiThread {
                    val serverIds  = fetched.map { it.id }.toSet()
                    val optimistic = messages.filter { it.id.startsWith("local_") && it.id !in serverIds }
                    messages.clear()
                    messages.addAll(fetched)
                    messages.addAll(optimistic)
                    messages.sortBy { parseTimeMillis(it.timestamp) }
                    renderMessages()
                }
            } catch (e: Exception) {
                if (showErrors) runOnUiThread {
                    Toast.makeText(this, "Could not load messages.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sendMessage() {
        val text = etMessageInput.text.toString().trim()
        if (text.isBlank()) return

        isSending                = true
        btnSendMessage.isEnabled = false
        btnSendMessage.text      = "…"

        val optimisticId = "local_${System.currentTimeMillis()}"
        messages.add(MessageData(optimisticId, myEmail, contactEmail, text,
            System.currentTimeMillis().toString()))
        etMessageInput.setText("")
        renderMessages()

        Thread {
            try {
                val body = JSONObject()
                    .put("senderEmail",   myEmail)
                    .put("receiverEmail", contactEmail)
                    .put("content",       text)
                    .toString()
                post("${apiBaseUrl}api/messages/send", body)
                runOnUiThread {
                    isSending                = false
                    btnSendMessage.isEnabled = true
                    btnSendMessage.text      = "Send"
                    fetchMessages(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    messages.removeAll { it.id == optimisticId }
                    isSending                = false
                    btnSendMessage.isEnabled = true
                    btnSendMessage.text      = "Send"
                    renderMessages()
                    AlertDialog.Builder(this)
                        .setTitle("Not Sent")
                        .setMessage("Message failed. Please try again.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }.start()
    }

    private fun renderMessages() {
        messageHistoryContainer.removeAllViews()

        if (messages.isEmpty()) {
            messageHistoryContainer.addView(TextView(this).apply {
                text     = "Say hello to ${contactName.split(" ").firstOrNull()}! 👋"
                textSize = 14f
                gravity  = Gravity.CENTER
                setTextColor(Color.parseColor("#7A8077"))
                setPadding(dp(20), dp(80), dp(20), dp(80))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return
        }

        messages.forEach { msg ->
            messageHistoryContainer.addView(messageBubble(msg, msg.senderEmail == myEmail))
        }
        messageHistoryScroll.post { messageHistoryScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun messageBubble(message: MessageData, sentByMe: Boolean): LinearLayout {
        val maxBubbleWidth = (resources.displayMetrics.widthPixels * 0.74).toInt()

        // ── Avatar for this side ──────────────────────────────────────
        val avatarSize = dp(32)
        val avatarUrl  = if (sentByMe) myProfilePicture else contactProfilePicture
        val avatarName = if (sentByMe) myEmail else contactName

        val avatarView: View = if (avatarUrl.isNotBlank()) {
            ImageView(this).apply{
                scaleType    = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true                          // ← makes it circular
                background   = resources.getDrawable(R.drawable.bg_circle_green, null)
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                loadImageInto(this, avatarUrl)
            }
        } else {
            TextView(this).apply {
                text     = initials(avatarName)
                gravity  = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = 11f
                setTextColor(Color.parseColor("#0A5C36"))
                background   = resources.getDrawable(R.drawable.bg_circle_green, null)
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
            }
        }

        // ── Bubble ────────────────────────────────────────────────────
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(
                if (sentByMe) R.drawable.bg_bubble_sent else R.drawable.bg_bubble_received
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        bubble.addView(TextView(this).apply {
            text     = message.content
            textSize = 14f
            maxWidth = maxBubbleWidth
            setTextColor(if (sentByMe) Color.WHITE else Color.parseColor("#1C1F1A"))
        })

        bubble.addView(TextView(this).apply {
            text     = formatTime(message.timestamp)
            textSize = 10f
            maxWidth = maxBubbleWidth
            setTextColor(
                if (sentByMe) Color.parseColor("#A7F3D0") else Color.parseColor("#94A3B8")
            )
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
        })

        // ── Row wrapper: avatar + bubble ──────────────────────────────
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.BOTTOM   // align avatar to bottom of bubble
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(4) }
        }

        val bubbleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (sentByMe) {
            // sent: spacer | bubble | avatar
            wrapper.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            wrapper.addView(bubble, bubbleParams.apply { marginEnd = dp(8) })
            wrapper.addView(avatarView)
        } else {
            // received: avatar | bubble | spacer
            wrapper.addView(avatarView)
            wrapper.addView(bubble, bubbleParams.apply { marginStart = dp(8) })
            wrapper.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }

        return wrapper
    }

    // ── Image loader ──────────────────────────────────────────────────────
    private fun loadImageInto(iv: android.widget.ImageView, url: String) {
        Thread {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                runOnUiThread { iv.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun parseTimeMillis(value: String): Long {
        if (value.isBlank()) return 0L
        value.toLongOrNull()?.let { return it }
        listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd'T'HH:mm:ss").forEach {
            try { return SimpleDateFormat(it, Locale.US).parse(value)?.time ?: 0L } catch (_: Exception) {}
        }
        return 0L
    }

    private fun formatTime(value: String): String {
        val m = parseTimeMillis(value)
        return if (m <= 0L) "Sending…" else SimpleDateFormat("h:mm a", Locale.US).format(m)
    }

    private fun initials(name: String): String {
        val p = name.trim().split(" ").filter { it.isNotBlank() }
        return if (p.size >= 2) "${p.first().first()}${p.last().first()}".uppercase()
        else p.firstOrNull()?.take(2)?.uppercase() ?: "?"
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.connectTimeout = 10000; c.readTimeout = 10000
        val s = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return s.bufferedReader().use(BufferedReader::readText)
    }

    private fun post(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 10000
        c.doInput = true; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(c.outputStream).use { it.write(body); it.flush() }
        val s = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return s.bufferedReader().use(BufferedReader::readText)
    }

    private fun encode(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int)        = (v * resources.displayMetrics.density).toInt()

    data class MessageData(
        val id: String, val senderEmail: String, val receiverEmail: String,
        val content: String, val timestamp: String
    )
}