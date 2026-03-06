import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

interface UserData {
  userId: string;
  fullName: string;
  role: string;
}

const assessments = [
  { date: 'Mar 12, 2024', type: 'PHQ-9 Depression', score: 12, level: 'Moderate', status: 'Reviewed' },
  { date: 'Mar 8, 2024', type: 'GAD-7 Anxiety', score: 7, level: 'Mild', status: 'Reviewed' },
  { date: 'Mar 1, 2024', type: 'Stress Assessment', score: 18, level: 'High', status: 'Pending' },
];

const riskColors: Record<string, { bg: string; color: string }> = {
  Moderate: { bg: '#FFFBEB', color: '#D97706' },
  Mild:     { bg: '#F0FDF4', color: '#10B981' },
  High:     { bg: '#FEF2F2', color: '#EF4444' },
};
const statusColors: Record<string, { bg: string; color: string }> = {
  Reviewed: { bg: '#F0FDF4', color: '#10B981' },
  Pending:  { bg: '#F3F4F6', color: '#6B7280' },
};

const navItems = [
  { icon: '🏠', label: 'Dashboard', active: true },
  { icon: '📅', label: 'Appointments', active: false },
  { icon: '📋', label: 'Assessments', active: false },
  { icon: '📈', label: 'Progress', active: false },
  { icon: '💬', label: 'Messages', active: false },
];

