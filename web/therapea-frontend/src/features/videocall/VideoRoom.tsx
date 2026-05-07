import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { 
  AgoraRTCProvider, 
  useRTCClient,
  useLocalCameraTrack, 
  useLocalMicrophoneTrack, 
  useJoin, 
  usePublish,
  useRemoteUsers,
  RemoteUser,
  LocalVideoTrack
} from "agora-rtc-react";
import AgoraRTC from "agora-rtc-sdk-ng";
import "./VideoRoom.css";

const FALLBACK_APP_ID = import.meta.env.VITE_AGORA_APP_ID || "";

export default function VideoRoom() {
  const client = useRTCClient(AgoraRTC.createClient({ codec: "vp8", mode: "rtc" }) as any);

  return (
    <AgoraRTCProvider client={client}>
      <CallInterface client={client} />
    </AgoraRTCProvider>
  );
}

function CallInterface({ client }: { client: any }) {
  const navigate = useNavigate();
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8083';
  
  const [token, setToken] = useState<string | null>(null);
  const [appId, setAppId] = useState<string>("");
  const [isTokenReady, setIsTokenReady] = useState(false);
  
  // THE FIX: Forces user interaction to bypass Mobile Autoplay Blocks
  const [userClickedJoin, setUserClickedJoin] = useState(false);
  
  const channelName = "therapy-session-1"; 

  const [isMicOn, setIsMicOn] = useState(true);
  const [isCamOn, setIsCamOn] = useState(true);

  // Hardware only activates AFTER clicking join
  const { localMicrophoneTrack, error: micError } = useLocalMicrophoneTrack(userClickedJoin);
  const { localCameraTrack, error: camError } = useLocalCameraTrack(userClickedJoin);
  const remoteUsers = useRemoteUsers();

  useEffect(() => {
    return () => {
      if (localCameraTrack) {
        localCameraTrack.stop();
        localCameraTrack.close();
      }
      if (localMicrophoneTrack) {
        localMicrophoneTrack.stop();
        localMicrophoneTrack.close();
      }
    };
  }, [localCameraTrack, localMicrophoneTrack]);

  useEffect(() => {
    const fetchToken = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/video/token?channelName=${channelName}`);
        if (!response.ok) throw new Error("Backend 403 or Not Responding");
        
        const data = await response.json();
        setToken(data.token);
        setAppId(data.appId);
      } catch (error) {
        setAppId(FALLBACK_APP_ID);
        setToken(null);
      } finally {
        setIsTokenReady(true);
      }
    };
    fetchToken();
  }, [API_BASE_URL, channelName]);

  const canJoin = isTokenReady && appId !== "" && userClickedJoin;

  
  useJoin({ appid: appId, channel: channelName, token: token, uid: 0 }, canJoin);  
  const isPublishReady = canJoin && !!localCameraTrack && !!localMicrophoneTrack;
  usePublish([localMicrophoneTrack, localCameraTrack], isPublishReady);
  
  const toggleMic = () => {
    if (localMicrophoneTrack) {
      localMicrophoneTrack.setMuted(isMicOn); 
      setIsMicOn(!isMicOn);
    }
  };

  const toggleCam = () => {
    if (localCameraTrack) {
      localCameraTrack.setEnabled(!isCamOn); 
      setIsCamOn(!isCamOn);
    }
  };

  const handleEndCall = async () => {
    try {
      await client.unpublish();
      await client.leave();
    } catch (e) {
      console.error("Error leaving channel", e);
    } finally {
      navigate("/dashboard");
    }
  };

  // --- LOBBY SCREEN (BYPASSES AUTOPLAY BLOCK) ---
  if (!userClickedJoin) {
    return (
      <div className="video-room-container" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
        <h1 style={{ marginBottom: '20px' }}>Therapy Room Ready</h1>
        <p style={{ marginBottom: '30px', color: '#9CA3AF' }}>Click below to enter the session.</p>
        <button 
          onClick={() => setUserClickedJoin(true)} 
          className="control-btn end-call-btn" 
          style={{ backgroundColor: '#10B981', padding: '15px 40px', fontSize: '1.2rem', borderRadius: '30px', width: 'auto', height: 'auto' }}
          disabled={!isTokenReady}
        >
          {isTokenReady ? "Join Call" : "Connecting to server..."}
        </button>
      </div>
    );
  }

  if (camError || micError) {
    return (
      <div className="video-room-container" style={{ flexDirection: 'column', color: 'white', textAlign: 'center', padding: '20px' }}>
        <h2>Hardware Blocked</h2>
        <p style={{ maxWidth: '400px', lineHeight: '1.6', marginBottom: '20px' }}>
          Your browser blocked access to the camera or microphone. Please allow access in your URL bar and refresh.
        </p>
        <button onClick={() => window.location.reload()} className="control-btn end-call-btn" style={{ backgroundColor: '#0A5C36' }}>
          I have allowed it, reload page
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
          <div className="waiting-text">Waiting for the other person to join...</div>
        )}
      </div>

      <div className="local-video-container">
        {localCameraTrack && isCamOn ? (
          <LocalVideoTrack track={localCameraTrack} play={true} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        ) : (
          <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", backgroundColor: "#1f2937", color: "#6b7280" }}>
            Camera Off
          </div>
        )}
      </div>

      <div className="call-controls">
        <button onClick={toggleMic} className={`control-btn ${isMicOn ? "active" : "muted"}`} title="Toggle Mic">
          {isMicOn ? (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" x2="12" y1="19" y2="22"/></svg>
          ) : (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="2" x2="22" y1="2" y2="22"/><path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2"/><path d="M5 10v2a7 7 0 0 0 12 5"/><path d="M15 9.34V5a3 3 0 0 0-5.68-1.33"/><path d="M9 9v3a3 3 0 0 0 5.12 2.12"/><line x1="12" x2="12" y1="19" y2="22"/></svg>
          )}
        </button>

        <button onClick={toggleCam} className={`control-btn ${isCamOn ? "active" : "muted"}`} title="Toggle Cam">
          {isCamOn ? (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m22 8-6 4 6 4V8Z"/><rect width="14" height="12" x="2" y="6" rx="2" ry="2"/></svg>
          ) : (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m22 8-6 4 6 4V8Z"/><rect width="14" height="12" x="2" y="6" rx="2" ry="2"/><line x1="2" x2="22" y1="2" y2="22"/></svg>
          )}
        </button>

        <button onClick={handleEndCall} className="control-btn end-call-btn">
          End Call
        </button>
      </div>
    </div>
  );
}