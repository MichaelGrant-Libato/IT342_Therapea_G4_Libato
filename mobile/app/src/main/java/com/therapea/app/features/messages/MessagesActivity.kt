package com.therapea.app.features.messages

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.therapea.app.BuildConfig
import com.therapea.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class MessagesActivity : Activity() {

    private lateinit var etSearch:        EditText
    private lateinit var recentContainer: LinearLayout
    private lateinit var rvConversations: RecyclerView
    private lateinit var tvStatus:        TextView

    private val handler    = Handler(Looper.getMainLooper())
    private val apiBaseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/"

    private var currentUser: UserData? = null
    private val contacts     = mutableListOf<ContactData>()
    private val lastMessages = mutableMapOf<String, LastMessage>()
    private val unreadMap    = mutableMapOf<String, Boolean>()
    private var searchQuery  = ""

    private lateinit var convAdapter: ConversationAdapter

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollLastMessages()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        etSearch        = findViewById(R.id.etContactSearch)
        recentContainer = findViewById(R.id.recentContactsContainer)
        rvConversations = findViewById(R.id.rvConversations)
        tvStatus        = findViewById(R.id.tvMessagesStatus)

        rvConversations.layoutManager = LinearLayoutManager(this)
        convAdapter = ConversationAdapter(mutableListOf()) { contact -> openChat(contact) }
        rvConversations.adapter = convAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { searchQuery = s.toString(); renderList() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        readUserSession()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(pollRunnable, 3000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    private fun readUserSession() {
        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)
        val data  = prefs.getString("user_data", null)
        if (data != null) {
            try {
                val json = JSONObject(data)
                currentUser = UserData(
                    email             = json.optString("email"),
                    fullName          = json.optString("fullName", "User"),
                    role              = json.optString("role", "PATIENT").uppercase(),
                    profilePictureUrl = json.optString("profilePictureUrl")
                )
            } catch (_: Exception) {}
        }
        if (currentUser == null) {
            val e = intent.getStringExtra("email")
            if (!e.isNullOrBlank()) currentUser = UserData(
                email             = e,
                fullName          = intent.getStringExtra("fullName") ?: "User",
                role              = (intent.getStringExtra("role") ?: "PATIENT").uppercase(),
                profilePictureUrl = intent.getStringExtra("profilePictureUrl") ?: ""
            )
        }
    }

    private fun loadContacts() {
        val user = currentUser ?: return
        showStatus("Loading chats…")

        Thread {
            try {
                val loaded = if (user.role == "DOCTOR") loadDoctorContacts(user.email)
                else                       loadPatientContacts(user.email)

                runOnUiThread {
                    contacts.clear()
                    // dedup handled inside each loader — just filter blanks here
                    contacts.addAll(loaded.filter { it.email.isNotBlank() })
                    renderRecent()
                    pollLastMessages()
                    hideStatus()
                }
            } catch (e: Exception) {
                runOnUiThread { showStatus("Could not load chats.") }
            }
        }.start()
    }

    private fun loadDoctorContacts(email: String): List<ContactData> {
        val json = JSONObject(get("${apiBaseUrl}api/patients/doctor?email=${encode(email)}"))
        if (!json.optBoolean("success", false)) return emptyList()

        val patients   = json.optJSONArray("patients") ?: JSONArray()
        val uniquePats = mutableMapOf<String, ContactData>()

        for (i in 0 until patients.length()) {
            val p        = patients.getJSONObject(i)
            val patEmail = p.optString("email").trim()
            val patName  = p.optString("fullName", p.optString("name", "")).trim()

            if (patEmail.isBlank()) continue

            val existing = uniquePats[patEmail]
            if (existing == null || patName.length > existing.name.length) {
                uniquePats[patEmail] = ContactData(
                    email             = patEmail,
                    name              = patName.ifBlank { "Patient" },
                    role              = "PATIENT",
                    profilePictureUrl = p.optString("profilePictureUrl")
                )
            }
        }
        return uniquePats.values.toList()
    }

    private fun loadPatientContacts(email: String): List<ContactData> {
        val json = JSONObject(get("${apiBaseUrl}api/appointments/user?email=${encode(email)}"))
        if (!json.optBoolean("success", false)) return emptyList()

        val appointments = json.optJSONArray("appointments") ?: JSONArray()
        val uniqueDocs   = mutableMapOf<String, ContactData>()

        for (i in 0 until appointments.length()) {
            val apt           = appointments.getJSONObject(i)
            val providerEmail = apt.optString("providerEmail").trim()
            val providerName  = apt.optString("providerName", "").trim()

            if (providerEmail.isBlank()) continue

            val existing = uniqueDocs[providerEmail]
            if (existing == null || providerName.length > existing.name.length) {
                uniqueDocs[providerEmail] = ContactData(
                    email             = providerEmail,
                    name              = providerName.ifBlank { "Provider" },
                    role              = "DOCTOR",
                    profilePictureUrl = apt.optString("providerProfilePictureUrl")
                )
            }
        }
        return uniqueDocs.values.toList()
    }

    private fun pollLastMessages() {
        val user = currentUser ?: return
        Thread {
            contacts.forEach { contact ->
                try {
                    val json = JSONObject(get(
                        "${apiBaseUrl}api/messages?user1=${encode(user.email)}&user2=${encode(contact.email)}"
                    ))
                    val arr = json.optJSONArray("messages") ?: JSONArray()
                    if (arr.length() > 0) {
                        val last = arr.getJSONObject(arr.length() - 1)
                        val lm   = LastMessage(
                            content     = last.optString("content"),
                            timestamp   = last.optString("timestamp"),
                            senderEmail = last.optString("senderEmail")
                        )
                        val lastRead = getLastRead(contact.email)
                        val msgTime  = parseTimeMillis(lm.timestamp)
                        lastMessages[contact.email] = lm
                        unreadMap[contact.email]    = lm.senderEmail == contact.email && msgTime > lastRead
                    }
                } catch (_: Exception) {}
            }
            runOnUiThread { renderList() }
        }.start()
    }

    private fun renderRecent() {
        recentContainer.removeAllViews()
        contacts.take(8).forEach { contact ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = android.view.Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                setOnClickListener { openChat(contact) }
            }

            // Avatar: photo if available, else initials
            if (contact.profilePictureUrl.isNotBlank()) {
                val iv = android.widget.ImageView(this).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                        setBackgroundResource(R.drawable.admin_bg_pill_green)
                    }
                }
                loadImageInto(iv, contact.profilePictureUrl)
                item.addView(iv)
            } else {
                item.addView(TextView(this).apply {
                    text     = initials(contact.name)
                    gravity  = android.view.Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 14f
                    setTextColor(Color.parseColor("#0A5C36"))
                    setBackgroundResource(R.drawable.admin_bg_pill_green)
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                })
            }

            item.addView(TextView(this).apply {
                text     = contact.name.split(" ").firstOrNull() ?: contact.name
                textSize = 11f
                gravity  = android.view.Gravity.CENTER
                setTextColor(Color.parseColor("#1C1F1A"))
                setPadding(0, dp(4), 0, 0)
            })
            recentContainer.addView(item)
        }
    }

    private fun renderList() {
        val filtered = contacts
            .filter {
                searchQuery.isBlank() ||
                        it.name.contains(searchQuery, true) ||
                        it.email.contains(searchQuery, true)
            }
            .sortedByDescending { parseTimeMillis(lastMessages[it.email]?.timestamp ?: "") }

        val items = filtered.map { contact ->
            ConversationItem(
                contact   = contact,
                lastMsg   = lastMessages[contact.email]?.content ?: "No messages yet",
                lastTime  = lastMessages[contact.email]?.let { formatTime(it.timestamp) } ?: "",
                hasUnread = unreadMap[contact.email] == true
            )
        }
        convAdapter.updateItems(items)
    }

    private fun openChat(contact: ContactData) {
        saveLastRead(contact.email, System.currentTimeMillis())
        unreadMap[contact.email] = false
        renderList()

        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("contactEmail",           contact.email)
            putExtra("contactName",            contact.name)
            putExtra("contactRole",            contact.role)
            putExtra("contactProfilePicture",  contact.profilePictureUrl)
            putExtra("currentEmail",           currentUser?.email ?: "")
            putExtra("currentFullName",        currentUser?.fullName ?: "")
            putExtra("currentRole",            currentUser?.role ?: "PATIENT")
            putExtra("currentProfilePicture",  currentUser?.profilePictureUrl ?: "")
        })
    }

    // ── Image loader (uses Coil which is already in your dependencies) ────
    private fun loadImageInto(iv: android.widget.ImageView, url: String) {
        Thread {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeStream(
                    java.net.URL(url).openStream()
                )
                runOnUiThread { iv.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun showStatus(msg: String) { tvStatus.text = msg; tvStatus.visibility = View.VISIBLE }
    private fun hideStatus()            { tvStatus.visibility = View.GONE }

    private fun saveLastRead(email: String, v: Long) =
        getSharedPreferences("therapea_messages", MODE_PRIVATE)
            .edit().putLong("last_read_$email", v).apply()

    private fun getLastRead(email: String): Long =
        getSharedPreferences("therapea_messages", MODE_PRIVATE)
            .getLong("last_read_$email", 0L)

    private fun parseTimeMillis(value: String): Long {
        if (value.isBlank()) return 0L
        value.toLongOrNull()?.let { return it }
        listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd'T'HH:mm:ss").forEach {
            try { return SimpleDateFormat(it, Locale.US).parse(value)?.time ?: 0L } catch (_: Exception) {}
        }
        return 0L
    }

    private fun formatTime(value: String): String {
        val millis = parseTimeMillis(value)
        return if (millis <= 0L) "" else SimpleDateFormat("h:mm a", Locale.US).format(millis)
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

    private fun encode(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int)        = (v * resources.displayMetrics.density).toInt()

    // ── Adapter ───────────────────────────────────────────────────────────
    inner class ConversationAdapter(
        private val items: MutableList<ConversationItem>,
        private val onClick: (ContactData) -> Unit
    ) : RecyclerView.Adapter<ConversationAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val avatar:      TextView = v.findViewById(R.id.tvConvAvatar)
            val avatarImg:   android.widget.ImageView? = v.findViewById(R.id.ivConvAvatar)
            val name:        TextView = v.findViewById(R.id.tvConvName)
            val lastMsg:     TextView = v.findViewById(R.id.tvConvLastMessage)
            val time:        TextView = v.findViewById(R.id.tvConvTime)
            val unreadDot:   View     = v.findViewById(R.id.viewUnreadDot)
            val unreadBadge: View     = v.findViewById(R.id.viewUnreadBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]

            // Show photo if available, else initials
            if (item.contact.profilePictureUrl.isNotBlank() && h.avatarImg != null) {
                h.avatar.visibility    = View.GONE
                h.avatarImg.visibility = View.VISIBLE
                loadImageInto(h.avatarImg, item.contact.profilePictureUrl)
            } else {
                h.avatar.visibility    = View.VISIBLE
                h.avatarImg?.visibility = View.GONE
                h.avatar.text = initials(item.contact.name)
            }

            h.name.text    = item.contact.name
            h.lastMsg.text = item.lastMsg
            h.time.text    = item.lastTime
            h.name.typeface = if (item.hasUnread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            h.lastMsg.setTextColor(
                Color.parseColor(if (item.hasUnread) "#1C1F1A" else "#7A8077")
            )
            h.unreadDot.visibility   = if (item.hasUnread) View.VISIBLE else View.GONE
            h.unreadBadge.visibility = if (item.hasUnread) View.VISIBLE else View.GONE
            h.itemView.setOnClickListener { onClick(item.contact) }
        }

        fun updateItems(new: List<ConversationItem>) {
            items.clear(); items.addAll(new); notifyDataSetChanged()
        }
    }

    // ── Data classes ──────────────────────────────────────────────────────
    data class UserData(
        val email:             String,
        val fullName:          String,
        val role:              String,
        val profilePictureUrl: String = ""
    )

    // ── profilePictureUrl added ───────────────────────────────────────────
    data class ContactData(
        val email:             String,
        val name:              String,
        val role:              String,
        val profilePictureUrl: String = ""
    )

    data class LastMessage(val content: String, val timestamp: String, val senderEmail: String)

    data class ConversationItem(
        val contact:   ContactData,
        val lastMsg:   String,
        val lastTime:  String,
        val hasUnread: Boolean
    )
}