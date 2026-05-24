package com.therapea.app.features.video

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
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

    private val apiBaseUrl = BuildConfig.BASE_URL.removeSuffix("api/").removeSuffix("/")
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
    private var channelName = "therapy-session-1"
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
    private lateinit var btnJoinCall: MaterialButton
    private lateinit var btnMic: MaterialButton
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnEndCall: MaterialButton

    private val rtcHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            android.util.Log.d("AGORA_DEBUG", "✅ Joined! uid=$uid channel=$channel")
            runOnUiThread {
                joined = true
                localUid = uid
                tvCallStatus.text = "Connected"
                tvWaiting.text = "Waiting for the other person to join..."
                tvWaiting.isVisible = true
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            android.util.Log.d("AGORA_DEBUG", "👤 Remote user joined uid=$uid")
            runOnUiThread {
                remoteUid = uid
                setupRemoteVideo(uid)
                tvWaiting.isVisible = false
                tvCallStatus.text = "In session"
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                if (remoteUid == uid) {
                    remoteUid = null
                    remoteVideoContainer.removeAllViews()
                    tvWaiting.text = "The other person left the session."
                    tvWaiting.isVisible = true
                    tvCallStatus.text = "Waiting"
                }
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            android.util.Log.d("AGORA_DEBUG", "🔌 Connection state=$state reason=$reason")
            // state: 1=Disconnected, 2=Connecting, 3=Connected, 4=Reconnecting, 5=Failed
            // reason: 8=InvalidToken, 9=TokenExpired, 10=RejectedByServer
            runOnUiThread {
                when (state) {
                    5 -> showDialog(
                        "Connection Failed",
                        "Could not connect to the session. Reason code: $reason. Please leave and try again."
                    )
                }
            }
        }

        override fun onError(err: Int) {
            android.util.Log.e("AGORA_DEBUG", "❌ Agora error: $err")
            runOnUiThread {
                showDialog("Video connection issue", "The call could not continue. Error code: $err")
            }
        }

        override fun onRequestToken() {
            runOnUiThread {
                showDialog("Session expired", "The video token expired. Please leave and rejoin.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_room)

        channelName = resolveChannelName()
        android.util.Log.d("AGORA_DEBUG", "📌 Initial channelName=$channelName")

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
        updateControls()
    }

    private fun setupListeners() {
        btnJoinCall.setOnClickListener { prepareToJoinCall() }
        btnMic.setOnClickListener { toggleMic() }
        btnCamera.setOnClickListener { toggleCamera() }
        btnEndCall.setOnClickListener { leaveCallAndFinish() }
    }

    private fun resolveChannelName(): String {
        val appointmentId = intent.getStringExtra("appointmentId")
        return intent.getStringExtra("channelName")
            ?: intent.getStringExtra("channel")
            ?: if (!appointmentId.isNullOrBlank()) "therapy-session-$appointmentId" else "therapy-session-1"
    }

    private fun fetchVideoToken() {
        tokenReady = false
        btnJoinCall.isEnabled = false
        btnJoinCall.text = "Connecting..."

        scope.launch {
            try {
                val encodedChannel = URLEncoder.encode(channelName, "UTF-8")
                val request = Request.Builder()
                    .url("$apiBaseUrl/api/video/token?channelName=$encodedChannel")
                    .get()
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val body = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }

                android.util.Log.d("AGORA_DEBUG", "HTTP status: ${response.code}")
                android.util.Log.d("AGORA_DEBUG", "Token server response: $body")

                if (!response.isSuccessful) throw IllegalStateException("Token request failed: HTTP ${response.code}")

                val json = JSONObject(body)
                token = json.optString("token", json.optString("rtcToken", "")).ifBlank { null }
                appId = json.optString("appId", "").ifBlank { fallbackAppId }
                localUid = json.optInt("uid", 0)

                // IMPORTANT: sync channelName from server response so token matches exactly
                val serverChannelName = json.optString("channelName", "").ifBlank { null }
                if (serverChannelName != null) {
                    android.util.Log.d("AGORA_DEBUG", "🔄 Syncing channelName from server: $serverChannelName (was: $channelName)")
                    channelName = serverChannelName
                }

                android.util.Log.d("AGORA_DEBUG", "Parsed → token=${token?.take(20)}... appId=$appId uid=$localUid channel=$channelName")

                if (token.isNullOrBlank() || appId.isBlank()) {
                    tvLobbyStatus.text = "Video service is not configured yet."
                    btnJoinCall.text = "Unavailable"
                    btnJoinCall.isEnabled = false
                    return@launch
                }

                tokenReady = true
                tvLobbyStatus.text = "Tap below to enter the session."
                btnJoinCall.text = "Join Call"
                btnJoinCall.isEnabled = true

            } catch (e: Exception) {
                android.util.Log.e("AGORA_DEBUG", "❌ fetchVideoToken failed: ${e.message}")
                appId = fallbackAppId
                token = null

                if (appId.isBlank()) {
                    tvLobbyStatus.text = "Could not connect to the video server."
                    btnJoinCall.text = "Retry"
                    btnJoinCall.isEnabled = true
                    btnJoinCall.setOnClickListener { fetchVideoToken() }
                } else {
                    tokenReady = true
                    tvLobbyStatus.text = "Token server unavailable. Joining with app fallback."
                    btnJoinCall.text = "Join Call"
                    btnJoinCall.isEnabled = true
                    btnJoinCall.setOnClickListener { prepareToJoinCall() }
                }
            }
        }
    }

    private fun prepareToJoinCall() {
        if (!tokenReady || appId.isBlank()) {
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
        android.util.Log.d("AGORA_DEBUG", "joinCall → appId='$appId' token=${token?.take(10)} channel=$channelName")

        scope.launch(Dispatchers.IO) {
            try {
                val engine = createRtcEngine()

                withContext(Dispatchers.Main) {
                    rtcEngine = engine
                    lobbyOverlay.isVisible = false
                    callLayer.isVisible = true
                    tvCallStatus.text = "Joining..."

                    setupLocalVideo()
                    rtcEngine?.setEnableSpeakerphone(true)

                    val mediaOptions = ChannelMediaOptions().apply {
                        channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                        publishCameraTrack = true
                        publishMicrophoneTrack = true
                        autoSubscribeAudio = true
                        autoSubscribeVideo = true
                    }

                    android.util.Log.d("AGORA_DEBUG", "🚀 joinChannel → token=${token?.take(20)}... channel=$channelName uid=$localUid appId=$appId")
                    rtcEngine?.joinChannel(token, channelName, localUid, mediaOptions)
                    rtcEngine?.startPreview()
                }
            } catch (e: Exception) {
                android.util.Log.e("AGORA_DEBUG", "❌ Exception type: ${e::class.java.name}")
                android.util.Log.e("AGORA_DEBUG", "❌ Message: ${e.message}")
                android.util.Log.e("AGORA_DEBUG", "❌ Cause: ${e.cause}")
                withContext(Dispatchers.Main) {
                    callLayer.isVisible = false
                    lobbyOverlay.isVisible = true
                    showDialog(
                        title = "Could not start video",
                        message = "Error: ${e.message}"
                    )
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

        val textureView = android.view.TextureView(baseContext)

        localVideoContainer.addView(
            textureView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        rtcEngine?.setupLocalVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, localUid))
        tvLocalCameraOff.visibility = View.GONE
    }

    private fun setupRemoteVideo(uid: Int) {
        remoteVideoContainer.removeAllViews()

        val textureView = android.view.TextureView(baseContext)

        remoteVideoContainer.addView(
            textureView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        rtcEngine?.setupRemoteVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
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
        updateControls()
    }

    private fun updateControls() {
        btnMic.text = if (micOn) "Mic On" else "Muted"
        btnCamera.text = if (camOn) "Cam On" else "Cam Off"

        btnMic.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (micOn) "#374151" else "#EF4444"))
        btnCamera.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (camOn) "#374151" else "#EF4444"))
        btnEndCall.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
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
            rtcEngine?.let { RtcEngine.destroy() }
            rtcEngine = null
            joined = false
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
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                joinCall()
            } else {
                showDialog(
                    title = "Hardware Blocked",
                    message = "Camera and microphone access are required for video sessions.",
                    positiveText = "Try Again",
                    onPositive = { requestPermissions(requiredPermissions, permissionRequestCode) }
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