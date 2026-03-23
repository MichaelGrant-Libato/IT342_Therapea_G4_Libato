import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import "../styles/Dashboard.css";

interface UserData {
  userId: string;
  fullName: string;
  role: string;
}

const Dashboard: React.FC = () => {
  const [user, setUser] = useState<UserData | null>(null);
  const navigate = useNavigate();

  // ✅ Block back navigation
  useEffect(() => {
    window.history.pushState(null, '', window.location.href);
    const handlePopState = () => {
      window.history.pushState(null, '', window.location.href);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  // ✅ Auth check
  useEffect(() => {
    const syncAuth = async () => {
      const storedUser =
        localStorage.getItem('user') || sessionStorage.getItem('user');

      if (storedUser) {
        try {
          setUser(JSON.parse(storedUser));
        } catch {
          handleLogout();
        }
        return;
      }

      // No stored user — redirect to login
      navigate('/login', { replace: true });
    };

    syncAuth();
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('user');
    localStorage.removeItem('sessionStart');
    sessionStorage.removeItem('user');
    sessionStorage.removeItem('sessionStart');
    sessionStorage.removeItem('oauth_state');
    navigate('/login', { replace: true });
  };

  if (!user) {
    return (
      <div className="dashboard-container">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
          <div>Loading...</div>
        </div>
      </div>
    );
  }

  const initials = user.fullName
    ? user.fullName.split(' ').map(n => n[0]).join('').toUpperCase()
    : '?';

  return (
    <div className="dashboard-container">
      {/* ── Header ── */}
      <header className="dashboard-header">
        <div className="header-left">
          <div className="header-logo">
            <div className="header-logo-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="white">
                <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
              </svg>
            </div>
            <span className="header-logo-text">
              Thera<span>Pea</span>
            </span>
          </div>
        </div>

        <div className="header-right">
          <div className="notification-bell">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
            <div className="notification-dot"/>
          </div>

          <div className="user-menu">
            <div className="user-avatar">{initials}</div>
            <div className="user-info">
              <div className="user-name">{user.fullName}</div>
              <div className="user-role">{user.role}</div>
            </div>
          </div>

          {/* ✅ Logout button */}
          <button
            onClick={handleLogout}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              background: 'transparent',
              border: '1px solid #e2e8f0',
              borderRadius: '8px',
              padding: '6px 14px',
              cursor: 'pointer',
              color: '#64748b',
              fontSize: '14px',
              fontWeight: 500,
              transition: 'all 0.2s',
            }}
            onMouseEnter={e => {
              (e.currentTarget as HTMLButtonElement).style.background = '#fee2e2';
              (e.currentTarget as HTMLButtonElement).style.color = '#dc2626';
              (e.currentTarget as HTMLButtonElement).style.borderColor = '#fca5a5';
            }}
            onMouseLeave={e => {
              (e.currentTarget as HTMLButtonElement).style.background = 'transparent';
              (e.currentTarget as HTMLButtonElement).style.color = '#64748b';
              (e.currentTarget as HTMLButtonElement).style.borderColor = '#e2e8f0';
            }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            Logout
          </button>
        </div>
      </header>

      {/* ── Main ── */}
      <main className="dashboard-main">
        <div className="welcome-section">
          <h1 className="welcome-title">
            Welcome back, {user.fullName ? user.fullName.split(' ')[0] : 'User'}!
          </h1>
          <p className="welcome-subtitle">Here's an overview of your mental health journey</p>
        </div>

        {/* Stats Grid */}
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-header">
              <div className="stat-icon stat-icon-green">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M9 11l3 3L22 4"/>
                  <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
                </svg>
              </div>
              <span className="stat-change stat-change-positive">+12%</span>
            </div>
            <div className="stat-value">8</div>
            <div className="stat-label">Sessions Completed</div>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <div className="stat-icon stat-icon-purple">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4"/>
                </svg>
              </div>
              <span className="stat-change stat-change-positive">+5%</span>
            </div>
            <div className="stat-value">85%</div>
            <div className="stat-label">Wellness Score</div>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <div className="stat-icon stat-icon-red">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 9.81a19.79 19.79 0 01-3.07-8.68A2 2 0 012 .98h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L6.91 8.84a16 16 0 006.29 6.29l1.22-1.24a2 2 0 012.11-.45 12.84 12.84 0 002.81.7A2 2 0 0122 16.16v.76z"/>
                </svg>
              </div>
              <span className="stat-change stat-change-negative">-8%</span>
            </div>
            <div className="stat-value">3</div>
            <div className="stat-label">Upcoming Sessions</div>
          </div>

          <div className="stat-card">
            <div className="stat-header">
              <div className="stat-icon stat-icon-blue">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                  <path d="M23 21v-2a4 4 0 00-3-3.87"/>
                  <path d="M16 3.13a4 4 0 010 7.75"/>
                </svg>
              </div>
              <span className="stat-change stat-change-positive">+2</span>
            </div>
            <div className="stat-value">4</div>
            <div className="stat-label">Care Team</div>
          </div>
        </div>

        {/* Content Grid */}
        <div className="content-grid">
          {/* Upcoming Sessions */}
          <section className="sessions-section">
            <div className="section-header">
              <h2 className="section-title">Upcoming Sessions</h2>
              <a href="#" className="view-all-link">View All</a>
            </div>
            <div className="session-list">
              <div className="session-item">
                <div className="session-time">
                  <div className="session-date">TODAY</div>
                  <div className="session-hour">3:00 PM</div>
                </div>
                <div className="session-info">
                  <div className="session-provider">Dr. Sarah Johnson</div>
                  <div className="session-type">Individual Therapy Session</div>
                  <div className="session-tags">
                    <span className="session-tag tag-green">Anxiety</span>
                    <span className="session-tag tag-purple">Stress Management</span>
                  </div>
                </div>
                <div className="session-action">
                  <button className="join-button">Join Session</button>
                </div>
              </div>

              <div className="session-item">
                <div className="session-time">
                  <div className="session-date">FRI</div>
                  <div className="session-hour">2:00 PM</div>
                </div>
                <div className="session-info">
                  <div className="session-provider">Dr. Michael Chen</div>
                  <div className="session-type">Medication Review</div>
                  <div className="session-tags">
                    <span className="session-tag tag-purple">Psychiatry</span>
                  </div>
                </div>
                <div className="session-action">
                  <button className="join-button">Join Session</button>
                </div>
              </div>
            </div>
          </section>

          {/* Wellness Progress */}
          <section className="progress-section">
            <div className="section-header">
              <h2 className="section-title">Wellness Progress</h2>
              <a href="#" className="view-all-link">View Details</a>
            </div>
            <div className="progress-list">
              {[
                { label: 'Mood',    value: 72, cls: 'progress-mood'    },
                { label: 'Sleep',   value: 58, cls: 'progress-sleep'   },
                { label: 'Anxiety', value: 45, cls: 'progress-anxiety' },
                { label: 'Stress',  value: 38, cls: 'progress-stress'  },
              ].map(({ label, value, cls }) => (
                <div key={label} className="progress-item">
                  <div className="progress-header">
                    <span className="progress-label">{label}</span>
                    <span className={`progress-value ${cls}`}>{value}%</span>
                  </div>
                  <div className="progress-bar">
                    <div className={`progress-fill ${cls}`} style={{ width: `${value}%` }}/>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Quick Actions */}
        <section className="actions-section">
          <div className="section-header">
            <h2 className="section-title">Quick Actions</h2>
          </div>
          <div className="actions-grid">
            {[
              {
                title: 'Take Assessment', desc: 'Complete your weekly check-in',
                cls: 'action-icon-green',
                icon: <><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></>
              },
              {
                title: 'Book Session', desc: 'Schedule your next appointment',
                cls: 'action-icon-purple',
                icon: <><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></>
              },
              {
                title: 'Find Provider', desc: 'Browse our care team',
                cls: 'action-icon-green',
                icon: <><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></>
              },
              {
                title: 'View Resources', desc: 'Access self-help materials',
                cls: 'action-icon-purple',
                icon: <><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14,2 14,8 20,8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10,9 9,9 8,9"/></>
              },
            ].map(({ title, desc, cls, icon }) => (
              <button key={title} className="action-button">
                <div className={`action-icon ${cls}`}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    {icon}
                  </svg>
                </div>
                <div className="action-content">
                  <div className="action-title">{title}</div>
                  <div className="action-description">{desc}</div>
                </div>
              </button>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
};

export default Dashboard;