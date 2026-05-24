import { useState, useEffect, useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  AgoraRTCProvider,
  useLocalCameraTrack,
  useLocalMicrophoneTrack,
  useJoin,
  usePublish,
  useRemoteUsers,
  RemoteUser,
  LocalVideoTrack,
  useRTCClient,        // ← use this instead of passing client manually
} from "agora-rtc-react";
import AgoraRTC from "agora-rtc-sdk-ng";
import "./VideoRoom.css";

const APP_ID      = import.meta.env.VITE_AGORA_APP_ID as string ?? "";
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string ?? "http://localhost:8083";

function useChannelName(): string {
  const { appointmentId: paramId } = useParams<{ appointmentId?: string }>();
  const [searchParams]             = useSearchParams();
  const queryId                    = searchParams.get("appointmentId");
  const id                         = paramId ?? queryId ?? "";
  return id ? `therapy-session-${id}` : "therapy-session-default";
}

// ── Root ──────────────────────────────────────────────────────────────────────
export default function VideoRoom() {
  // useMemo so the client is not recreated on every render.
  // Do NOT annotate the type — let it be inferred so both packages agree.
  const client = useMemo(
    () => AgoraRTC.createClient({ codec: "vp8", mode: "rtc" }),
    []
  );

  return (
    <AgoraRTCProvider client={client as any}>
      <CallInterface />
    </AgoraRTCProvider>
  );
}

