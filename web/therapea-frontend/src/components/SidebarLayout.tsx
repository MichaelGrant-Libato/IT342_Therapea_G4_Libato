import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import '../styles/Dashboard.css';

const NavIcon = ({ type }: { type: string }) => {
  const paths: Record<string, JSX.Element> = {
    home:      <><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></>,
    calendar:  <><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></>,
    clipboard: <><path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></>,
    chart:     <><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></>,
    settings:  <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></>,
    message:   <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>,
    logout:    <><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></>,
    user:      <><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></>,
    users:     <><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></>,
    bell:      <><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></>,
    map:       <><polygon points="1 6 1 22 8 18 16 22 23 18 23 2 16 6 8 2 1 6"/><line x1="8" y1="2" x2="8" y2="18"/><line x1="16" y1="6" x2="16" y2="22"/></>,
  };
  return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{paths[type]}</svg>;
};

export const SidebarLayout: React.FC<{ children: React.ReactNode; title?: string }> = ({ children, title = "Dashboard" }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [showLogoutModal, setShowLogoutModal] = useState(false);

  // Get user info directly from local storage for the sidebar
  const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
  const user = stored ? JSON.parse(stored) : null;
  const isDoctor = user?.role === 'DOCTOR';
  const initials = user?.fullName?.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase() || 'U';

  const getActiveId = (pathname: string) => {
    if (pathname.startsWith('/appointments')) return 'appointments';
    if (pathname.startsWith('/patients'))     return 'patients';
    if (pathname.startsWith('/therapists'))   return 'therapists';
    if (pathname.startsWith('/assessment'))   return 'assessments';
    if (pathname.startsWith('/progress'))     return 'progress';
    if (pathname.startsWith('/settings'))     return 'settings';
    if (pathname.startsWith('/messages'))     return 'messages';
    if (pathname.startsWith('/profile'))      return 'profile';
    if (pathname.startsWith('/emergency'))    return 'emergency';
    return 'dashboard';
  };

  const activeNav = getActiveId(location.pathname);

  const handleConfirmLogout = () => {
    ['user', 'sessionStart', 'oauth_state'].forEach(k => {
      localStorage.removeItem(k);
      sessionStorage.removeItem(k);
    });
    navigate('/login', { replace: true });
  };

  const navItems = [
    { id: 'dashboard',    label: 'Dashboard',         icon: 'home',      path: '/dashboard'    },
    { id: 'appointments', label: 'Appointments',      icon: 'calendar',  path: '/appointments' },
    ...(isDoctor
      ? [{ id: 'patients',   label: 'Patients',       icon: 'users',     path: '/patients'     }]
      : [
          { id: 'therapists', label: 'Find a Therapist', icon: 'search', path: '/therapists'   },
          { id: 'assessments',label: 'Assessments',   icon: 'clipboard', path: '/assessment'   },
        ]
    ),
    { id: 'progress',     label: 'Progress',          icon: 'chart',     path: '/progress'     },
    { id: 'messages',     label: 'Messages',          icon: 'message',   path: '/messages'     },
  ];

  return (
    <>
      <div className={`db-root ${showLogoutModal ? 'blurred' : ''}`}>
        <aside className="db-sidebar">
          <div className="db-sidebar-logo">
            <div className="db-logo-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="white">
                <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
              </svg>
            </div>
            <span className="db-logo-text">TheraPea</span>
          </div>
          <div className="db-nav-label">Menu</div>
          <nav className="db-nav">
            {navItems.map(item => (
              <button key={item.id} className={`db-nav-btn${activeNav === item.id ? ' active' : ''}`} onClick={() => navigate(item.path)}>
                <span className="db-nav-icon-wrap"><NavIcon type={item.icon} /></span>{item.label}
                {activeNav === item.id && <span className="db-nav-indicator" />}
              </button>
            ))}
          </nav>
          <div className="db-sidebar-bottom">
            <button className={`db-nav-btn emergency-btn${activeNav === 'emergency' ? ' active' : ''}`} onClick={() => navigate('/emergency')}>
              <span className="db-nav-icon-wrap emergency-icon"><NavIcon type="map" /></span>
              {isDoctor ? 'Emergency Resources' : 'Emergency Map'}
            </button>
            <div className="db-sidebar-divider" />
            <button className={`db-nav-btn${activeNav === 'settings' ? ' active' : ''}`} onClick={() => navigate('/settings')}>
              <span className="db-nav-icon-wrap"><NavIcon type="settings" /></span>Settings
            </button>
            <button className={`db-nav-btn${activeNav === 'profile' ? ' active' : ''}`} onClick={() => navigate('/profile')}>
              <span className="db-nav-icon-wrap"><NavIcon type="user" /></span>Profile
            </button>
            <button className="db-nav-btn db-logout-btn" onClick={() => setShowLogoutModal(true)}>
              <span className="db-nav-icon-wrap"><NavIcon type="logout" /></span>Sign out
            </button>
          </div>
        </aside>

        <div className="db-main">
          <header className="db-topbar">
            <div className="db-topbar-left"><span className="db-topbar-title">{title}</span></div>
            <div className="db-topbar-right">
              <button className="db-bell"><NavIcon type="bell" /><span className="db-bell-dot" /></button>
              
              {/* 🔴 NEW: Topbar Dynamic Avatar Logic */}
              <div 
                className={`db-avatar${isDoctor ? ' doctor' : ''}`} 
                onClick={() => navigate('/profile')}
                style={user?.profilePictureUrl ? { padding: 0, overflow: 'hidden' } : {}}
              >
                {user?.profilePictureUrl ? (
                  <img 
                    src={user.profilePictureUrl} 
                    alt="Profile" 
                    style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} 
                  />
                ) : (
                  initials
                )}
              </div>

            </div>
          </header>

          {/* THIS IS WHERE THE UNIQUE PAGE CONTENT GOES! */}
          <div className="db-content">
            {children}
          </div>
          
        </div>
      </div>

      {showLogoutModal && (
        <div className="db-modal-overlay" onClick={() => setShowLogoutModal(false)}>
          <div className="db-modal-card" onClick={e => e.stopPropagation()}>
            <div className="db-modal-icon"><NavIcon type="logout" /></div>
            <h2 className="db-modal-title">Sign out of TheraPea?</h2>
            <p className="db-modal-text">You will need to sign back in to access your appointments and messages.</p>
            <div className="db-modal-actions">
              <button className="db-btn-outline" onClick={() => setShowLogoutModal(false)}>Cancel</button>
              <button className="db-btn-danger" onClick={handleConfirmLogout}>Sign out</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};