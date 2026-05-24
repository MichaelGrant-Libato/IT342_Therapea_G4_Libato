package com.therapea.app.features.map

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.therapea.app.R
import com.therapea.app.features.therapists.TherapistProfileActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class TherapistData(
    val id: String,
    val name: String,
    val title: String,
    val experience: String,
    val rate: Double,
    val rating: Double,
    val reviews: Int,
    val available: Boolean,
    val online: Boolean,
    val specialties: List<String>,
    val profilePictureUrl: String = ""
)

class FindTherapistActivity : Activity() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val ITEMS_PER_PAGE = 6

    private var masterList   = mutableListOf<TherapistData>()
    private var filteredList = mutableListOf<TherapistData>()
    private var pagedList    = mutableListOf<TherapistData>()

    private var currentPage = 1
    private var totalPages  = 1

    private lateinit var rvTherapists: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var spnSpecialty: Spinner
    private lateinit var spnAvailability: Spinner
    private lateinit var tvCount: TextView
    private lateinit var tvPageInfo: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var llEmptyState: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var llPagination: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: TherapistAdapter

    private val specialtiesList  = arrayOf("All Specialties","Anxiety","Depression","Trauma","PTSD","Stress","CBT","EMDR","Grief")
    private val availabilityList = arrayOf("All Availability","Available Today","Available This Week","Online Only")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_therapist)
        initViews()
        setupDropdowns()
        setupListeners()
        fetchDoctorsFromAPI()
    }

    private fun initViews() {
        rvTherapists    = findViewById(R.id.rvTherapists)
        etSearch        = findViewById(R.id.etSearch)
        spnSpecialty    = findViewById(R.id.spnSpecialty)
        spnAvailability = findViewById(R.id.spnAvailability)
        tvCount         = findViewById(R.id.tvCount)
        tvPageInfo      = findViewById(R.id.tvPageInfo)
        btnPrev         = findViewById(R.id.btnPrev)
        btnNext         = findViewById(R.id.btnNext)
        llEmptyState    = findViewById(R.id.llEmptyState)
        tvEmptyMessage  = findViewById(R.id.tvEmptyMessage)
        llPagination    = findViewById(R.id.llPagination)
        progressBar     = findViewById(R.id.progressBar)

        rvTherapists.layoutManager = LinearLayoutManager(this)
        adapter = TherapistAdapter(pagedList)
        rvTherapists.adapter = adapter
    }

    private fun setupDropdowns() {
        spnSpecialty.adapter    = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, specialtiesList)
        spnAvailability.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, availabilityList)
    }

    private fun setupListeners() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFiltersAndPaginate() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dropdownListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) { applyFiltersAndPaginate() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spnSpecialty.onItemSelectedListener    = dropdownListener
        spnAvailability.onItemSelectedListener = dropdownListener

        btnPrev.setOnClickListener { if (currentPage > 1)          { currentPage--; updatePaginationView() } }
        btnNext.setOnClickListener { if (currentPage < totalPages)  { currentPage++; updatePaginationView() } }

        findViewById<Button>(R.id.btnClearFilters).setOnClickListener {
            etSearch.setText("")
            spnSpecialty.setSelection(0)
            spnAvailability.setSelection(0)
            applyFiltersAndPaginate()
        }
    }

    private fun fetchDoctorsFromAPI() {
        progressBar.visibility  = View.VISIBLE
        llEmptyState.visibility = View.GONE
        rvTherapists.visibility = View.GONE

        scope.launch(Dispatchers.IO) {
            try {
                val request  = Request.Builder().url("http://10.0.2.2:8083/api/doctors/list").build()
                val response = client.newCall(request).execute()
                val data     = JSONObject(response.body?.string() ?: "{}")

                if (!data.optBoolean("success", false)) throw Exception("API returned success=false")

                val array    = data.optJSONArray("doctors") ?: JSONArray()
                val tempList = mutableListOf<TherapistData>()

                for (i in 0 until array.length()) {
                    val obj      = array.getJSONObject(i)
                    val rawSpecs = obj.optJSONArray("specialties")
                    val specList = mutableListOf<String>()
                    if (rawSpecs != null) for (j in 0 until rawSpecs.length()) specList.add(rawSpecs.getString(j))

                    tempList.add(TherapistData(
                        id         = obj.optString("id"),
                        name       = obj.optString("name", "Provider"),
                        title      = obj.optString("title", "Licensed Professional"),
                        experience = obj.optString("experience", "Verified"),
                        rate       = obj.optDouble("rate", 1500.0),
                        rating     = obj.optDouble("rating", 5.0),
                        reviews    = obj.optInt("reviews", 0),
                        available  = obj.optBoolean("available", true),
                        online     = obj.optBoolean("online", true),
                        specialties        = specList,
                        profilePictureUrl  = obj.optString("profilePictureUrl", "")
                    ))
                }

                withContext(Dispatchers.Main) {
                    masterList.clear()
                    masterList.addAll(tempList)
                    applyFiltersAndPaginate()
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility  = View.GONE
                    llEmptyState.visibility = View.VISIBLE
                    tvEmptyMessage.text = "Failed to connect to the database. Please check your connection."
                }
            }
        }
    }

    private fun applyFiltersAndPaginate() {
        val q     = etSearch.text.toString().lowercase().trim()
        val spec  = spnSpecialty.selectedItem.toString()
        val avail = spnAvailability.selectedItem.toString()

        filteredList.clear()
        filteredList.addAll(masterList.filter { t ->
            val matchSearch = q.isEmpty() || t.name.lowercase().contains(q) || t.title.lowercase().contains(q) || t.specialties.any { it.lowercase().contains(q) }
            val matchSpec   = spec  == "All Specialties"  || t.specialties.contains(spec)
            val matchAvail  = avail == "All Availability" ||
                    (avail == "Available Today"    && t.available) ||
                    (avail == "Available This Week") ||
                    (avail == "Online Only"         && t.online)
            matchSearch && matchSpec && matchAvail
        })

        currentPage = 1
        totalPages  = ceil(filteredList.size.toDouble() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1)
        tvCount.text = "Showing ${filteredList.size} providers based on your filters."
        updatePaginationView()
    }

    private fun updatePaginationView() {
        pagedList.clear()
        val start = (currentPage - 1) * ITEMS_PER_PAGE
        val end   = (start + ITEMS_PER_PAGE).coerceAtMost(filteredList.size)
        if (start < filteredList.size) pagedList.addAll(filteredList.subList(start, end))

        adapter.notifyDataSetChanged()

        if (filteredList.isEmpty()) {
            llEmptyState.visibility = View.VISIBLE
            rvTherapists.visibility = View.GONE
            llPagination.visibility = View.GONE
            tvEmptyMessage.text = "We couldn't find any therapists matching those criteria."
        } else {
            llEmptyState.visibility = View.GONE
            rvTherapists.visibility = View.VISIBLE
            llPagination.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
            tvPageInfo.text = "Page $currentPage of $totalPages"
            btnPrev.isEnabled = currentPage > 1
            btnNext.isEnabled = currentPage < totalPages
            btnPrev.setTextColor(if (currentPage > 1)          Color.parseColor("#4A5047") else Color.parseColor("#B0B0B0"))
            btnNext.setTextColor(if (currentPage < totalPages)  Color.parseColor("#4A5047") else Color.parseColor("#B0B0B0"))
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    inner class TherapistAdapter(private val list: List<TherapistData>) : RecyclerView.Adapter<TherapistAdapter.CardViewHolder>() {

        inner class CardViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvInitials:     TextView     = v.findViewById(R.id.tvAvatarInitials)
            val ivAvatar:       ImageView    = v.findViewById(R.id.ivDoctorAvatar)   // ← ADD
            val tvName:         TextView     = v.findViewById(R.id.tvDoctorName)
            val tvTitle:        TextView     = v.findViewById(R.id.tvDoctorTitle)
            val tvStars:        TextView     = v.findViewById(R.id.tvStars)
            val llTags:         LinearLayout = v.findViewById(R.id.llTags)
            val tvRate:         TextView     = v.findViewById(R.id.tvSessionRate)
            val llBadges:       LinearLayout = v.findViewById(R.id.llBadges)
            val btnViewProfile: Button       = v.findViewById(R.id.btnViewProfile)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_therapist_card, parent, false))

        override fun getItemCount() = list.size

        // 4. In onBindViewHolder(), load profile picture
        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val t = list[position]

            holder.tvName.text  = t.name
            holder.tvTitle.text = "${t.title} • ${t.experience}"
            holder.tvRate.text  = "₱${String.format("%,.0f", t.rate)}"

            val stars = Math.round(t.rating).toInt()
            holder.tvStars.text = (1..5).joinToString(" ") { if (it <= stars) "★" else "☆" } +
                    "  ${t.rating} (${t.reviews} reviews)"

            val cleanName = t.name.replace("Dr. ", "").trim().split(" ")
            val initials  = (if (cleanName.size > 1)
                "${cleanName.first().firstOrNull() ?: ""}${cleanName.last().firstOrNull() ?: ""}"
            else cleanName.first().take(2)).uppercase()

            if (t.profilePictureUrl.isNotBlank()) {
                holder.ivAvatar.visibility   = View.VISIBLE
                holder.tvInitials.visibility = View.GONE

                // Circular clip via outline — no Coil transformation needed
                holder.ivAvatar.clipToOutline  = true
                holder.ivAvatar.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                holder.ivAvatar.setBackgroundResource(R.drawable.bg_circle_white)

                holder.ivAvatar.load(t.profilePictureUrl) {
                    crossfade(true)
                    listener(
                        onError = { _, _ ->
                            holder.ivAvatar.visibility   = View.GONE
                            holder.tvInitials.visibility = View.VISIBLE
                            holder.tvInitials.text       = initials
                        }
                    )
                }
            } else {
                holder.ivAvatar.visibility   = View.GONE
                holder.tvInitials.visibility = View.VISIBLE
                holder.tvInitials.text       = initials
            }

            // Specialty tags
            holder.llTags.removeAllViews()
            t.specialties.take(3).forEach { spec ->
                holder.llTags.addView(TextView(baseContext).apply {
                    text = spec; textSize = 12f
                    setTextColor(Color.parseColor("#4A5047"))
                    setBackgroundResource(R.drawable.bg_card_bordered)
                    setPadding(24, 10, 24, 10)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 10, 0) }
                })
            }
            if (t.specialties.size > 3) {
                holder.llTags.addView(TextView(baseContext).apply {
                    text = "+${t.specialties.size - 3}"; textSize = 12f
                    setTextColor(Color.parseColor("#7A8077"))
                    setPadding(8, 10, 8, 10)
                })
            }

            // Status badges
            holder.llBadges.removeAllViews()
            if (t.available) holder.llBadges.addView(badge("✓ Accepting", "#F0FDF4", "#16A34A", bottom = 0))
            if (t.online)    holder.llBadges.addView(badge("Telehealth",  "#F5F3FF", "#7C3AED", bottom = 0))

            val clickListener = View.OnClickListener {
                startActivity(
                    Intent(this@FindTherapistActivity, TherapistProfileActivity::class.java).apply {
                        putExtra("THERAPIST_ID",          t.id)
                        putExtra("therapistName",         t.name)
                        putExtra("therapistTitle",        t.title)
                        putExtra("therapistExperience",   t.experience)
                        putExtra("therapistRate",         t.rate)
                        putExtra("therapistRating",       t.rating)
                        putExtra("therapistReviews",      t.reviews)
                        putExtra("therapistOnline",       t.online)
                        putExtra("therapistAvailable",    t.available)
                        putExtra("therapistSpecialties",  ArrayList(t.specialties))
                        putExtra("therapistProfilePicUrl",t.profilePictureUrl)
                    }
                )
            }
            holder.itemView.setOnClickListener(clickListener)
            holder.btnViewProfile.setOnClickListener(clickListener)
        }

        private fun badge(label: String, bg: String, fg: String, bottom: Int) =
            TextView(baseContext).apply {
                text = label; textSize = 10f
                setTextColor(Color.parseColor(fg))
                setBackgroundResource(R.drawable.bg_card_bordered)
                setPadding(16, 6, 16, 6)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = bottom; marginEnd = 8 }
            }
    }
}