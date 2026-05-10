import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, useMap, Polyline } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import './EmergencyMap.css';

// Fix for default Leaflet marker icons not loading in React
const hospitalIcon = new L.Icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

// Custom Icon for User Location
const userIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

// Real facilities in Cebu City
const FACILITIES = [
  { id:1, name:'Vicente Sotto Memorial Medical Center (VSMMC)', address:'B. Rodriguez St, Cebu City', type:'Psychiatric Hospital', available:true, phone:'(032) 253 9891', hours:'24/7', lat: 10.3090, lng: 123.8932 },
  { id:2, name:'Chong Hua Hospital - Psychiatry Dept.', address:'Don Mariano Cui St, Cebu City', type:'Psychiatric Unit', available:true, phone:'(032) 255 8000', hours:'24/7', lat: 10.3087, lng: 123.8913 },
  { id:3, name:'Cebu Doctors University Hospital', address:'Osmeña Blvd, Cebu City', type:'Mental Health Ward', available:true, phone:'(032) 255 5555', hours:'24/7', lat: 10.3129, lng: 123.8934 },
  { id:4, name:'Gestalt Wellness Institute', address:'Taft Business Center, Gorordo Ave', type:'Outpatient Clinic', available:false, phone:'0915 540 5005', hours:'9AM–5PM', lat: 10.3150, lng: 123.8975 },
];

// ─── SMART MAP CONTROLLER ───
const MapController = ({ center, routeCoords }: { center: [number, number], routeCoords: [number, number][] }) => {
  const map = useMap();
  
  useEffect(() => {
    const timer = setTimeout(() => {
      map.invalidateSize(); 
      if (routeCoords.length > 0) {
        map.fitBounds(routeCoords, { padding: [50, 50], maxZoom: 16 });
      } else {
        map.setView(center, 15);
      }
    }, 250);
    
    return () => clearTimeout(timer);
  }, [center, routeCoords, map]);
  
  return null;
};

// ─── FLOATING "LOCATE ME" BUTTON ───
const LocateControl = ({ userLocation, onLocate }: { userLocation: [number, number], onLocate: () => void }) => {
  const map = useMap();
  return (
    <button
      className="em-locate-btn"
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        onLocate(); 
        map.flyTo(userLocation, 15, { animate: true, duration: 1.5 });
      }}
      title="Go to my location"
    >
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polygon points="3 11 22 2 13 21 11 13 3 11" />
      </svg>
    </button>
  );
};

