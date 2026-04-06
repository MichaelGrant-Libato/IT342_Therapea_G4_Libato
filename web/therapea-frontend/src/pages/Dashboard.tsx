import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

// ── Types ────────────────────────────────────────────────

interface UserData {
  userId: string; email: string; fullName: string; role: string;
  emailVerified: boolean; profileCompleted: boolean;
  createdAt: string | null; lastLogin: string | null;
}

// [ASSESSMENT COMMENTED OUT]
// interface Assessment {
//   id: string; assessmentType: string; phq9Score: number; gad7Score: number;
//   clinicalScore: number; riskLevel: string; status: string; createdAt: string;
// }

// ── Helpers ──────────────────────────────────────────────

const fmt = (iso: string | null) => {
  if (!iso) return 'N/A';
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
};

// [ASSESSMENT COMMENTED OUT]
// const riskBadge = (level: string) => {
//   const map: Record<string, { bg: string; color: string }> = {
//     Low:      { bg: '#dcfce7', color: '#15803d' },
//     Mild:     { bg: '#fef9c3', color: '#854d0e' },
//     Moderate: { bg: '#ffedd5', color: '#9a3412' },
//     High:     { bg: '#fee2e2', color: '#991b1b' },
//   };
//   return map[level] || { bg: '#f1f5f9', color: '#475569' };
// };

// [ASSESSMENT COMMENTED OUT]
// const statusBadge = (s: string) =>
//   s === 'Reviewed'
//     ? { bg: '#dcfce7', color: '#15803d' }
//     : { bg: '#f1f5f9', color: '#64748b' };

// ── Sidebar Icon ─────────────────────────────────────────

const Icon = ({ type, size = 18 }: { type: string; size?: number }) => {
  const d: Record<string, JSX.Element> = {
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
    video:     <><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2"/></>,
    eye:       <><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></>,
    check:     <><polyline points="20 6 9 17 4 12"/></>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {d[type]}
    </svg>
  );
};

// ── Main Dashboard ────────────────────────────────────────