// ── Main call UI ──────────────────────────────────────────────────────────────
// useRTCClient() reads the client from context — no prop needed, no type clash.
function CallInterface() {
  const client      = useRTCClient();
  const navigate    = useNavigate();
  const channelName = useChannelName();

  const [token,        setToken]        = useState<string | null>(null);
  const [isTokenReady, setIsTokenReady] = useState(false);
  const [tokenError,   setTokenError]   = useState("");
  const [userClickedJoin, setUserClickedJoin] = useState(false);
  const [isMicOn, setIsMicOn] = useState(true);
  const [isCamOn, setIsCamOn] = useState(true);

  const { localMicrophoneTrack, error: micError } = useLocalMicrophoneTrack(userClickedJoin);
  const { localCameraTrack,     error: camError  } = useLocalCameraTrack(userClickedJoin);
  const remoteUsers = useRemoteUsers();

  // ── Fetch token ─────────────────────────────────────────────────────────
  useEffect(() => {
    if (APP_ID === "") {
      setTokenError("VITE_AGORA_APP_ID is not set in .env");
      setIsTokenReady(true);
      return;
    }

    const fetchToken = async () => {
      try {
        const res = await fetch(
          `${API_BASE_URL}/api/video/token?channelName=${encodeURIComponent(channelName)}`
        );
        if (!res.ok) throw new Error(`Token server returned ${res.status}`);
        const data = await res.json();
        setToken(data.token ?? data.rtcToken ?? null);
      } catch (e) {
        console.warn("Token fetch failed, joining in test mode:", e);
        setToken(null);
      } finally {
        setIsTokenReady(true);
      }
    };

    fetchToken();
  }, [channelName]);

  // ── Cleanup on unmount ──────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      localCameraTrack?.stop();
      localCameraTrack?.close();
      localMicrophoneTrack?.stop();
      localMicrophoneTrack?.close();
    };
  }, [localCameraTrack, localMicrophoneTrack]);

  // ── Join + publish ──────────────────────────────────────────────────────
  const canJoin    = isTokenReady && APP_ID !== "" && userClickedJoin;
  const canPublish = canJoin && !!localCameraTrack && !!localMicrophoneTrack;

  useJoin({ appid: APP_ID, channel: channelName, token, uid: 0 }, canJoin);
  usePublish([localMicrophoneTrack, localCameraTrack], canPublish);

  // ── Controls ────────────────────────────────────────────────────────────
  const toggleMic = () => {
    localMicrophoneTrack?.setMuted(isMicOn);
    setIsMicOn(p => !p);
  };

  const toggleCam = () => {
    localCameraTrack?.setEnabled(!isCamOn);
    setIsCamOn(p => !p);
  };

  const handleEndCall = async () => {
    try {
      localCameraTrack?.stop();    localCameraTrack?.close();
      localMicrophoneTrack?.stop(); localMicrophoneTrack?.close();
      await client.unpublish();
      await client.leave();
    } catch (e) {
      console.error("Error leaving channel:", e);
    } finally {
      navigate(-1);
    }
  };

  // ── Screens ─────────────────────────────────────────────────────────────
  if (APP_ID === "" || tokenError) {
    return (
      <div className="video-room-container" style={centeredStyle}>
        <h2 style={{ color: "#EF4444" }}>Video Not Configured</h2>
        <p style={{ color: "#9CA3AF", maxWidth: 400, textAlign: "center" }}>
          {tokenError || "VITE_AGORA_APP_ID is missing from your .env file."}
        </p>
      </div>
    );
  }

  if (camError || micError) {
    return (
      <div className="video-room-container" style={{ ...centeredStyle, padding: 20 }}>
        <h2 style={{ color: "white" }}>Camera / Mic Blocked</h2>
        <p style={{ color: "#9CA3AF", maxWidth: 400, lineHeight: 1.6, marginBottom: 20, textAlign: "center" }}>
          Your browser blocked access. Allow it in the address bar and reload.
        </p>
        <button onClick={() => window.location.reload()}
          className="control-btn end-call-btn" style={{ backgroundColor: "#0A5C36" }}>
          I allowed it — reload
        </button>
      </div>
    );
  }

  if (!userClickedJoin) {
    return (
      <div className="video-room-container" style={centeredStyle}>
        <h1 style={{ color: "white", marginBottom: 8 }}>Therapy Room</h1>
        <p style={{ color: "#6B7280", fontSize: 13, marginBottom: 4 }}>
          Channel: <code style={{ color: "#9CA3AF" }}>{channelName}</code>
        </p>
        <p style={{ color: "#9CA3AF", marginBottom: 32 }}>
          {isTokenReady ? "Room ready. Click below to enter." : "Connecting to server…"}
        </p>
        <button
          onClick={() => setUserClickedJoin(true)}
          className="control-btn end-call-btn"
          disabled={!isTokenReady}
          style={{
            backgroundColor: "#10B981", padding: "15px 40px",
            fontSize: "1.1rem", borderRadius: 30, width: "auto", height: "auto",
            opacity: isTokenReady ? 1 : 0.5,
            cursor: isTokenReady ? "pointer" : "not-allowed",
          }}
        >
          {isTokenReady ? "Join Call" : "Connecting…"}
        </button>
      </div>
    );
  }

  return (
    <div className="video-room-container">
      <div className="remote-video-container">
        {remoteUsers.length > 0 ? (
          <RemoteUser user={remoteUsers[0]} style={{ width: "100%", height: "100%" }} />
        ) : (
          <div className="waiting-text">
            Waiting for the other person to join…
            <br />
            <span style={{ fontSize: 12, color: "#6B7280" }}>Channel: {channelName}</span>
          </div>
        )}
      </div>

      <div className="local-video-container">
        {localCameraTrack && isCamOn ? (
          <LocalVideoTrack track={localCameraTrack} play
            style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        ) : (
          <div style={cameraOffStyle}>Camera Off</div>
        )}
      </div>

      <div className="call-controls">
        <button onClick={toggleMic} className={`control-btn ${isMicOn ? "active" : "muted"}`}>
          {isMicOn ? <MicOnIcon /> : <MicOffIcon />}
        </button>
        <button onClick={toggleCam} className={`control-btn ${isCamOn ? "active" : "muted"}`}>
          {isCamOn ? <CamOnIcon /> : <CamOffIcon />}
        </button>
        <button onClick={handleEndCall} className="control-btn end-call-btn">
          End Call
        </button>
      </div>
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────
const centeredStyle: React.CSSProperties = {
  display: "flex", flexDirection: "column",
  alignItems: "center", justifyContent: "center", color: "white",
};
const cameraOffStyle: React.CSSProperties = {
  width: "100%", height: "100%",
  display: "flex", alignItems: "center", justifyContent: "center",
  backgroundColor: "#1f2937", color: "#6b7280", fontSize: 13,
};

// ── Icons ─────────────────────────────────────────────────────────────────────
const MicOnIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
    <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
    <line x1="12" x2="12" y1="19" y2="22"/>
  </svg>
);
const MicOffIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <line x1="2" x2="22" y1="2" y2="22"/>
    <path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2"/>
    <path d="M5 10v2a7 7 0 0 0 12 5"/>
    <path d="M15 9.34V5a3 3 0 0 0-5.68-1.33"/>
    <path d="M9 9v3a3 3 0 0 0 5.12 2.12"/>
    <line x1="12" x2="12" y1="19" y2="22"/>
  </svg>
);
const CamOnIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="m22 8-6 4 6 4V8Z"/>
    <rect width="14" height="12" x="2" y="6" rx="2" ry="2"/>
  </svg>
);
const CamOffIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="m22 8-6 4 6 4V8Z"/>
    <rect width="14" height="12" x="2" y="6" rx="2" ry="2"/>
    <line x1="2" x2="22" y1="2" y2="22"/>
  </svg>
);