const EmergencyMap: React.FC = () => {
  const navigate = useNavigate();
  const [selected, setSelected] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  
  const [userLocation, setUserLocation] = useState<[number, number]>([10.3157, 123.8854]);
  const [userAddress, setUserAddress] = useState<string>("Locating your address...");
  const [locationLoaded, setLocationLoaded] = useState(false);

  // Routing States
  const [routeCoords, setRouteCoords] = useState<[number, number][]>([]);
  const [routeStats, setRouteStats] = useState<{ dist: string, drive: string, walk: string, transit: string } | null>(null);
  const [isRouting, setIsRouting] = useState(false);
  const [showRouteData, setShowRouteData] = useState(false);

  useEffect(() => {
    const fetchAddress = async (lat: number, lng: number) => {
      try {
        const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=16`);
        const data = await res.json();
        if (data && data.display_name) {
          const shortAddress = data.display_name.split(',').slice(0, 3).join(',');
          setUserAddress(shortAddress);
        } else {
          setUserAddress("Current Location");
        }
      } catch (err) {
        console.error("Geocoding failed", err);
        setUserAddress("Current Location");
      }
    };

    if ("geolocation" in navigator) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const lat = position.coords.latitude;
          const lng = position.coords.longitude;
          setUserLocation([lat, lng]);
          setLocationLoaded(true);
          fetchAddress(lat, lng); 
        },
        (error) => {
          console.warn("Geolocation failed. Defaulting to Cebu City center.");
          setLocationLoaded(true);
          setUserAddress("Cebu City (Default Location)");
        }
      );
    } else {
      setLocationLoaded(true);
      setUserAddress("Cebu City (Default Location)");
    }
  }, []);

  const selectedFacility = FACILITIES.find(f => f.id === selected);

  const handleSelectFacility = (id: number) => {
    if (selected === id) {
      setSelected(null);
      setRouteCoords([]);
      setRouteStats(null);
      setShowRouteData(false);
    } else {
      setSelected(id);
      setRouteCoords([]);
      setRouteStats(null);
      setShowRouteData(false);
    }
  };

  const handleGetDirections = async (targetFacilityOverride?: typeof FACILITIES[0]) => {
    const target = targetFacilityOverride || selectedFacility;
    if (!target || !userLocation) return;
    
    setIsRouting(true);
    setShowRouteData(true);

    try {
      const [startLat, startLng] = userLocation;
      const [endLat, endLng] = [target.lat, target.lng];
      
      const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${startLng},${startLat};${endLng},${endLat}?overview=full&geometries=geojson`);
      const data = await res.json();
      
      if (data.routes && data.routes[0]) {
        const route = data.routes[0];
        const coords = route.geometry.coordinates.map((c: any) => [c[1], c[0]] as [number, number]);
        setRouteCoords(coords);

        const distKm = (route.distance / 1000).toFixed(1);
        const driveMins = Math.max(1, Math.round(route.duration / 60));
        const walkMins = Math.max(1, Math.round(route.distance / 80)); 
        const transitMins = Math.round(driveMins * 1.5 + 10); 

        setRouteStats({
          dist: `${distKm} km`,
          drive: `${driveMins} min`,
          walk: `${walkMins} min`,
          transit: `${transitMins} min`
        });
      }
    } catch (err) {
      console.error("Routing failed:", err);
    } finally {
      setIsRouting(false);
    }
  };

  const handleLocateMe = () => {
    setSelected(null);
    setRouteCoords([]);
    setRouteStats(null);
    setShowRouteData(false);
  };

  const filtered = FACILITIES.filter(f =>
    f.name.toLowerCase().includes(search.toLowerCase()) ||
    f.address.toLowerCase().includes(search.toLowerCase())
  );

  const mapCenter = selectedFacility ? [selectedFacility.lat, selectedFacility.lng] as [number, number] : userLocation;

  return (
    <div className="em-root">
      <style>{`
        .em-root { display: flex; height: 100vh; width: 100vw; overflow: hidden; }
        
        .em-map-container {
            flex: 1;
            position: relative;
            height: 100vh;
            width: 100%;
        }

        .leaflet-container {
            position: absolute !important;
            top: 0; bottom: 0; left: 0; right: 0;
            width: 100% !important;
            height: 100% !important;
        }

        .em-route-stats { display: flex; gap: 12px; margin-top: 16px; padding: 12px; background: #f8fafc; border-radius: 12px; border: 1px solid #e2e8f0; }
        .em-route-box { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; }
        .em-route-box svg { color: #64748b; }
        .em-route-time { font-size: 13px; font-weight: 700; color: #1e293b; }
        .em-route-label { font-size: 11px; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em; }
        .em-dist-pill { display: inline-block; background: #e2e8f0; color: #475569; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 99px; margin-bottom: 8px; }
        
        /* Sidebar Button */
        .em-dir-btn { width: 100%; display: block; text-align: center; background: #0A5C36; color: white; border: none; padding: 10px; border-radius: 8px; font-weight: 600; cursor: pointer; transition: 0.2s; margin-top: 16px; font-family: inherit; font-size: 14px; }
        .em-dir-btn:hover { background: #064e3b; }
        .em-dir-btn:disabled { opacity: 0.6; cursor: not-allowed; }

        /* Marker Popup Button */
        .em-dir-btn-small { width: 100%; display: block; text-align: center; background: #0A5C36; color: white; border: none; padding: 8px; border-radius: 6px; font-weight: 600; cursor: pointer; transition: 0.2s; margin-top: 10px; font-family: inherit; font-size: 13px; }
        .em-dir-btn-small:hover { background: #064e3b; }
        .em-dir-btn-small:disabled { opacity: 0.6; cursor: not-allowed; }
        
        .em-locate-btn { position: absolute; bottom: 30px; right: 20px; z-index: 1000; background: white; border: 2px solid rgba(0,0,0,0.1); border-radius: 12px; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; cursor: pointer; color: #1e293b; box-shadow: 0 4px 6px rgba(0,0,0,0.1); transition: 0.2s; }
        .em-locate-btn:hover { background: #f8fafc; color: #0A5C36; transform: translateY(-2px); box-shadow: 0 6px 12px rgba(0,0,0,0.15); }

        .em-address-popup { text-align: center; }
        .em-address-popup strong { display: block; font-size: 14px; color: #0f172a; margin-bottom: 4px; }
        .em-address-popup span { font-size: 13px; color: #64748b; line-height: 1.4; }
      `}</style>

      <aside className="em-sidebar">
        <div className="em-sidebar-header">
          <button className="em-back-btn" onClick={() => navigate('/dashboard')}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
            Back to Dashboard
          </button>
          <h1>Nearby Psychiatric Wards</h1>
          <p>Emergency mental health facilities in Cebu City</p>
        </div>

        <div className="em-search-wrap">
          <div className="em-search-inner">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input placeholder="Search facilities…" value={search} onChange={e => setSearch(e.target.value)} />
          </div>
        </div>

        <div className="em-facility-list">
          {filtered.map(f => (
            <div
              key={f.id}
              className={`em-facility-item${selected === f.id ? ' active' : ''}`}
              onClick={() => handleSelectFacility(f.id)}
            >
              <div className="em-fac-top">
                <div className="em-fac-name">{f.name}</div>
              </div>
              
              {selected === f.id && routeStats && showRouteData && (
                <div className="em-dist-pill">{routeStats.dist} away</div>
              )}

              <div className="em-fac-addr">{f.address}</div>
              <div className="em-fac-badges">
                <span className="em-badge em-badge-type">{f.type}</span>
                <span className={`em-badge ${f.available ? 'em-badge-open' : 'em-badge-limited'}`}>
                  {f.available ? '● Open 24/7' : '● Limited hours'}
                </span>
              </div>
              
              {selected === f.id && (
                <div className="em-fac-detail">
                  <p>📞 {f.phone}</p>
                  <p>🕐 Hours: {f.hours}</p>
                  
                  {isRouting ? (
                    <p style={{ color: '#64748b', fontSize: 13, marginTop: 12 }}>Calculating fastest route...</p>
                  ) : routeStats && showRouteData ? (
                    <div className="em-route-stats">
                      <div className="em-route-box">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/><circle cx="7" cy="17" r="2"/><path d="M9 17h6"/><circle cx="17" cy="17" r="2"/></svg>
                        <span className="em-route-time">{routeStats.drive}</span>
                        <span className="em-route-label">Drive</span>
                      </div>
                      <div className="em-route-box">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/><path d="M8 14h.01"/><path d="M12 14h.01"/><path d="M16 14h.01"/><path d="M8 18h.01"/><path d="M12 18h.01"/><path d="M16 18h.01"/></svg>
                        <span className="em-route-time">{routeStats.transit}</span>
                        <span className="em-route-label">Transit</span>
                      </div>
                      <div className="em-route-box">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M13 4v16"/><path d="M17 4v16"/><path d="M19 4H5c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z"/></svg>
                        <span className="em-route-time">{routeStats.walk}</span>
                        <span className="em-route-label">Walk</span>
                      </div>
                    </div>
                  ) : (
                    <button 
                      className="em-dir-btn"
                      onClick={(e) => {
                        e.stopPropagation(); 
                        handleGetDirections();
                      }}
                      disabled={isRouting}
                    >
                      Get Directions
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="em-crisis">
          <div className="em-crisis-label">National Crisis Line (PH)</div>
          <div className="em-crisis-num">1553</div>
          <div className="em-crisis-sub">NCMH Crisis Hotline — Call toll-free 24/7</div>
        </div>
      </aside>

      {/* ── Leaflet Map ── */}
      <div className="em-map-container">
        {locationLoaded ? (
          <MapContainer 
            center={userLocation} 
            zoom={15} 
            minZoom={12} 
            zoomControl={true}
            style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }}
          >
            <TileLayer
              attribution='© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              noWrap={true}
            />
            
            <MapController center={mapCenter} routeCoords={showRouteData ? routeCoords : []} />

            <LocateControl userLocation={userLocation} onLocate={handleLocateMe} />

            {showRouteData && routeCoords.length > 0 && (
              <Polyline positions={routeCoords} color="#0A5C36" weight={5} opacity={0.8} />
            )}

            <Marker position={userLocation} icon={userIcon}>
              <Popup>
                <div className="em-address-popup">
                  <strong>You are here at:</strong>
                  <span>{userAddress}</span>
                </div>
              </Popup>
            </Marker>

            {filtered.map(f => (
              <Marker 
                key={f.id} 
                position={[f.lat, f.lng]}
                icon={hospitalIcon} 
                eventHandlers={{ click: () => handleSelectFacility(f.id) }}
              >
                {/* 🔴 NEW: Updated Popup with the required styling, hours, and dynamic route stats block */}
                <Popup minWidth={260}>
                  <strong style={{ fontSize: '15px', color: '#0f172a', marginBottom: '4px' }}>{f.name}</strong>
                  <div style={{ color: '#64748b', fontSize: '13.5px', marginBottom: '6px' }}>{f.address}</div>
                  
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600, fontSize: '13.5px', color: '#1e293b', marginBottom: '4px' }}>
                    📞 {f.phone}
                  </div>
                  
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13.5px', color: '#475569', marginBottom: '12px' }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                    </svg>
                    Hours: {f.hours}
                  </div>

                  {selected === f.id && isRouting ? (
                    <div style={{ textAlign: 'center', padding: '10px', color: '#64748b', fontSize: '13px', background: '#f8fafc', borderRadius: '8px' }}>
                      Calculating route...
                    </div>
                  ) : selected === f.id && routeStats && showRouteData ? (
                    <div className="em-route-stats" style={{ marginTop: '4px', padding: '10px', gap: '8px' }}>
                      <div className="em-route-box">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/><circle cx="7" cy="17" r="2"/><path d="M9 17h6"/><circle cx="17" cy="17" r="2"/></svg>
                        <span className="em-route-time">{routeStats.drive}</span>
                        <span className="em-route-label">Drive</span>
                      </div>
                      <div className="em-route-box">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/><path d="M8 14h.01"/><path d="M12 14h.01"/><path d="M16 14h.01"/><path d="M8 18h.01"/><path d="M12 18h.01"/><path d="M16 18h.01"/></svg>
                        <span className="em-route-time">{routeStats.transit}</span>
                        <span className="em-route-label">Transit</span>
                      </div>
                      <div className="em-route-box">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M13 4v16"/><path d="M17 4v16"/><path d="M19 4H5c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z"/></svg>
                        <span className="em-route-time">{routeStats.walk}</span>
                        <span className="em-route-label">Walk</span>
                      </div>
                    </div>
                  ) : (
                    <button 
                      className="em-dir-btn-small"
                      onClick={(e) => {
                        e.stopPropagation();
                        if (selected !== f.id) {
                          setSelected(f.id);
                        }
                        handleGetDirections(f); // Pass the facility directly so it routes instantly
                      }}
                    >
                      Get Directions
                    </button>
                  )}
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        ) : (
          <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64748b' }}>
            Loading map and locating you...
          </div>
        )}
      </div>
    </div>
  );
};

export default EmergencyMap;