const Dashboard: React.FC = () => {
  const [user, setUser] = useState<UserData | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const storedUser = localStorage.getItem('user');
    if (storedUser) setUser(JSON.parse(storedUser));
    else navigate('/login');
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('user');
    navigate('/login');
  };

  if (!user) return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F9FAFB', fontFamily: "'Inter', sans-serif" }}>
      <p style={{ color: '#9CA3AF', fontSize: 14 }}>Loading…</p>
    </div>
  );

  const firstName = user.fullName.split(' ')[0];
  const initial = firstName[0].toUpperCase();

  return (
    <>
      <style>{`
        html, body, #root { margin: 0; padding: 0; width: 100%; min-height: 100vh; box-sizing: border-box; }
        *, *::before, *::after { box-sizing: border-box; }
        body { font-family: 'Inter', sans-serif; background: #F9FAFB; }
        a { text-decoration: none; }
        @media (min-width: 1024px) {
          .dash-sidebar { transform: translateX(0) !important; position: relative !important; }
          .dash-overlay { display: none !important; }
        }
      `}</style>

      <div style={{ minHeight: '100vh', width: '100vw', display: 'flex', fontFamily: "'Inter', sans-serif", background: '#F9FAFB' }}>

        {/* Mobile overlay */}
        {sidebarOpen && (
          <div
            className="dash-overlay"
            onClick={() => setSidebarOpen(false)}
            style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.3)', zIndex: 30 }}
          />
        )}

        {/* ── Sidebar ── */}
        <aside
          className="dash-sidebar"
          style={{
            position: 'fixed', top: 0, left: 0, bottom: 0, zIndex: 40,
            width: 240, background: '#ffffff', borderRight: '1px solid #F3F4F6',
            display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
            transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)',
            transition: 'transform 0.2s ease',
          }}
        >
          <div>
            {/* Logo */}
            <div style={{ padding: '20px 24px', display: 'flex', alignItems: 'center', gap: 10, borderBottom: '1px solid #F3F4F6', marginBottom: 8 }}>
              <div style={{ width: 32, height: 32, borderRadius: 8, background: 'linear-gradient(135deg,#2563EB,#7C3AED)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <span style={{ color: '#fff', fontWeight: 700, fontSize: 14 }}>T</span>
              </div>
              <span style={{ fontWeight: 700, color: '#111827', fontSize: 16 }}>TheraPea</span>
            </div>

            {/* Nav */}
            <nav style={{ padding: '4px 12px' }}>
              <p style={{ fontSize: 11, fontWeight: 600, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.08em', padding: '8px 12px', marginBottom: 4 }}>Main</p>
              {navItems.map((item) => (
                <a
                  key={item.label}
                  href="#"
                  style={{
                    display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px',
                    borderRadius: 10, marginBottom: 2, fontSize: 14, fontWeight: item.active ? 600 : 500,
                    color: item.active ? '#2563EB' : '#6B7280',
                    background: item.active ? '#EFF6FF' : 'transparent',
                    transition: 'background 0.1s',
                  }}
                  onMouseEnter={(e) => { if (!item.active) e.currentTarget.style.background = '#F9FAFB'; }}
                  onMouseLeave={(e) => { if (!item.active) e.currentTarget.style.background = 'transparent'; }}
                >
                  <span style={{ fontSize: 16 }}>{item.icon}</span>
                  {item.label}
                  {item.active && <div style={{ marginLeft: 'auto', width: 6, height: 6, borderRadius: '50%', background: '#2563EB' }} />}
                </a>
              ))}
            </nav>
          </div>

          {/* User + logout */}
          <div style={{ padding: '12px', borderTop: '1px solid #F3F4F6' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', borderRadius: 10, background: '#F9FAFB', marginBottom: 4 }}>
              <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'linear-gradient(135deg,#2563EB,#7C3AED)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, color: '#fff', fontWeight: 700, fontSize: 13 }}>
                {initial}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: 13, fontWeight: 600, color: '#111827', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user.fullName}</p>
                <p style={{ fontSize: 11, color: '#9CA3AF', textTransform: 'capitalize' }}>{user.role.toLowerCase()}</p>
              </div>
            </div>
            <button
              onClick={handleLogout}
              style={{
                display: 'flex', alignItems: 'center', gap: 8, width: '100%', padding: '10px 12px',
                borderRadius: 10, border: 'none', background: 'transparent', cursor: 'pointer',
                fontSize: 14, fontWeight: 500, color: '#6B7280', fontFamily: "'Inter', sans-serif",
                transition: 'background 0.1s, color 0.1s',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = '#FEF2F2'; e.currentTarget.style.color = '#EF4444'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#6B7280'; }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5-5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z" />
              </svg>
              Sign out
            </button>
          </div>
        </aside>

        {/* ── Main ── */}
        <main style={{ flex: 1, marginLeft: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }} className="dash-main">
          <style>{`@media(min-width:1024px){.dash-main{margin-left:240px !important;}}`}</style>

          {/* Top bar */}
          <header style={{ position: 'sticky', top: 0, zIndex: 20, background: '#ffffff', borderBottom: '1px solid #F3F4F6', padding: '0 24px', height: 64, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <button
                onClick={() => setSidebarOpen(true)}
                style={{ padding: 6, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', display: 'flex' }}
                className="hamburger-btn"
              >
                <style>{`@media(min-width:1024px){.hamburger-btn{display:none !important;}}`}</style>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <path d="M3 12h18M3 6h18M3 18h18" stroke="#374151" strokeWidth="2" strokeLinecap="round" />
                </svg>
              </button>
              <div>
                <h1 style={{ fontSize: 18, fontWeight: 700, color: '#111827', margin: 0 }}>Dashboard</h1>
                <p style={{ fontSize: 12, color: '#9CA3AF', margin: 0 }}>Friday, March 6, 2026</p>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <button style={{ position: 'relative', padding: 8, borderRadius: 10, border: 'none', background: 'transparent', cursor: 'pointer' }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="#9CA3AF">
                  <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5S10.5 3.17 10.5 4v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z" />
                </svg>
                <span style={{ position: 'absolute', top: 6, right: 6, width: 8, height: 8, borderRadius: '50%', background: '#EF4444', border: '1.5px solid #fff' }} />
              </button>
              <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'linear-gradient(135deg,#2563EB,#7C3AED)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 13 }}>
                {initial}
              </div>
            </div>
          </header>

          <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: 20 }}>

            {/* Welcome banner */}
            <div style={{ borderRadius: 20, padding: '28px 32px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', overflow: 'hidden', position: 'relative', background: 'linear-gradient(135deg,#1d4ed8,#2563EB 55%,#7C3AED)' }}>
              <div style={{ position: 'absolute', right: -32, top: -32, width: 160, height: 160, borderRadius: '50%', background: 'rgba(255,255,255,0.07)' }} />
              <div style={{ position: 'relative', zIndex: 1 }}>
                <p style={{ color: 'rgba(219,234,254,0.9)', fontSize: 13, fontWeight: 500, marginBottom: 4 }}>Good morning 👋</p>
                <h2 style={{ color: '#fff', fontSize: 22, fontWeight: 700, marginBottom: 4 }}>Welcome back, {firstName}</h2>
                <p style={{ color: 'rgba(191,219,254,0.8)', fontSize: 13 }}>Here's an overview of your mental health journey</p>
              </div>
              <div style={{ display: 'flex', gap: 32, position: 'relative', zIndex: 1 }}>
                {[{ label: 'Sessions', value: '12' }, { label: 'Streak', value: '7d' }, { label: 'Score', value: '72%' }].map((s) => (
                  <div key={s.label} style={{ textAlign: 'center' }}>
                    <p style={{ color: '#fff', fontWeight: 700, fontSize: 20, marginBottom: 2 }}>{s.value}</p>
                    <p style={{ color: 'rgba(191,219,254,0.75)', fontSize: 12 }}>{s.label}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Next session */}
            <div style={{ background: '#fff', borderRadius: 20, border: '1px solid #F3F4F6', padding: '24px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
                <h3 style={{ fontSize: 15, fontWeight: 700, color: '#111827' }}>Next Therapy Session</h3>
                <span style={{ fontSize: 12, fontWeight: 600, padding: '4px 10px', borderRadius: 20, background: '#DBEAFE', color: '#2563EB' }}>Upcoming</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                  <div style={{ width: 48, height: 48, borderRadius: 16, background: 'linear-gradient(135deg,#7C3AED,#2563EB)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 15, flexShrink: 0 }}>MJ</div>
                  <div>
                    <p style={{ fontSize: 14, fontWeight: 700, color: '#111827', marginBottom: 2 }}>Dr. Michael Johnson</p>
                    <p style={{ fontSize: 12, color: '#9CA3AF', marginBottom: 8 }}>Clinical Psychologist</p>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {['📅 March 15, 2024', '🕑 2:00 PM', '⏱ 50 min'].map((tag) => (
                        <span key={tag} style={{ fontSize: 12, color: '#6B7280', background: '#F9FAFB', padding: '4px 10px', borderRadius: 8 }}>{tag}</span>
                      ))}
                    </div>
                  </div>
                </div>
                <button
                  style={{
                    display: 'flex', alignItems: 'center', gap: 8, padding: '10px 20px', borderRadius: 12, border: 'none',
                    background: '#2563EB', color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                    boxShadow: '0 4px 14px rgba(37,99,235,0.3)', fontFamily: "'Inter', sans-serif",
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = '#1d4ed8'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = '#2563EB'; }}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="white">
                    <path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z" />
                  </svg>
                  Join Video Call
                </button>
              </div>
            </div>

            {/* Assessments table */}
            <div style={{ background: '#fff', borderRadius: 20, border: '1px solid #F3F4F6', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
              <div style={{ padding: '20px 24px', borderBottom: '1px solid #F3F4F6', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <h3 style={{ fontSize: 15, fontWeight: 700, color: '#111827' }}>Recent Triage Assessments</h3>
                <button style={{ fontSize: 12, fontWeight: 600, color: '#2563EB', background: '#EFF6FF', border: 'none', padding: '6px 12px', borderRadius: 8, cursor: 'pointer', fontFamily: "'Inter', sans-serif" }}>
                  View all
                </button>
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
                  <thead>
                    <tr style={{ background: '#F9FAFB' }}>
                      {['Date', 'Assessment Type', 'Risk Score', 'Status', ''].map((h) => (
                        <th key={h} style={{ padding: '12px 24px', textAlign: 'left', fontSize: 11, fontWeight: 600, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.06em', whiteSpace: 'nowrap' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {assessments.map((a, i) => (
                      <tr
                        key={i}
                        style={{ borderTop: '1px solid #F9FAFB' }}
                        onMouseEnter={(e) => { (e.currentTarget as HTMLTableRowElement).style.background = '#FAFAFA'; }}
                        onMouseLeave={(e) => { (e.currentTarget as HTMLTableRowElement).style.background = 'transparent'; }}
                      >
                        <td style={{ padding: '16px 24px', color: '#6B7280', whiteSpace: 'nowrap' }}>{a.date}</td>
                        <td style={{ padding: '16px 24px', fontWeight: 600, color: '#111827', whiteSpace: 'nowrap' }}>{a.type}</td>
                        <td style={{ padding: '16px 24px' }}>
                          <span style={{ fontSize: 12, fontWeight: 600, padding: '4px 10px', borderRadius: 20, background: riskColors[a.level].bg, color: riskColors[a.level].color, whiteSpace: 'nowrap' }}>
                            {a.level} ({a.score})
                          </span>
                        </td>
                        <td style={{ padding: '16px 24px' }}>
                          <span style={{ fontSize: 12, fontWeight: 600, padding: '4px 10px', borderRadius: 20, background: statusColors[a.status].bg, color: statusColors[a.status].color }}>
                            {a.status}
                          </span>
                        </td>
                        <td style={{ padding: '16px 24px' }}>
                          <button style={{ padding: 6, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer' }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = '#F3F4F6'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                          >
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="#9CA3AF">
                              <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z" />
                            </svg>
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

          </div>
        </main>
      </div>
    </>
  );
};

export default Dashboard;