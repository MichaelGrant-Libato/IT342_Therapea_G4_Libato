import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import AgoraRTC, {
  type IAgoraRTCClient,
  type IAgoraRTCRemoteUser,
  type ICameraVideoTrack,
  type IMicrophoneAudioTrack,
} from "agora-rtc-sdk-ng";
import "./VideoRoom.css";

const ENV_APP_ID = (import.meta.env.VITE_AGORA_APP_ID as string) ?? "";
const RAW_API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string) ?? "";
const API_BASE_URL = RAW_API_BASE_URL.replace(/\/$/, "");

function useAppointmentId(): string {
  const { appointmentId: routeAppointmentId } = useParams<{
    appointmentId?: string;
  }>();

  const [searchParams] = useSearchParams();

  return (
    routeAppointmentId ||
    searchParams.get("appointmentId") ||
    searchParams.get("id") ||
    "default"
  );
}

function makeFallbackChannelName(appointmentId: string): string {
  return `therapy-session-${appointmentId}`;
}

function getStableWebUid(): number {
  const key = "therapea_web_agora_uid";

  const raw = sessionStorage.getItem(key);
  const existing = Number.parseInt(raw ?? "", 10);

  if (Number.isInteger(existing) && existing >= 10000 && existing <= 39999) {
    return existing;
  }

  sessionStorage.removeItem(key);
  localStorage.removeItem(key);

  const next = 10000 + Math.floor(Math.random() * 30000);
  sessionStorage.setItem(key, String(next));

  return next;
}

