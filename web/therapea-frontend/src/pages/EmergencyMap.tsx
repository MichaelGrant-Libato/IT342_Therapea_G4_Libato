import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import '../styles/EmergencyMap.css';

// Fix for default Leaflet marker icons not loading in React
import iconRetina from 'leaflet/dist/images/marker-icon-2x.png';
import iconUrl from 'leaflet/dist/images/marker-icon.png';
import shadowUrl from 'leaflet/dist/images/marker-shadow.png';

L.Icon.Default.mergeOptions({
  iconRetinaUrl: iconRetina,
  iconUrl: iconUrl,
  shadowUrl: shadowUrl,
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
  { id:1, name:'Vicente Sotto Memorial Medical Center (VSMMC) Behavioral Sciences', address:'B. Rodriguez St, Cebu City', distance:'--', type:'Psychiatric Hospital', available:true, phone:'(032) 253 9891', hours:'24/7', lat: 10.3090, lng: 123.8932 },
  { id:2, name:'Chong Hua Hospital - Psychiatry Dept.', address:'Don Mariano Cui St, Cebu City', distance:'--', type:'Psychiatric Unit', available:true, phone:'(032) 255 8000', hours:'24/7', lat: 10.3087, lng: 123.8913 },
  { id:3, name:'Cebu Doctors University Hospital', address:'Osmeña Blvd, Cebu City', distance:'--', type:'Mental Health Ward', available:true, phone:'(032) 255 5555', hours:'24/7', lat: 10.3129, lng: 123.8934 },
  { id:4, name:'Gestalt Wellness Institute', address:'Taft Business Center, Gorordo Ave, Cebu City', distance:'--', type:'Outpatient Clinic', available:false, phone:'0915 540 5005', hours:'9AM–5PM', lat: 10.3150, lng: 123.8975 },
];

// Helper component to smoothly animate the map to a selected marker
const ChangeView = ({ center, zoom }: { center: [number, number], zoom: number }) => {
  const map = useMap();
  map.flyTo(center, zoom);
  return null;
};

const EmergencyMap: React.FC = () => {
  const navigate = useNavigate();
  const [selected, setSelected] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  
  // Default to Cebu City center
  const [userLocation, setUserLocation] = useState<[number, number]>([10.3157, 123.8854]);
  const [locationLoaded, setLocationLoaded] = useState(false);

  useEffect(() => {
    // Attempt to get the user's actual physical location
    if ("geolocation" in navigator) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          setUserLocation([position.coords.latitude, position.coords.longitude]);
          setLocationLoaded(true);
        },
        (error) => {
          console.warn("Geolocation failed or denied. Defaulting to Cebu City center.", error);
          setLocationLoaded(true); // Still load the map, just using the default coordinates
        }
      );
    } else {
      setLocationLoaded(true);
    }
  }, []);

  const filtered = FACILITIES.filter(f =>
    f.name.toLowerCase().includes(search.toLowerCase()) ||
    f.address.toLowerCase().includes(search.toLowerCase())
  );

  const handleSelectFacility = (id: number) => {
    setSelected(selected === id ? null : id);
  };

  const selectedFacility = FACILITIES.find(f => f.id === selected);
  const mapCenter = selectedFacility 
    ? [selectedFacility.lat, selectedFacility.lng] as [number, number] 
    : userLocation;

  return (
    <div className="em-root">
      {/* ── Sidebar ── */}
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
            <input
              placeholder="Search facilities…"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
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
                  <a 
                    href={`https://www.google.com/maps/dir/?api=1&destination=${f.lat},${f.lng}`} 
                    target="_blank" 
                    rel="noopener noreferrer"
                    className="em-dir-btn"
                  >
                    Get directions via Google Maps
                  </a>
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
            zoom={14} 
            style={{ height: '100%', width: '100%' }}
            zoomControl={true}
          >
            {/* Base Map Tiles from OpenStreetMap */}
            <TileLayer
              attribution='© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            
            {/* Component to animate map movement */}
            <ChangeView center={mapCenter} zoom={selected ? 16 : 14} />

            {/* Marker for User's Current Location */}
            <Marker position={userLocation} icon={userIcon}>
              <Popup>You are here</Popup>
            </Marker>

            {/* Markers for Facilities */}
            {filtered.map(f => (
              <Marker 
                key={f.id} 
                position={[f.lat, f.lng]}
                eventHandlers={{
                  click: () => handleSelectFacility(f.id),
                }}
              >
                <Popup>
                  <strong>{f.name}</strong><br />
                  {f.address}<br />
                  {f.phone}
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        ) : (
          <div className="em-map-loading">
            Loading map and locating you...
          </div>
        )}
      </div>
    </div>
  );
};

export default EmergencyMap;