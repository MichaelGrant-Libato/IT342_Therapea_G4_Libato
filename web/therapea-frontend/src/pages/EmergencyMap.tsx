import React from 'react';
import '../styles/EmergencyMap.css';

const EmergencyMap: React.FC = () => {
  return (
    <div className="map-page-container">
      {/* Sidebar */}
      <aside className="map-sidebar">
        <div className="sidebar-header">
          <h2>Nearby Psychiatric Wards</h2>
          <p>Emergency mental health facilities in your area</p>
        </div>

        <div className="sidebar-search">
          <div className="search-box-mock">
            <span className="icon">⊙</span>
            <hr />
            <span className="icon-right">⍟</span>
          </div>
        </div>

        <div className="sidebar-bottom">
          <div className="crisis-card">
            <div className="crisis-title">National Crisis Line</div>
            <div className="crisis-number">988</div>
            <div className="crisis-desc">Available 24/7 for immediate support</div>
          </div>
        </div>
      </aside>

      {/* Static Map Area */}
      <main className="map-area">
        <div className="map-legend">
          <div><span className="circle-legend"></span> Facility Location</div>
          <div><span className="line-legend"></span> Route</div>
        </div>
        
        {/* Mock Map Elements */}
        <svg className="map-svg" width="100%" height="100%">
          <polyline points="0,150 400,550 600,510 800,800" fill="none" stroke="#cbd5e1" strokeWidth="3" strokeDasharray="8 8" />
        </svg>
        
        <div className="map-node" style={{ top: '20%', left: '40%' }}></div>
        <div className="map-node" style={{ top: '48%', left: '55%' }}></div>
        <div className="map-node" style={{ top: '65%', left: '65%' }}></div>
        <div className="map-node" style={{ top: '70%', left: '25%' }}></div>

        <div className="map-tooltip">Map shows approximate locations</div>
      </main>
    </div>
  );
};

export default EmergencyMap;