const Dashboard: React.FC = () => {
  const [user, setUser]             = useState<UserData | null>(null);
  // [ASSESSMENT COMMENTED OUT]
  // const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [isLoading, setIsLoading]   = useState(true);
  const [activeNav, setActiveNav]   = useState('dashboard');
  const navigate = useNavigate();

  // Block back navigation
  useEffect(() => {
    window.history.pushState(null, '', window.location.href);
    const h = () => window.history.pushState(null, '', window.location.href);
    window.addEventListener('popstate', h);
    return () => window.removeEventListener('popstate', h);
  }, []);

  // Load user + assessments
  useEffect(() => {
    const load = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) { navigate('/login', { replace: true }); return; }

      let parsed: any;
      try { parsed = JSON.parse(stored); } catch { doLogout(); return; }

      const email = parsed.email;
      if (!email) { doLogout(); return; }

      try {
        // [ASSESSMENT COMMENTED OUT]
        const profileRes = await fetch(`http://localhost:8083/api/dashboard/profile?email=${encodeURIComponent(email)}`);

        const profileData = await profileRes.json();
        if (profileData.success) {
          setUser(profileData as UserData);
          const u = { ...parsed, fullName: profileData.fullName, role: profileData.role };
          localStorage.getItem('user')
            ? localStorage.setItem('user', JSON.stringify(u))
            : sessionStorage.setItem('user', JSON.stringify(u));
        } else {
          setUser({ userId: parsed.userId||'', email: parsed.email||'', fullName: parsed.fullName||'',
            role: parsed.role||'PATIENT', emailVerified: true, profileCompleted: true,
            createdAt: null, lastLogin: null });
        }

        // [ASSESSMENT COMMENTED OUT]
        // const assessData = await assessRes.json();
        // if (assessData.success) setAssessments(assessData.assessments);

      } catch {
        setUser({ userId: parsed.userId||'', email: parsed.email||'', fullName: parsed.fullName||'',
          role: parsed.role||'PATIENT', emailVerified: true, profileCompleted: true,
          createdAt: null, lastLogin: null });
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, [navigate]);

  const doLogout = () => {
    ['user','sessionStart','oauth_state'].forEach(k => {
      localStorage.removeItem(k); sessionStorage.removeItem(k);
    });
    navigate('/login', { replace: true });
  };

  if (isLoading) return (
    <div style={{ display:'flex', alignItems:'center', justifyContent:'center',
      height:'100vh', background:'#f0f4f8', flexDirection:'column', gap:'12px' }}>
      <div style={{ width:36, height:36, border:'3px solid #e2e8f0',
        borderTop:'3px solid #1e293b', borderRadius:'50%', animation:'spin .8s linear infinite' }}/>
      <p style={{ color:'#64748b', fontSize:14 }}>Loading…</p>
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  );

  if (!user) return null;

  const isDoctor  = user.role === 'DOCTOR';
  const firstName = user.fullName?.split(' ')[0] || 'User';
  const initials  = user.fullName?.split(' ').map((n:string) => n[0]).join('').toUpperCase() || '?';

  const navItems = [
    { id:'dashboard',    label:'Dashboard',    icon:'home',      path:'/dashboard'    },
    { id:'appointments', label:'Appointments', icon:'calendar',  path:'/appointments' },
    ...(isDoctor ? [{ id:'patients', label:'Patients', icon:'users', path:'/patients' }] : []),
    
    // [ASSESSMENT COMMENTED OUT]
    // { id:'assessments',  label:'Assessments',  icon:'clipboard', path:'/assessment'   },
    
    { id:'progress',     label:'Progress',     icon:'chart',     path:'/progress'     },
    { id:'settings',     label:'Settings',     icon:'settings',  path:'/settings'     },
    { id:'messages',     label:'Messages',     icon:'message',   path:'/messages'     },
  ];

  const navBtn = (active: boolean): React.CSSProperties => ({
    display:'flex', alignItems:'center', gap:10, padding:'9px 14px',
    cursor:'pointer', fontSize:13, fontWeight: active ? 600 : 400,
    color: active ? '#1e293b' : '#64748b',
    background: active ? '#f1f5f9' : 'transparent',
    border:'none', borderRadius:6, width:'calc(100% - 16px)', margin:'1px 8px',
    transition:'all .15s', textAlign:'left' as const,
  });

  return (
    <div style={{ display:'flex', minHeight:'100vh', fontFamily:'system-ui,-apple-system,sans-serif' }}>

      {/* ── Sidebar ── */}
      <aside style={{ width:200, minHeight:'100vh', background:'#fff',
        borderRight:'1px solid #e2e8f0', display:'flex', flexDirection:'column',
        position:'fixed', left:0, top:0, bottom:0, zIndex:100 }}>

        {/* Logo */}
        <div style={{ padding:'18px 16px 14px', display:'flex', alignItems:'center',
          gap:10, borderBottom:'1px solid #f1f5f9' }}>
          <div style={{ width:32, height:32, background:'#1e293b', borderRadius:6,
            display:'flex', alignItems:'center', justifyContent:'center' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="white">
              <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
            </svg>
          </div>
          <span style={{ fontWeight:700, fontSize:15, color:'#1e293b' }}>TheraPea</span>
        </div>

        {/* Nav */}
        <nav style={{ flex:1, padding:'10px 0', overflowY:'auto' }}>
          {navItems.map(item => (
            <button key={item.id} style={navBtn(activeNav === item.id)}
              onClick={() => {
                setActiveNav(item.id);
                if (item.path !== '/dashboard') navigate(item.path);
              }}
              onMouseEnter={e => { if (activeNav !== item.id) (e.currentTarget as HTMLButtonElement).style.background = '#f8fafc'; }}
              onMouseLeave={e => { if (activeNav !== item.id) (e.currentTarget as HTMLButtonElement).style.background = 'transparent'; }}>
              <Icon type={item.icon} size={16} />
              {item.label}
            </button>
          ))}
        </nav>

        {/* Bottom */}
        <div style={{ borderTop:'1px solid #f1f5f9', padding:'8px 0' }}>
          <button style={navBtn(false)} onClick={doLogout}>
            <Icon type="logout" size={16} /> Sign Out
          </button>
          <button style={navBtn(activeNav === 'profile')}
            onClick={() => setActiveNav('profile')}>
            <Icon type="user" size={16} /> Profile
          </button>
        </div>
      </aside>

      {/* ── Main ── */}
      <div style={{ marginLeft:200, flex:1, background:'#f0f4f8', minHeight:'100vh',
        display:'flex', flexDirection:'column' }}>

        {/* Header */}
        <header style={{ background:'#fff', borderBottom:'1px solid #e2e8f0', padding:'0 24px',
          height:54, display:'flex', alignItems:'center', justifyContent:'space-between',
          position:'sticky', top:0, zIndex:50 }}>
          <h1 style={{ margin:0, fontSize:17, fontWeight:700, color:'#1e293b' }}>
            {activeNav.charAt(0).toUpperCase() + activeNav.slice(1)}
          </h1>
          <div style={{ display:'flex', alignItems:'center', gap:16 }}>
            <div style={{ position:'relative', cursor:'pointer', color:'#64748b' }}>
              <Icon type="bell" size={20} />
              <div style={{ position:'absolute', top:-2, right:-2, width:8, height:8,
                background:'#ef4444', borderRadius:'50%', border:'2px solid #fff' }}/>
            </div>
            <div style={{ width:34, height:34, borderRadius:'50%',
              background: isDoctor ? 'linear-gradient(135deg,#2563EB,#7C3AED)' : 'linear-gradient(135deg,#6b8f6e,#4a7c59)',
              display:'flex', alignItems:'center', justifyContent:'center',
              color:'#fff', fontWeight:700, fontSize:13, cursor:'pointer' }}>
              {initials}
            </div>
          </div>
        </header>

        {/* Content */}
        <main style={{ padding:24, flex:1 }}>

          {/* Welcome */}
          <div style={{ background:'#fff', borderRadius:10, padding:'18px 22px',
            marginBottom:18, border:'1px solid #e2e8f0' }}>
            <h2 style={{ margin:'0 0 4px', fontSize:20, fontWeight:700, color:'#1e293b' }}>
              Welcome back, {isDoctor ? `Dr. ${firstName}` : firstName}
            </h2>
            <p style={{ margin:0, fontSize:13, color:'#64748b' }}>
              {isDoctor ? "Here's your practice overview for today" : "Here's an overview of your mental health journey"}
            </p>
          </div>

          {isDoctor
            ? <DoctorView /* [ASSESSMENT COMMENTED OUT] assessments={assessments} */ />
            : <PatientView /* [ASSESSMENT COMMENTED OUT] assessments={assessments} */ navigate={navigate}
                user={{ fullName: user.fullName, email: user.email, createdAt: user.createdAt }} />
          }
        </main>
      </div>
    </div>
  );
};

// ── Patient View ─────────────────────────────────────────

const PatientView: React.FC<{
  // [ASSESSMENT COMMENTED OUT]
  // assessments: Assessment[];
  navigate: any;
  user: { fullName: string; email: string; createdAt: string | null };
}> = ({ /* assessments, */ navigate, user }) => (
  <>
    {/* Next Therapy Session */}
    <div style={{ background:'#fff', borderRadius:10, padding:'18px 22px',
      marginBottom:18, border:'1px solid #e2e8f0' }}>
      <h3 style={{ margin:'0 0 14px', fontSize:14, fontWeight:700, color:'#1e293b' }}>
        Next Therapy Session
      </h3>
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', flexWrap:'wrap', gap:12 }}>
        <div style={{ display:'flex', alignItems:'center', gap:14 }}>
          <div style={{ width:46, height:46, borderRadius:'50%', background:'#e2e8f0',
            display:'flex', alignItems:'center', justifyContent:'center', color:'#94a3b8' }}>
            <Icon type="user" size={22} />
          </div>
          <div>
            <div style={{ fontWeight:600, fontSize:15, color:'#1e293b' }}>Dr. Michael Johnson</div>
            <div style={{ fontSize:13, color:'#64748b' }}>Clinical Psychologist</div>
            <div style={{ display:'flex', alignItems:'center', gap:5, marginTop:4, color:'#94a3b8' }}>
              <Icon type="calendar" size={12} />
              <span style={{ fontSize:12 }}>March 15, 2024 at 2:00 PM</span>
            </div>
          </div>
        </div>
        <button style={{ display:'flex', alignItems:'center', gap:7, background:'#1e293b',
          color:'#fff', border:'none', borderRadius:8, padding:'10px 18px',
          fontSize:13, fontWeight:600, cursor:'pointer' }}>
          <Icon type="video" size={14} /> Join Video Call
        </button>
      </div>
    </div>

    {/* [ASSESSMENT COMMENTED OUT] */}
    {/* Recent Assessments */}
    {/* <div style={{ background:'#fff', borderRadius:10, padding:'18px 22px', border:'1px solid #e2e8f0' }}>
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:16 }}>
        <h3 style={{ margin:0, fontSize:14, fontWeight:700, color:'#1e293b' }}>
          Recent Triage Assessments
        </h3>
        <button onClick={() => navigate('/assessment')}
          style={{ background:'#1e293b', color:'#fff', border:'none', borderRadius:6,
            padding:'7px 14px', fontSize:12, fontWeight:600, cursor:'pointer',
            display:'flex', alignItems:'center', gap:5 }}>
          + New Assessment
        </button>
      </div>

      {assessments.length === 0 ? (
        <div style={{ textAlign:'center', padding:'48px 0', color:'#94a3b8' }}>
          <div style={{ margin:'0 auto 12px', color:'#cbd5e1' }}>
            <Icon type="clipboard" size={40} />
          </div>
          <p style={{ fontSize:14, margin:'0 0 16px', fontWeight:500 }}>No assessments taken yet</p>
          <p style={{ fontSize:13, margin:'0 0 16px', color:'#94a3b8' }}>
            Take your first Smart Triage Assessment to understand your mental health
          </p>
          <button onClick={() => navigate('/assessment')}
            style={{ background:'#1e293b', color:'#fff', border:'none', borderRadius:8,
              padding:'10px 20px', fontSize:13, fontWeight:600, cursor:'pointer' }}>
            Take Assessment
          </button>
        </div>
      ) : (
        <div style={{ overflowX:'auto' }}>
          <table style={{ width:'100%', borderCollapse:'collapse' }}>
            <thead>
              <tr>
                {['Date','Assessment Type','Risk Score','Status','Actions'].map(h => (
                  <th key={h} style={{ padding:'10px 14px', textAlign:'left', fontSize:11,
                    fontWeight:700, color:'#94a3b8', textTransform:'uppercase', letterSpacing:'0.5px',
                    borderBottom:'1px solid #f1f5f9' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {assessments.map(a => {
                const rb = riskBadge(a.riskLevel);
                const sb = statusBadge(a.status);
                return (
                  <tr key={a.id}
                    onMouseEnter={e => (e.currentTarget as HTMLTableRowElement).style.background = '#fafbfc'}
                    onMouseLeave={e => (e.currentTarget as HTMLTableRowElement).style.background = 'transparent'}>
                    <td style={{ padding:'13px 14px', fontSize:13, color:'#475569', borderBottom:'1px solid #f8fafc' }}>
                      {a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-US', { month:'long', day:'numeric', year:'numeric' }) : 'N/A'}
                    </td>
                    <td style={{ padding:'13px 14px', fontSize:13, color:'#1e293b', fontWeight:500, borderBottom:'1px solid #f8fafc' }}>
                      {a.assessmentType}
                    </td>
                    <td style={{ padding:'13px 14px', borderBottom:'1px solid #f8fafc' }}>
                      <span style={{ background:rb.bg, color:rb.color, padding:'3px 10px',
                        borderRadius:12, fontSize:12, fontWeight:600 }}>
                        {a.riskLevel} ({a.clinicalScore})
                      </span>
                    </td>
                    <td style={{ padding:'13px 14px', borderBottom:'1px solid #f8fafc' }}>
                      <span style={{ background:sb.bg, color:sb.color, padding:'3px 10px',
                        borderRadius:12, fontSize:12, fontWeight:600 }}>
                        {a.status}
                      </span>
                    </td>
                    <td style={{ padding:'13px 14px', borderBottom:'1px solid #f8fafc' }}>
                      <button style={{ background:'none', border:'none', cursor:'pointer',
                        color:'#94a3b8', padding:4, borderRadius:4 }} title="View">
                        <Icon type="eye" size={15} />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
    */}
  </>
);

// ── Doctor View ──────────────────────────────────────────

const DoctorView: React.FC<{ /* [ASSESSMENT COMMENTED OUT] assessments: Assessment[] */ }> = (/* { assessments } */) => {
  // [ASSESSMENT COMMENTED OUT]
  // const pending   = assessments.filter(a => a.status === 'Pending');
  // const reviewed  = assessments.filter(a => a.status === 'Reviewed');

  const statCards = [
    { label: "Today's Appointments", value: '8',  color: '#dbeafe', text: '#1e40af' },
    { label: 'Total Patients',       value: '124', color: '#f3e8ff', text: '#7e22ce' },
    // [ASSESSMENT COMMENTED OUT] Set pending to 0 for the stat card while feature is hidden
    { label: 'Pending Reviews',      value: '0', color: '#fef9c3', text: '#854d0e' },
    { label: 'Average Rating',       value: '4.9', color: '#dcfce7', text: '#15803d' },
  ];

  return (
    <>
      {/* Stat cards */}
      <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(180px,1fr))',
        gap:14, marginBottom:18 }}>
        {statCards.map(s => (
          <div key={s.label} style={{ background:'#fff', borderRadius:10, padding:'16px 20px',
            border:'1px solid #e2e8f0' }}>
            <div style={{ fontSize:28, fontWeight:800, color:'#1e293b', marginBottom:4 }}>{s.value}</div>
            <div style={{ fontSize:12, color:'#64748b' }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* [ASSESSMENT COMMENTED OUT] */}
      {/* Pending reviews */}
      {/*
      <div style={{ background:'#fff', borderRadius:10, padding:'18px 22px', border:'1px solid #e2e8f0' }}>
        <h3 style={{ margin:'0 0 16px', fontSize:14, fontWeight:700, color:'#1e293b' }}>
          Pending Assessment Reviews
          {pending.length > 0 && (
            <span style={{ marginLeft:8, background:'#fee2e2', color:'#991b1b',
              fontSize:11, fontWeight:700, padding:'2px 8px', borderRadius:10 }}>
              {pending.length}
            </span>
          )}
        </h3>
        {pending.length === 0 ? (
          <div style={{ textAlign:'center', padding:'32px', color:'#94a3b8', fontSize:13 }}>
            <Icon type="check" size={32} />
            <p style={{ marginTop:8 }}>All assessments reviewed — great work!</p>
          </div>
        ) : (
          <table style={{ width:'100%', borderCollapse:'collapse' }}>
            <thead>
              <tr>
                {['Date','Assessment Type','Risk Score','Clinical Score','Action'].map(h => (
                  <th key={h} style={{ padding:'10px 14px', textAlign:'left', fontSize:11,
                    fontWeight:700, color:'#94a3b8', textTransform:'uppercase',
                    borderBottom:'1px solid #f1f5f9' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pending.map(a => {
                const rb = riskBadge(a.riskLevel);
                return (
                  <tr key={a.id}>
                    <td style={{ padding:'13px 14px', fontSize:13, color:'#475569', borderBottom:'1px solid #f8fafc' }}>
                      {a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-US', { month:'short', day:'numeric', year:'numeric' }) : 'N/A'}
                    </td>
                    <td style={{ padding:'13px 14px', fontSize:13, color:'#1e293b', fontWeight:500, borderBottom:'1px solid #f8fafc' }}>
                      {a.assessmentType}
                    </td>
                    <td style={{ padding:'13px 14px', borderBottom:'1px solid #f8fafc' }}>
                      <span style={{ background:rb.bg, color:rb.color, padding:'3px 10px', borderRadius:12, fontSize:12, fontWeight:600 }}>
                        {a.riskLevel}
                      </span>
                    </td>
                    <td style={{ padding:'13px 14px', fontSize:13, color:'#1e293b', fontWeight:600, borderBottom:'1px solid #f8fafc' }}>
                      {a.clinicalScore}/100
                    </td>
                    <td style={{ padding:'13px 14px', borderBottom:'1px solid #f8fafc' }}>
                      <button
                        onClick={async () => {
                          await fetch(`http://localhost:8083/api/assessments/${a.id}/review`, { method:'PATCH' });
                          window.location.reload();
                        }}
                        style={{ background:'#1e293b', color:'#fff', border:'none', borderRadius:6,
                          padding:'5px 12px', fontSize:12, fontWeight:600, cursor:'pointer' }}>
                        Mark Reviewed
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
      */}
    </>
  );
};

export default Dashboard;