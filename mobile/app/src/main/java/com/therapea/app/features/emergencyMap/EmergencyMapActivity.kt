// app/src/main/java/com/therapea/app/features/emergencyMap/EmergencyMapActivity.kt
package com.therapea.app.features.emergencyMap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.therapea.app.R
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

data class EmergencyFacility(
    val id: Int,
    val name: String,
    val address: String,
    val type: String,
    val available: Boolean,
    val phone: String,
    val hours: String,
    val lat: Double,
    val lng: Double
)

data class RouteStats(
    val dist: String,
    val drive: String,
    val walk: String,
    val transit: String
)

class EmergencyMapActivity : Activity() {

    private val apiBaseUrl = "http://10.0.2.2:8083"
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var webView: WebView
    private lateinit var rvFacilities: RecyclerView
    private lateinit var adapter: FacilityAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private lateinit var etSearch: EditText
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnLocateMe: MaterialButton

    private val allFacilities = mutableListOf<EmergencyFacility>()
    private var filteredFacilities = listOf<EmergencyFacility>()

    private var selectedId: Int? = null
    private var routingId: Int? = null
    private var searchQuery = ""

    private var userLat = 10.3157
    private var userLng = 123.8854
    private var userAddress = "Cebu City (Default Location)"
    private var mapReady = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_map)

        bindViews()
        setupWebView()
        setupUi()
        loadDefaultFacilities()
        fetchFacilitiesFromBackend()
        requestLocationPermission()
    }

    private fun bindViews() {
        webView = findViewById(R.id.webViewMap)
        rvFacilities = findViewById(R.id.rvFacilities)
        etSearch = findViewById(R.id.etSearch)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        tvEmpty = findViewById(R.id.tvEmptyFacilities)
        btnBack = findViewById(R.id.btnBack)
        btnLocateMe = findViewById(R.id.btnLocateMe)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.addJavascriptInterface(MapBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mapReady = true
                syncMapMarkers()
                syncUserMarker()
            }
        }

        webView.loadDataWithBaseURL(
            "https://leaflet.local/",
            buildLeafletHtml(),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun setupUi() {
        btnBack.setOnClickListener { finish() }

        btnLocateMe.setOnClickListener {
            clearSelection()
            js("flyToUser()")
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        val sheet = findViewById<View>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(sheet)
        bottomSheetBehavior.peekHeight = dp(330)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        adapter = FacilityAdapter(
            onCardClick = { facility -> handleFacilitySelected(facility) },
            onDirectionsClick = { facility -> handleGetDirections(facility) }
        )

        rvFacilities.layoutManager = LinearLayoutManager(this)
        rvFacilities.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                renderFacilities()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadDefaultFacilities() {
        allFacilities.clear()
        allFacilities.addAll(
            listOf(
                EmergencyFacility(
                    1,
                    "Vicente Sotto Memorial Medical Center (VSMMC)",
                    "B. Rodriguez St, Cebu City",
                    "Psychiatric Hospital",
                    true,
                    "(032) 253 9891",
                    "24/7",
                    10.3090,
                    123.8932
                ),
                EmergencyFacility(
                    2,
                    "Chong Hua Hospital - Psychiatry Dept.",
                    "Don Mariano Cui St, Cebu City",
                    "Psychiatric Unit",
                    true,
                    "(032) 255 8000",
                    "24/7",
                    10.3087,
                    123.8913
                ),
                EmergencyFacility(
                    3,
                    "Cebu Doctors University Hospital",
                    "Osmena Blvd, Cebu City",
                    "Mental Health Ward",
                    true,
                    "(032) 255 5555",
                    "24/7",
                    10.3129,
                    123.8934
                ),
                EmergencyFacility(
                    4,
                    "Gestalt Wellness Institute",
                    "Taft Business Center, Gorordo Ave",
                    "Outpatient Clinic",
                    false,
                    "0915 540 5005",
                    "9AM-5PM",
                    10.3150,
                    123.8975
                )
            )
        )
        renderFacilities()
    }

    private fun fetchFacilitiesFromBackend() {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$apiBaseUrl/api/emergency/facilities")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@launch

                val parsed = JSONTokener(body).nextValue()
                val array = when (parsed) {
                    is JSONArray -> parsed
                    is JSONObject -> parsed.optJSONArray("facilities")
                        ?: parsed.optJSONArray("data")
                    else -> null
                } ?: return@launch

                val remoteFacilities = mutableListOf<EmergencyFacility>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    remoteFacilities.add(
                        EmergencyFacility(
                            id = item.optInt("id", i + 1),
                            name = item.optString("name"),
                            address = item.optString("address"),
                            type = item.optString("type", "Mental Health Facility"),
                            available = item.optBoolean("available", true),
                            phone = item.optString("phone", "Not listed"),
                            hours = item.optString("hours", "24/7"),
                            lat = item.optDouble("lat"),
                            lng = item.optDouble("lng")
                        )
                    )
                }

                if (remoteFacilities.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        allFacilities.clear()
                        allFacilities.addAll(remoteFacilities)
                        selectedId = null
                        adapter.clearRouteData()
                        renderFacilities()
                    }
                }
            } catch (_: Exception) {
                // Static Cebu facilities remain available, matching the web fallback behavior.
            }
        }
    }

    private fun renderFacilities() {
        val query = searchQuery.trim().lowercase()

        filteredFacilities = allFacilities.filter {
            query.isBlank() ||
                    it.name.lowercase().contains(query) ||
                    it.address.lowercase().contains(query) ||
                    it.type.lowercase().contains(query)
        }

        if (selectedId != null && filteredFacilities.none { it.id == selectedId }) {
            selectedId = null
            adapter.clearRouteData()
            js("clearRoute()")
        }

        tvEmpty.isVisible = filteredFacilities.isEmpty()
        adapter.submitList(filteredFacilities)
        adapter.setSelected(selectedId)
        syncMapMarkers()
    }

    private fun handleFacilitySelected(facility: EmergencyFacility) {
        if (selectedId == facility.id) {
            clearSelection()
            return
        }

        selectedId = facility.id
        routingId = null
        adapter.clearRouteData()
        adapter.setSelected(selectedId)
        js("clearRoute()")
        js("selectFacility(${facility.id}, true)")
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun clearSelection() {
        selectedId = null
        routingId = null
        adapter.clearRouteData()
        adapter.setSelected(null)
        js("clearRoute()")
        js("clearSelectedMarker()")
    }

    private fun handleGetDirections(facility: EmergencyFacility) {
        selectedId = facility.id
        routingId = facility.id
        adapter.setSelected(facility.id)
        adapter.setRouting(facility.id)
        js("selectFacility(${facility.id}, false)")
        fetchRoute(facility)
    }

    private fun fetchRoute(facility: EmergencyFacility) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "https://router.project-osrm.org/route/v1/driving/" +
                        "$userLng,$userLat;${facility.lng},${facility.lat}" +
                        "?overview=full&geometries=geojson"

                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string().orEmpty()
                val route = JSONObject(body).optJSONArray("routes")?.optJSONObject(0)
                    ?: throw IllegalStateException("No route")

                val distance = route.optDouble("distance", 0.0)
                val duration = route.optDouble("duration", 0.0)
                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")

                val coordsJs = buildString {
                    append("[")
                    for (i in 0 until coords.length()) {
                        val point = coords.getJSONArray(i)
                        if (i > 0) append(",")
                        append("[${point.getDouble(1)},${point.getDouble(0)}]")
                    }
                    append("]")
                }

                val distKm = String.format(Locale.US, "%.1f km", distance / 1000)
                val driveMins = maxOf(1, (duration / 60).toInt())
                val walkMins = maxOf(1, (distance / 80).toInt())
                val transitMins = (driveMins * 1.5 + 10).toInt()

                val stats = RouteStats(
                    dist = distKm,
                    drive = "$driveMins min",
                    walk = "$walkMins min",
                    transit = "$transitMins min"
                )

                withContext(Dispatchers.Main) {
                    routingId = null
                    adapter.setRouting(null)
                    adapter.updateStats(facility.id, stats)
                    js("drawRoute($coordsJs)")
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    routingId = null
                    adapter.setRouting(null)
                    adapter.updateMessage(facility.id, "Route unavailable. Please try again.")
                }
            }
        }
    }

    private fun requestLocationPermission() {
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            fetchLocation()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locations = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).mapNotNull { provider ->
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }

            val bestLocation = locations.maxByOrNull { it.time }

            if (bestLocation != null) {
                updateUserLocation(bestLocation)
            } else {
                useDefaultLocation()
            }
        } catch (_: Exception) {
            useDefaultLocation()
        }
    }

    private fun updateUserLocation(location: Location) {
        userLat = location.latitude
        userLng = location.longitude
        userAddress = "Current Location"
        tvLocationStatus.text = "Locating your address..."
        syncUserMarker()
        reverseGeocode(userLat, userLng)
    }

    private fun useDefaultLocation() {
        userLat = 10.3157
        userLng = 123.8854
        userAddress = "Cebu City (Default Location)"
        tvLocationStatus.text = userAddress
        syncUserMarker()
        showDialog(
            "Location unavailable",
            "Using Cebu City as your default location. You can still view facilities and routes."
        )
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse" +
                        "?format=json&lat=$lat&lon=$lng&zoom=16"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TheraPea-Mobile")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                val displayName = JSONObject(body).optString("display_name", "Current Location")
                val shortAddress = displayName.split(",").take(3).joinToString(",")

                withContext(Dispatchers.Main) {
                    userAddress = shortAddress
                    tvLocationStatus.text = "You are near: $shortAddress"
                    syncUserMarker()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    userAddress = "Current Location"
                    tvLocationStatus.text = userAddress
                    syncUserMarker()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) fetchLocation() else useDefaultLocation()
        }
    }

    private fun syncMapMarkers() {
        if (!mapReady) return

        val array = JSONArray()
        filteredFacilities.forEach { facility ->
            array.put(
                JSONObject().apply {
                    put("id", facility.id)
                    put("name", facility.name)
                    put("address", facility.address)
                    put("type", facility.type)
                    put("available", facility.available)
                    put("phone", facility.phone)
                    put("hours", facility.hours)
                    put("lat", facility.lat)
                    put("lng", facility.lng)
                }
            )
        }

        js("setFacilities($array)")
        selectedId?.let { js("selectFacility($it, false)") }
    }

    private fun syncUserMarker() {
        if (!mapReady) return
        js("setUserMarker($userLat, $userLng, ${JSONObject.quote(userAddress)})")
    }

    private fun js(script: String) {
        runOnUiThread {
            if (mapReady) webView.evaluateJavascript(script, null)
        }
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Okay", null)
            .show()
    }

    private fun buildLeafletHtml(): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  * { box-sizing: border-box; }
  html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; }
  body { background: #ECEEE8; }
  .leaflet-container { font-family: system-ui, -apple-system, BlinkMacSystemFont, sans-serif; }
  .leaflet-control-attribution { font-size: 9px; }
  .popup-title { font-family: Georgia, serif; color: #0f172a; font-size: 15px; font-weight: 700; margin-bottom: 5px; }
  .popup-muted { color: #64748b; font-size: 13px; margin-bottom: 7px; line-height: 1.35; }
  .popup-row { color: #1e293b; font-size: 13px; font-weight: 600; margin-bottom: 5px; }
  .popup-btn { width: 100%; margin-top: 8px; border: 0; border-radius: 8px; background: #0A5C36; color: #fff; padding: 9px; font-weight: 700; }
  .user-popup { text-align: center; }
  .user-popup strong { display: block; color: #0f172a; margin-bottom: 4px; }
  .user-popup span { color: #64748b; font-size: 13px; }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', { zoomControl: true }).setView([10.3157, 123.8854], 15);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '© OpenStreetMap contributors',
  maxZoom: 19
}).addTo(map);

var hospitalIcon = new L.Icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

var userIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

var markers = {};
var userMarker = null;
var routeLayer = null;
var userLatLng = [10.3157, 123.8854];

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function popupHtml(f) {
  return ''
    + '<div>'
    + '<div class="popup-title">' + escapeHtml(f.name) + '</div>'
    + '<div class="popup-muted">' + escapeHtml(f.address) + '</div>'
    + '<div class="popup-row">Phone: ' + escapeHtml(f.phone) + '</div>'
    + '<div class="popup-muted">Hours: ' + escapeHtml(f.hours) + '</div>'
    + '<button class="popup-btn" onclick="Android.onDirectionsClick(' + f.id + ')">Get Directions</button>'
    + '</div>';
}

function setFacilities(items) {
  Object.keys(markers).forEach(function(id) {
    map.removeLayer(markers[id]);
  });
  markers = {};

  items.forEach(function(f) {
    var marker = L.marker([f.lat, f.lng], { icon: hospitalIcon }).addTo(map);
    marker.bindPopup(popupHtml(f), { minWidth: 245 });
    marker.on('click', function() {
      Android.onMarkerClick(f.id);
    });
    markers[f.id] = marker;
  });
}

function setUserMarker(lat, lng, address) {
  userLatLng = [lat, lng];

  if (userMarker) map.removeLayer(userMarker);

  userMarker = L.marker(userLatLng, { icon: userIcon }).addTo(map);
  userMarker.bindPopup(
    '<div class="user-popup"><strong>You are here at:</strong><span>' + escapeHtml(address) + '</span></div>'
  );
}

function selectFacility(id, moveCamera) {
  var marker = markers[id];
  if (!marker) return;

  marker.openPopup();

  if (moveCamera) {
    map.flyTo(marker.getLatLng(), 16, { animate: true, duration: 1.0 });
  }
}

function clearSelectedMarker() {
  map.closePopup();
}

function clearRoute() {
  if (routeLayer) {
    map.removeLayer(routeLayer);
    routeLayer = null;
  }
}

function drawRoute(coords) {
  clearRoute();

  routeLayer = L.polyline(coords, {
    color: '#0A5C36',
    weight: 5,
    opacity: 0.85,
    lineJoin: 'round'
  }).addTo(map);

  map.fitBounds(routeLayer.getBounds(), {
    padding: [50, 50],
    maxZoom: 16
  });
}

function flyToUser() {
  map.flyTo(userLatLng, 15, { animate: true, duration: 1.0 });
  if (userMarker) userMarker.openPopup();
}
</script>
</body>
</html>
""".trimIndent()

    inner class MapBridge {
        @JavascriptInterface
        fun onMarkerClick(id: Int) {
            val facility = allFacilities.find { it.id == id } ?: return
            runOnUiThread { handleFacilitySelected(facility) }
        }

        @JavascriptInterface
        fun onDirectionsClick(id: Int) {
            val facility = allFacilities.find { it.id == id } ?: return
            runOnUiThread { handleGetDirections(facility) }
        }
    }

    inner class FacilityAdapter(
        private val onCardClick: (EmergencyFacility) -> Unit,
        private val onDirectionsClick: (EmergencyFacility) -> Unit
    ) : RecyclerView.Adapter<FacilityAdapter.VH>() {

        private val items = mutableListOf<EmergencyFacility>()
        private var selectedFacilityId: Int? = null
        private var routingFacilityId: Int? = null
        private val statsMap = mutableMapOf<Int, RouteStats>()
        private val messageMap = mutableMapOf<Int, String>()

        fun submitList(next: List<EmergencyFacility>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        fun setSelected(id: Int?) {
            selectedFacilityId = id
            notifyDataSetChanged()
        }

        fun setRouting(id: Int?) {
            routingFacilityId = id
            notifyDataSetChanged()
        }

        fun updateStats(id: Int, stats: RouteStats) {
            statsMap[id] = stats
            messageMap.remove(id)
            notifyDataSetChanged()
        }

        fun updateMessage(id: Int, message: String) {
            messageMap[id] = message
            notifyDataSetChanged()
        }

        fun clearRouteData() {
            statsMap.clear()
            messageMap.clear()
            routingFacilityId = null
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val root: View = view
            val accent: View = view.findViewById(R.id.viewAccent)
            val name: TextView = view.findViewById(R.id.tvFacName)
            val address: TextView = view.findViewById(R.id.tvFacAddress)
            val type: TextView = view.findViewById(R.id.tvFacType)
            val availability: TextView = view.findViewById(R.id.tvFacAvailability)
            val details: LinearLayout = view.findViewById(R.id.llFacDetails)
            val phone: TextView = view.findViewById(R.id.tvFacPhone)
            val hours: TextView = view.findViewById(R.id.tvFacHours)
            val routingText: TextView = view.findViewById(R.id.tvRoutingText)
            val routeStats: LinearLayout = view.findViewById(R.id.llRouteStats)
            val distance: TextView = view.findViewById(R.id.tvRouteDistance)
            val drive: TextView = view.findViewById(R.id.tvDriveTime)
            val transit: TextView = view.findViewById(R.id.tvTransitTime)
            val walk: TextView = view.findViewById(R.id.tvWalkTime)
            val directions: MaterialButton = view.findViewById(R.id.btnGetDirections)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_facility, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val facility = items[position]
            val selected = selectedFacilityId == facility.id
            val stats = statsMap[facility.id]
            val message = messageMap[facility.id]
            val isRouting = routingFacilityId == facility.id

            holder.root.setBackgroundResource(
                if (selected) R.drawable.em_bg_facility_selected else R.drawable.em_bg_facility_item
            )
            holder.accent.isVisible = selected

            holder.name.text = facility.name
            holder.address.text = facility.address
            holder.type.text = facility.type
            holder.availability.text = if (facility.available) "● Open 24/7" else "● Limited hours"
            holder.availability.setTextColor(
                Color.parseColor(if (facility.available) "#065F46" else "#991B1B")
            )
            holder.availability.setBackgroundResource(
                if (facility.available) R.drawable.em_bg_badge_open else R.drawable.em_bg_badge_limited
            )

            holder.details.isVisible = selected
            holder.phone.text = facility.phone
            holder.hours.text = "Hours: ${facility.hours}"

            holder.routingText.isVisible = selected && (isRouting || message != null)
            holder.routingText.text = when {
                isRouting -> "Calculating fastest route..."
                message != null -> message
                else -> ""
            }

            holder.routeStats.isVisible = selected && stats != null && !isRouting
            holder.directions.isVisible = selected && stats == null && !isRouting

            if (stats != null) {
                holder.distance.text = "${stats.dist} away"
                holder.drive.text = stats.drive
                holder.transit.text = stats.transit
                holder.walk.text = stats.walk
            }

            holder.root.setOnClickListener { onCardClick(facility) }
            holder.directions.setOnClickListener { onDirectionsClick(facility) }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        webView.destroy()
        scope.cancel()
        super.onDestroy()
    }
}