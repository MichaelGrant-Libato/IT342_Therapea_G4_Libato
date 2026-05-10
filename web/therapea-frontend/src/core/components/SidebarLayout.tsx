import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import "../../features/dashboard/Dashboard.css";

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
    search:    <><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></>,
    clock:     <><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></>
  };
  return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{paths[type]}</svg>;
};

const timeAgo = (dateStr: string) => {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins <= 0 ? 'Just now' : mins + 'm ago'}`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
};

export const SidebarLayout: React.FC<{ children: React.ReactNode; title?: string }> = ({ children, title = "Dashboard" }) => {
  const navigate = useNavigate();
  const location = useLocation();
  
  const [showLogoutModal, setShowLogoutModal] = useState(false);
  const [hasUnreadMessages, setHasUnreadMessages] = useState(false);
  const [notifications, setNotifications] = useState<any[]>([]);
  const [showNotifs, setShowNotifs] = useState(false);
  const notifRef = useRef<HTMLDivElement>(null);

  const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
  const user = stored ? JSON.parse(stored) : null;
  const isDoctor = user?.role === 'DOCTOR';
  const initials = user?.fullName?.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase() || 'U';

  // 🔴 NEW: GLOBAL THEME INITIALIZER 🔴
  // Every time a page loads, this ensures Dark Theme is applied if saved in settings.
  useEffect(() => {
    if (user?.email) {
      const savedSettings = localStorage.getItem(`settings_${user.email}`);
      if (savedSettings) {
        const { theme } = JSON.parse(savedSettings);
        if (theme === 'Dark') {
          document.documentElement.setAttribute('data-theme', 'dark');
        } else {
          document.documentElement.removeAttribute('data-theme');
        }
      }
    }
  }, [user?.email]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(event.target as Node)) {
        setShowNotifs(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (!user?.email) return;

    const pollGlobalData = async () => {
      try {
        let contacts: { email: string }[] = [];
        const contactRes = isDoctor 
          ? await fetch(`http://localhost:8083/api/patients/doctor?email=${encodeURIComponent(user.email)}`)
          : await fetch(`http://localhost:8083/api/appointments/user?email=${encodeURIComponent(user.email)}`);
        
        if (contactRes.ok) {
          const data = await contactRes.json();
          if (isDoctor && data.success) contacts = data.patients;
          else if (!isDoctor && data.success) {
            const uniqueDocs = new Set<string>();
            data.appointments.forEach((apt: any) => { if (apt.providerEmail) uniqueDocs.add(apt.providerEmail); });
            contacts = Array.from(uniqueDocs).map(email => ({ email }));
          }
        }

        let globalUnreadMsg = false;
        await Promise.all(contacts.map(async (contact) => {
          if (!contact.email) return;
          const msgRes = await fetch(`http://localhost:8083/api/messages?user1=${encodeURIComponent(user.email)}&user2=${encodeURIComponent(contact.email)}`);
          if (msgRes.ok) {
            const data = await msgRes.json();
            if (data.success && data.messages.length > 0) {
              const lastMsg = data.messages[data.messages.length - 1];
              if (lastMsg.senderEmail === contact.email) {
                const lastRead = localStorage.getItem(`last_read_${contact.email}`);
                if (!lastRead || new Date(lastMsg.timestamp) > new Date(lastRead)) globalUnreadMsg = true;
              }
            }
          }
        }));
        setHasUnreadMessages(globalUnreadMsg);

        const notifRes = await fetch(`http://localhost:8083/api/notifications?email=${encodeURIComponent(user.email)}`);
        if (notifRes.status === 403) {
            setNotifications([]);
            return;
        }
        if (notifRes.ok) {
          const notifData = await notifRes.json();
          if (notifData.success) setNotifications(notifData.notifications || []);
        }
      } catch (error) {}
    };

    pollGlobalData();
    const intervalId = setInterval(pollGlobalData, 8000); 
    return () => clearInterval(intervalId);
  }, [user?.email, user?.role, isDoctor]);

  const unreadNotifsCount = notifications.filter(n => !n.read).length;

  const handleMarkAsRead = async (id: number) => {
    try {
      const res = await fetch(`http://localhost:8083/api/notifications/${id}/read`, { method: 'PATCH' });
      if (res.ok) setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
    } catch (err) { console.error(err); }
  };

  const handleMarkAllAsRead = async () => {
    try {
      const res = await fetch(`http://localhost:8083/api/notifications/read-all?email=${encodeURIComponent(user.email)}`, { method: 'PATCH' });
      if (res.ok) setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    } catch (err) { console.error(err); }
  };

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
    // Remove dark theme on logout
    document.documentElement.removeAttribute('data-theme');
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
                <span className="db-nav-icon-wrap"><NavIcon type={item.icon} /></span>
                {item.label}
                {item.id === 'messages' && hasUnreadMessages && (
                  <span style={{ width: 8, height: 8, backgroundColor: '#ef4444', borderRadius: '50%', marginLeft: 'auto', marginRight: '14px' }} />
                )}
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
              
              <div className="db-notif-wrapper" ref={notifRef}>
                <button className="db-bell" onClick={() => setShowNotifs(!showNotifs)}>
                  <NavIcon type="bell" />
                  {unreadNotifsCount > 0 && <span className="db-bell-badge">{unreadNotifsCount}</span>}
                </button>
                
                {showNotifs && (
                  <div className="db-notif-dropdown">
                    <div className="db-notif-header">
                      {/* 🔴 FIXED: Using var(--text-main) so it adapts to dark mode automatically */}
                      <h3 style={{ margin: 0, fontSize: '15px', color: 'var(--text-main)' }}>Notifications</h3>
                      {unreadNotifsCount > 0 && (
                        <button onClick={handleMarkAllAsRead} className="db-notif-read-all">Mark all as read</button>
                      )}
                    </div>
                    <div className="db-notif-body">
                      {notifications.length === 0 ? (
                        <div className="db-notif-empty" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '48px 20px', textAlign: 'center' }}>
                          <div style={{ width: '48px', height: '48px', background: 'var(--bg)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', marginBottom: '16px' }}>
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                            </svg>
                          </div>
                          <p style={{ margin: 0, color: 'var(--text-sub)', fontSize: '15px', fontWeight: 500 }}>You're all caught up!</p>
                          <span style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>No new notifications</span>
                        </div>
                      ) : (
                        notifications.map(notif => (
                          <div 
                            key={notif.id} 
                            className={`db-notif-item ${!notif.read ? 'unread' : ''}`}
                            onClick={() => {
                               handleMarkAsRead(notif.id);
                               navigate('/appointments'); 
                               setShowNotifs(false);
                            }}
                          >
                            <div className="db-notif-icon">
                              <NavIcon type={notif.type === 'BOOKING' ? 'calendar' : 'clock'} />
                            </div>
                            <div className="db-notif-content">
                              <h4 className="db-notif-title">{notif.title}</h4>
                              <p className="db-notif-msg">{notif.message}</p>
                              <span className="db-notif-time">{timeAgo(notif.createdAt)}</span>
                            </div>
                            {!notif.read && <div className="db-notif-dot" />}
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </div>
              
              <div 
                className={`db-avatar${isDoctor ? ' doctor' : ''}`} 
                onClick={() => navigate('/profile')}
                style={user?.profilePictureUrl ? { padding: 0, overflow: 'hidden' } : {}}
              >
                {user?.profilePictureUrl ? (
                  <img src={user.profilePictureUrl} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
                ) : initials}
              </div>

            </div>
          </header>

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