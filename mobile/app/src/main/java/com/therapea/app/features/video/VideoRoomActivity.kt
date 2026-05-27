package com.therapea.app.features.video

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.therapea.app.BuildConfig
import com.therapea.app.R
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.RemoteVideoStats
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class VideoRoomActivity : Activity() {

    private val showDebugOverlay = false

    private val apiBaseUrl = BuildConfig.BASE_URL
        .removeSuffix("/api/")
        .removeSuffix("/api")
        .trimEnd('/') + "/"

    private val fallbackAppId = BuildConfig.AGORA_APP_ID
    private val httpClient = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissionRequestCode = 4702
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var rtcEngine: RtcEngine? = null

    private var token: String? = null
    private var appId = ""
    private var appointmentId = "default"
    private var channelName = "therapy-session-default"

    private var localUid = 0
    private var remoteUid: Int? = null

    private var tokenReady = false
    private var joined = false
    private var leaving = false

    private var micOn = true
    private var camOn = true

    private lateinit var lobbyOverlay: View
    private lateinit var callLayer: View

    private lateinit var remoteVideoContainer: FrameLayout
    private lateinit var localVideoContainer: FrameLayout

    private lateinit var tvWaiting: TextView
    private lateinit var tvLocalCameraOff: TextView
    private lateinit var tvLobbyStatus: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var tvDebug: TextView

    private lateinit var btnJoinCall: MaterialButton
    private lateinit var btnMic: MaterialButton
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnEndCall: MaterialButton

    private fun debug(msg: String) {
        android.util.Log.d("AGORA_DEBUG", msg)

        if (!showDebugOverlay) return

        runOnUiThread {
            tvDebug.text = msg
            tvDebug.visibility = View.VISIBLE
        }
    }

    private val rtcHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            debug("JOINED channel=$channel uid=$uid")

            runOnUiThread {
                joined = true
                tvCallStatus.text = "Connected"
                tvWaiting.text = "Waiting for the other person to join..."
                tvWaiting.isVisible = true

                rtcEngine?.muteAllRemoteVideoStreams(false)
                rtcEngine?.muteAllRemoteAudioStreams(false)

                bringCallOverlaysToFront()
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            debug("USER JOINED uid=$uid")

            runOnUiThread {
                rtcEngine?.muteRemoteVideoStream(uid, false)
                rtcEngine?.muteRemoteAudioStream(uid, false)
                setupRemoteVideo(uid)
            }
        }

        override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) {
            debug("FIRST FRAME uid=$uid ${width}x${height}")

            runOnUiThread {
                setupRemoteVideo(uid)
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            debug("REMOTE STATE uid=$uid state=$state reason=$reason")

            runOnUiThread {
                when (state) {
                    Constants.REMOTE_VIDEO_STATE_DECODING -> setupRemoteVideo(uid)
                    Constants.REMOTE_VIDEO_STATE_STARTING -> setupRemoteVideo(uid)
                    Constants.REMOTE_VIDEO_STATE_FROZEN -> debug("REMOTE FROZEN uid=$uid reason=$reason")
                    Constants.REMOTE_VIDEO_STATE_FAILED -> debug("REMOTE FAILED uid=$uid reason=$reason")
                }
            }
        }

        override fun onRemoteVideoStats(stats: RemoteVideoStats?) {
            stats?.let {
                debug("STATS uid=${it.uid} bitrate=${it.receivedBitrate} ${it.width}x${it.height}")
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            debug("USER OFFLINE uid=$uid reason=$reason")

            runOnUiThread {
                if (remoteUid == uid) {
                    remoteUid = null
                    remoteVideoContainer.removeAllViews()
                    tvWaiting.text = "The other person left the session."
                    tvWaiting.isVisible = true
                    tvCallStatus.text = "Waiting"
                    bringCallOverlaysToFront()
                }
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            debug("CONNECTION state=$state reason=$reason")

            runOnUiThread {
                if (state == Constants.CONNECTION_STATE_FAILED) {
                    showDialog("Connection Failed", "Could not connect. Reason: $reason")
                }
            }
        }

        override fun onError(err: Int) {
            debug("ERROR err=$err")
        }

        override fun onRequestToken() {
            debug("TOKEN EXPIRED")

            runOnUiThread {
                showDialog("Session expired", "The video token expired. Please leave and rejoin.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_room)

        channelName = resolveChannelName()

        bindViews()
        setupInitialUi()
        setupListeners()
        fetchVideoToken()
    }

    private fun bindViews() {
        lobbyOverlay = findViewById(R.id.lobbyOverlay)
        callLayer = findViewById(R.id.callLayer)

        remoteVideoContainer = findViewById(R.id.remoteVideoContainer)
        localVideoContainer = findViewById(R.id.localVideoContainer)

        tvWaiting = findViewById(R.id.tvWaiting)
        tvLocalCameraOff = findViewById(R.id.tvLocalCameraOff)
        tvLobbyStatus = findViewById(R.id.tvLobbyStatus)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvDebug = findViewById(R.id.tvDebug)

        btnJoinCall = findViewById(R.id.btnJoinCall)
        btnMic = findViewById(R.id.btnMic)
        btnCamera = findViewById(R.id.btnCamera)
        btnEndCall = findViewById(R.id.btnEndCall)
    }

    private fun setupInitialUi() {
        callLayer.isVisible = false
        lobbyOverlay.isVisible = true

        btnJoinCall.isEnabled = false
        btnJoinCall.text = "Connecting..."

        tvLobbyStatus.text = "Preparing the secure therapy room..."
        tvWaiting.text = "Waiting for the other person to join..."
        tvDebug.visibility = View.GONE

        updateControls()
    }

    private fun setupListeners() {
        btnJoinCall.setOnClickListener { prepareToJoinCall() }
        btnMic.setOnClickListener { toggleMic() }
        btnCamera.setOnClickListener { toggleCamera() }
        btnEndCall.setOnClickListener { leaveCallAndFinish() }
    }

    private fun resolveChannelName(): String {
        appointmentId = intent.getStringExtra("appointmentId")
            ?: intent.getStringExtra("id")
                    ?: "default"

        return "therapy-session-$appointmentId"
    }

    private fun stableMobileUid(): Int {
        val prefs = getSharedPreferences("TheraPeaSession", MODE_PRIVATE)

        val savedUid = prefs.getInt("agora_mobile_uid", -1)
        if (savedUid in 40000..63999) {
            return savedUid
        }

        val userData = prefs.getString("user_data", "") ?: ""

        val androidId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""

        val seed = userData.ifBlank { androidId }.ifBlank { "therapea-mobile" }
        val bucket = Math.floorMod(("android:$seed").hashCode(), 24000)

        val uid = 40000 + bucket

        prefs.edit()
            .putInt("agora_mobile_uid", uid)
            .apply()

        return uid
    }

    private fun fetchVideoToken() {
        tokenReady = false
        token = null

        btnJoinCall.isEnabled = false
        btnJoinCall.text = "Connecting..."
        tvLobbyStatus.text = "Preparing the secure therapy room..."

        scope.launch {
            try {
                localUid = stableMobileUid()

                val encodedAppointmentId = URLEncoder.encode(appointmentId, "UTF-8")

                val request = Request.Builder()
                    .url("${apiBaseUrl}api/video/token?appointmentId=$encodedAppointmentId&uid=$localUid")
                    .get()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }

                val body = withContext(Dispatchers.IO) {
                    response.body?.string().orEmpty()
                }

                android.util.Log.d("AGORA_DEBUG", "Token HTTP status=${response.code}")
                android.util.Log.d("AGORA_DEBUG", "Token response=$body")

                if (!response.isSuccessful) {
                    throw IllegalStateException("Token request failed: HTTP ${response.code}")
                }

                val json = JSONObject(body)

                token = json.optString("token", json.optString("rtcToken", ""))
                    .ifBlank { null }

                appId = json.optString("appId", "")
                    .ifBlank { fallbackAppId }

                localUid = json.optInt("uid", localUid)

                val serverChannelName = json.optString("channelName", "")
                    .ifBlank { null }

                if (serverChannelName != null) {
                    channelName = serverChannelName
                }

                android.util.Log.d(
                    "AGORA_DEBUG",
                    "Parsed token uid=$localUid channel=$channelName appId=$appId"
                )

                if (token.isNullOrBlank() || appId.isBlank()) {
                    tokenReady = false
                    tvLobbyStatus.text = "Video service is not configured yet."
                    btnJoinCall.text = "Unavailable"
                    btnJoinCall.isEnabled = false
                    return@launch
                }

                tokenReady = true
                tvLobbyStatus.text = "Tap below to enter the session."
                btnJoinCall.text = "Join Call"
                btnJoinCall.isEnabled = true
                btnJoinCall.setOnClickListener { prepareToJoinCall() }

            } catch (e: Exception) {
                android.util.Log.e("AGORA_DEBUG", "fetchVideoToken failed: ${e.message}")

                tokenReady = false
                token = null

                tvLobbyStatus.text = "Could not connect to the video server."
                btnJoinCall.text = "Retry"
                btnJoinCall.isEnabled = true
                btnJoinCall.setOnClickListener { fetchVideoToken() }
            }
        }
    }

    private fun prepareToJoinCall() {
        if (!tokenReady || token.isNullOrBlank() || appId.isBlank()) {
            showDialog("Room not ready", "The video room is still connecting. Please try again.")
            return
        }

        if (!hasRequiredPermissions()) {
            requestPermissions(requiredPermissions, permissionRequestCode)
            return
        }

        joinCall()
    }

    private fun joinCall() {
        if (joined || leaving) return

        android.util.Log.d("AGORA_DEBUG", "joinCall appId=$appId uid=$localUid channel=$channelName")

        scope.launch(Dispatchers.IO) {
            try {
                val engine = createRtcEngine()

                withContext(Dispatchers.Main) {
                    rtcEngine = engine

                    lobbyOverlay.isVisible = false
                    callLayer.isVisible = true
                    tvCallStatus.text = "Joining..."
                    tvDebug.visibility = View.GONE

                    rtcEngine?.setEnableSpeakerphone(true)
                    rtcEngine?.enableVideo()
                    rtcEngine?.enableAudio()
                    rtcEngine?.muteAllRemoteVideoStreams(false)
                    rtcEngine?.muteAllRemoteAudioStreams(false)

                    setupLocalVideo()

                    val mediaOptions = ChannelMediaOptions().apply {
                        channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                        clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                        publishCameraTrack = true
                        publishMicrophoneTrack = true
                        autoSubscribeAudio = true
                        autoSubscribeVideo = true
                    }

                    rtcEngine?.startPreview()
                    rtcEngine?.joinChannel(token, channelName, localUid, mediaOptions)

                    bringCallOverlaysToFront()

                    android.util.Log.d(
                        "AGORA_DEBUG",
                        "joinChannel called uid=$localUid channel=$channelName"
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("AGORA_DEBUG", "joinCall failed: ${e.message}")

                withContext(Dispatchers.Main) {
                    callLayer.isVisible = false
                    lobbyOverlay.isVisible = true
                    showDialog("Could not start video", "Error: ${e.message}")
                }
            }
        }
    }

    private fun createRtcEngine(): RtcEngine {
        return RtcEngine.create(applicationContext, appId, rtcHandler).apply {
            enableVideo()
            enableAudio()
        }
    }

    private fun setupLocalVideo() {
        localVideoContainer.removeAllViews()

        val surfaceView = RtcEngine.CreateRendererView(this@VideoRoomActivity)

        // Local preview must appear above the remote full-screen SurfaceView.
        surfaceView.setZOrderMediaOverlay(true)

        localVideoContainer.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        rtcEngine?.setupLocalVideo(
            VideoCanvas(
                surfaceView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                localUid
            )
        )

        localVideoContainer.visibility = View.VISIBLE
        tvLocalCameraOff.visibility = View.GONE

        bringCallOverlaysToFront()
    }

    private fun setupRemoteVideo(uid: Int) {
        if (remoteUid == uid && remoteVideoContainer.childCount > 0) {
            debug("GUARD duplicate setupRemoteVideo uid=$uid")

            runOnUiThread {
                tvWaiting.isVisible = false
                tvCallStatus.text = "In session"
                bringCallOverlaysToFront()
            }

            return
        }

        runOnUiThread {
            debug("CREATING remote SurfaceView uid=$uid")

            remoteVideoContainer.removeAllViews()

            val surfaceView = RtcEngine.CreateRendererView(this@VideoRoomActivity)
            surfaceView.setZOrderMediaOverlay(false)

            remoteVideoContainer.addView(
                surfaceView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            rtcEngine?.setupRemoteVideo(
                VideoCanvas(
                    surfaceView,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    uid
                )
            )

            rtcEngine?.muteRemoteVideoStream(uid, false)
            rtcEngine?.muteRemoteAudioStream(uid, false)

            remoteUid = uid
            tvWaiting.isVisible = false
            tvCallStatus.text = "In session"

            bringCallOverlaysToFront()

            debug("setupRemoteVideo DONE uid=$uid")
        }
    }

    private fun bringCallOverlaysToFront() {
        findViewById<View>(R.id.localPreviewCard).bringToFront()
        findViewById<View>(R.id.callControls).bringToFront()
        tvCallStatus.bringToFront()

        if (showDebugOverlay) {
            tvDebug.bringToFront()
        }
    }

    private fun toggleMic() {
        micOn = !micOn
        rtcEngine?.muteLocalAudioStream(!micOn)
        updateControls()
    }

    private fun toggleCamera() {
        camOn = !camOn

        rtcEngine?.muteLocalVideoStream(!camOn)
        localVideoContainer.isVisible = camOn
        tvLocalCameraOff.isVisible = !camOn

        if (camOn) {
            setupLocalVideo()
        } else {
            localVideoContainer.removeAllViews()
        }

        bringCallOverlaysToFront()
        updateControls()
    }

    private fun updateControls() {
        btnMic.text = if (micOn) "Mic On" else "Muted"
        btnCamera.text = if (camOn) "Cam On" else "Cam Off"

        btnMic.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(if (micOn) "#374151" else "#EF4444"))

        btnCamera.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(if (camOn) "#374151" else "#EF4444"))

        btnEndCall.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#EF4444"))
    }

    private fun leaveCallAndFinish() {
        if (leaving) return

        leaving = true
        leaveCall()
        finish()
    }

    private fun leaveCall() {
        try {
            rtcEngine?.stopPreview()
            rtcEngine?.leaveChannel()

            localVideoContainer.removeAllViews()
            remoteVideoContainer.removeAllViews()

            rtcEngine?.let {
                RtcEngine.destroy()
            }

            rtcEngine = null
            joined = false
            remoteUid = null

        } catch (_: Exception) {
            rtcEngine = null
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == permissionRequestCode) {
            val granted =
                grantResults.isNotEmpty() &&
                        grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (granted) {
                joinCall()
            } else {
                showDialog(
                    title = "Hardware Blocked",
                    message = "Camera and microphone access are required for video sessions.",
                    positiveText = "Try Again",
                    onPositive = {
                        requestPermissions(requiredPermissions, permissionRequestCode)
                    }
                )
            }
        }
    }

    private fun showDialog(
        title: String,
        message: String,
        positiveText: String = "Okay",
        onPositive: (() -> Unit)? = null
    ) {
        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                dialog.dismiss()
                onPositive?.invoke()
            }
            .show()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Leave video session?")
            .setMessage("Your camera and microphone will be disconnected.")
            .setPositiveButton("Leave") { _, _ -> leaveCallAndFinish() }
            .setNegativeButton("Stay", null)
            .show()
    }

    override fun onDestroy() {
        leaveCall()
        scope.cancel()
        super.onDestroy()
    }
}