export default function VideoRoom() {
  const navigate = useNavigate();
  const appointmentId = useAppointmentId();

  const uid = useMemo<number>(() => {
    const safeUid = getStableWebUid();
    console.log("FINAL_WEB_UID_USED_BY_AGORA:", safeUid, typeof safeUid);
    return safeUid;
  }, []);

  const client = useMemo<IAgoraRTCClient>(() => {
    return AgoraRTC.createClient({
      mode: "rtc",
      codec: "vp8",
    });
  }, []);

  const fallbackChannelName = useMemo(() => {
    return makeFallbackChannelName(appointmentId);
  }, [appointmentId]);

  const remoteVideoRef = useRef<HTMLDivElement | null>(null);

  const [localVideoNode, setLocalVideoNode] = useState<HTMLDivElement | null>(
    null
  );

  const localAudioTrackRef = useRef<IMicrophoneAudioTrack | null>(null);
  const localCameraTrackRef = useRef<ICameraVideoTrack | null>(null);

  const joinedRef = useRef(false);
  const publishedRef = useRef(false);

  const [appId, setAppId] = useState(ENV_APP_ID);
  const [channelName, setChannelName] = useState(fallbackChannelName);
  const [token, setToken] = useState<string | null>(null);

  const [isTokenReady, setIsTokenReady] = useState(false);
  const [tokenError, setTokenError] = useState("");

  const [isJoining, setIsJoining] = useState(false);
  const [inCall, setInCall] = useState(false);
  const [hasRemoteVideo, setHasRemoteVideo] = useState(false);

  const [isMicOn, setIsMicOn] = useState(true);
  const [isCamOn, setIsCamOn] = useState(true);

  useEffect(() => {
    setIsTokenReady(false);
    setTokenError("");
    setToken(null);
    setChannelName(fallbackChannelName);

    const fetchToken = async () => {
      try {
        const url = `${API_BASE_URL}/api/video/token?appointmentId=${encodeURIComponent(
          appointmentId
        )}&uid=${uid}`;

        console.log("AGORA_WEB_FETCH_TOKEN:", url);

        const res = await fetch(url, {
          headers: {
            "ngrok-skip-browser-warning": "true",
          },
        });

        if (!res.ok) {
          const errorText = await res.text().catch(() => "");
          throw new Error(`Token server returned ${res.status}. ${errorText}`);
        }

        const data = await res.json();

        const serverToken = data.token ?? data.rtcToken ?? null;
        const serverAppId = data.appId || ENV_APP_ID;
        const serverChannel = data.channelName || fallbackChannelName;

        if (!serverToken) {
          throw new Error("Token server did not return a token.");
        }

        if (!serverAppId) {
          throw new Error("Agora App ID is missing.");
        }

        setToken(serverToken);
        setAppId(serverAppId);
        setChannelName(serverChannel);

        console.log("AGORA_WEB_TOKEN_READY:", {
          appointmentId,
          uid,
          channelName: serverChannel,
          appId: serverAppId,
        });
      } catch (error) {
        console.error("AGORA_WEB_TOKEN_FAILED:", error);
        setTokenError("Could not prepare the video room. Please try again.");
      } finally {
        setIsTokenReady(true);
      }
    };

    fetchToken();
  }, [appointmentId, uid, fallbackChannelName]);

  useEffect(() => {
    const handleUserJoined = (user: IAgoraRTCRemoteUser) => {
      console.log("AGORA_WEB_USER_JOINED:", user.uid);
    };

    const handleUserPublished = async (
      user: IAgoraRTCRemoteUser,
      mediaType: "audio" | "video"
    ) => {
      console.log("AGORA_WEB_USER_PUBLISHED:", user.uid, mediaType);

      try {
        await client.subscribe(user, mediaType);
        console.log("AGORA_WEB_SUBSCRIBE_SUCCESS:", user.uid, mediaType);

        if (mediaType === "video") {
          setHasRemoteVideo(true);

          window.requestAnimationFrame(() => {
            if (remoteVideoRef.current && user.videoTrack) {
              user.videoTrack.play(remoteVideoRef.current);
              console.log("AGORA_WEB_REMOTE_VIDEO_PLAYING:", user.uid);
            }
          });
        }

        if (mediaType === "audio") {
          user.audioTrack?.play();
        }
      } catch (error) {
        console.error("AGORA_WEB_SUBSCRIBE_FAILED:", error);
      }
    };

    const handleUserUnpublished = (
      user: IAgoraRTCRemoteUser,
      mediaType: "audio" | "video"
    ) => {
      console.log("AGORA_WEB_USER_UNPUBLISHED:", user.uid, mediaType);

      if (mediaType === "video") {
        user.videoTrack?.stop();
        setHasRemoteVideo(false);
      }
    };

    const handleUserLeft = (user: IAgoraRTCRemoteUser) => {
      console.log("AGORA_WEB_USER_LEFT:", user.uid);

      user.videoTrack?.stop();
      setHasRemoteVideo(false);
    };

    const handleConnectionStateChange = (
      currentState: string,
      previousState: string,
      reason?: string
    ) => {
      console.log("AGORA_WEB_CONNECTION:", {
        currentState,
        previousState,
        reason,
      });
    };

    client.on("user-joined", handleUserJoined);
    client.on("user-published", handleUserPublished);
    client.on("user-unpublished", handleUserUnpublished);
    client.on("user-left", handleUserLeft);
    client.on("connection-state-change", handleConnectionStateChange);

    return () => {
      client.off("user-joined", handleUserJoined);
      client.off("user-published", handleUserPublished);
      client.off("user-unpublished", handleUserUnpublished);
      client.off("user-left", handleUserLeft);
      client.off("connection-state-change", handleConnectionStateChange);
    };
  }, [client]);

  useEffect(() => {
    const cameraTrack = localCameraTrackRef.current;

    if (!inCall || !isCamOn || !localVideoNode || !cameraTrack) {
      return;
    }

    cameraTrack.play(localVideoNode);

    return () => {
      cameraTrack.stop();
    };
  }, [inCall, isCamOn, localVideoNode]);

  const startCall = async () => {
    if (!isTokenReady || !token || !appId || !channelName) {
      setTokenError("Video room is not ready yet. Please try again.");
      return;
    }

    if (joinedRef.current || isJoining) {
      return;
    }

    setIsJoining(true);
    setTokenError("");

    try {
      console.log("AGORA_WEB_START_CALL:", {
        appId,
        channelName,
        uid,
        uidType: typeof uid,
      });

      const [microphoneTrack, cameraTrack] = await Promise.all([
        AgoraRTC.createMicrophoneAudioTrack(),
        AgoraRTC.createCameraVideoTrack(),
      ]);

      localAudioTrackRef.current = microphoneTrack;
      localCameraTrackRef.current = cameraTrack;

      setInCall(true);

      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve());
      });

      await client.join(appId, channelName, token, Number(uid));
      joinedRef.current = true;

      console.log("AGORA_WEB_JOIN_SUCCESS:", {
        uid,
        channelName,
      });

      await client.publish([microphoneTrack, cameraTrack]);
      publishedRef.current = true;

      console.log("AGORA_WEB_PUBLISH_SUCCESS:", {
        uid,
        channelName,
        tracks: 2,
      });
    } catch (error) {
      console.error("AGORA_WEB_START_CALL_FAILED:", error);

      await cleanupAgora();

      setTokenError(
        "Could not join or publish the video call. Please reload and try again."
      );
    } finally {
      setIsJoining(false);
    }
  };

  const cleanupAgora = async () => {
    try {
      if (publishedRef.current) {
        const tracks = [
          localAudioTrackRef.current,
          localCameraTrackRef.current,
        ].filter(Boolean) as Array<IMicrophoneAudioTrack | ICameraVideoTrack>;

        if (tracks.length > 0) {
          await client.unpublish(tracks);
        }
      }
    } catch (error) {
      console.error("AGORA_WEB_UNPUBLISH_FAILED:", error);
    }

    try {
      if (joinedRef.current) {
        await client.leave();
      }
    } catch (error) {
      console.error("AGORA_WEB_LEAVE_FAILED:", error);
    }

    try {
      localCameraTrackRef.current?.stop();
      localCameraTrackRef.current?.close();

      localAudioTrackRef.current?.stop();
      localAudioTrackRef.current?.close();
    } catch (error) {
      console.error("AGORA_WEB_TRACK_CLEANUP_FAILED:", error);
    }

    localCameraTrackRef.current = null;
    localAudioTrackRef.current = null;

    joinedRef.current = false;
    publishedRef.current = false;

    setInCall(false);
    setHasRemoteVideo(false);
  };

  const toggleMic = async () => {
    const audioTrack = localAudioTrackRef.current;
    if (!audioTrack) return;

    const nextValue = !isMicOn;

    await audioTrack.setMuted(!nextValue);
    setIsMicOn(nextValue);
  };

  const toggleCam = async () => {
    const cameraTrack = localCameraTrackRef.current;
    if (!cameraTrack) return;

    const nextValue = !isCamOn;

    await cameraTrack.setEnabled(nextValue);
    setIsCamOn(nextValue);

    if (nextValue && localVideoNode) {
      cameraTrack.play(localVideoNode);
    } else {
      cameraTrack.stop();
    }
  };

  const handleEndCall = async () => {
    await cleanupAgora();
    navigate(-1);
  };

  useEffect(() => {
    return () => {
      cleanupAgora();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (tokenError) {
    return (
      <div className="video-room-container" style={centeredStyle}>
        <h2 style={{ color: "#EF4444" }}>Video Not Available</h2>

        <p style={{ color: "#9CA3AF", maxWidth: 440, textAlign: "center" }}>
          {tokenError}
        </p>

        <button
          onClick={() => window.location.reload()}
          className="control-btn end-call-btn"
          style={{ backgroundColor: "#0A5C36" }}
        >
          Retry
        </button>
      </div>
    );
  }

  if (!inCall) {
    return (
      <div className="video-room-container" style={centeredStyle}>
        <h1 style={{ color: "white", marginBottom: 8 }}>Therapy Room</h1>

        <p style={{ color: "#6B7280", fontSize: 13, marginBottom: 4 }}>
          Appointment: <code style={{ color: "#9CA3AF" }}>{appointmentId}</code>
        </p>

        <p style={{ color: "#6B7280", fontSize: 13, marginBottom: 4 }}>
          Channel: <code style={{ color: "#9CA3AF" }}>{channelName}</code>
        </p>

        <p style={{ color: "#6B7280", fontSize: 13, marginBottom: 4 }}>
          Web UID: <code style={{ color: "#9CA3AF" }}>{uid}</code>
        </p>

        <p style={{ color: "#9CA3AF", marginBottom: 32 }}>
          {isTokenReady
            ? "Room ready. Click below to enter."
            : "Connecting to server..."}
        </p>

        <button
          onClick={startCall}
          className="control-btn end-call-btn"
          disabled={!isTokenReady || !token || isJoining}
          style={{
            backgroundColor: "#10B981",
            padding: "15px 40px",
            fontSize: "1.1rem",
            borderRadius: 30,
            width: "auto",
            height: "auto",
            opacity: isTokenReady && token && !isJoining ? 1 : 0.5,
            cursor:
              isTokenReady && token && !isJoining ? "pointer" : "not-allowed",
          }}
        >
          {isJoining ? "Joining..." : isTokenReady ? "Join Call" : "Connecting..."}
        </button>
      </div>
    );
  }

  return (
    <div className="video-room-container">
      <div className="remote-video-container" ref={remoteVideoRef}>
        {!hasRemoteVideo && (
          <div className="waiting-text">
            Waiting for the other person to join...
            <br />
            <span style={{ fontSize: 12, color: "#6B7280" }}>
              Channel: {channelName}
            </span>
            <br />
            <span style={{ fontSize: 12, color: "#6B7280" }}>
              Web UID: {uid}
            </span>
          </div>
        )}
      </div>

      <div className="local-video-container">
        {isCamOn ? (
          <div
            ref={setLocalVideoNode}
            style={{
              width: "100%",
              height: "100%",
              borderRadius: 12,
              overflow: "hidden",
              backgroundColor: "#111827",
            }}
          />
        ) : (
          <div style={cameraOffStyle}>Camera Off</div>
        )}
      </div>

      <div className="call-controls">
        <button
          onClick={toggleMic}
          className={`control-btn ${isMicOn ? "active" : "muted"}`}
        >
          {isMicOn ? <MicOnIcon /> : <MicOffIcon />}
        </button>

        <button
          onClick={toggleCam}
          className={`control-btn ${isCamOn ? "active" : "muted"}`}
        >
          {isCamOn ? <CamOnIcon /> : <CamOffIcon />}
        </button>

        <button onClick={handleEndCall} className="control-btn end-call-btn">
          End Call
        </button>
      </div>
    </div>
  );
}

const centeredStyle: CSSProperties = {
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  color: "white",
};

const cameraOffStyle: CSSProperties = {
  width: "100%",
  height: "100%",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  backgroundColor: "#1f2937",
  color: "#6b7280",
  fontSize: 13,
};

const MicOnIcon = () => (
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
  >
    <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
    <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
    <line x1="12" x2="12" y1="19" y2="22" />
  </svg>
);

const MicOffIcon = () => (
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
  >
    <line x1="2" x2="22" y1="2" y2="22" />
    <path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2" />
    <path d="M5 10v2a7 7 0 0 0 12 5" />
    <path d="M15 9.34V5a3 3 0 0 0-5.68-1.33" />
    <path d="M9 9v3a3 3 0 0 0 5.12 2.12" />
    <line x1="12" x2="12" y1="19" y2="22" />
  </svg>
);

const CamOnIcon = () => (
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
  >
    <path d="m22 8-6 4 6 4V8Z" />
    <rect width="14" height="12" x="2" y="6" rx="2" ry="2" />
  </svg>
);

const CamOffIcon = () => (
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
  >
    <path d="m22 8-6 4 6 4V8Z" />
    <rect width="14" height="12" x="2" y="6" rx="2" ry="2" />
    <line x1="2" x2="22" y1="2" y2="22" />
  </svg>
);