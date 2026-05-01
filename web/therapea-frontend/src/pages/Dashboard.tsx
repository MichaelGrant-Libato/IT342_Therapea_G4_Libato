import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import '../styles/Dashboard.css';

// ─── Interfaces ───
interface UserData {
  userId: string; email: string; fullName: string; role: string;
  emailVerified: boolean; profileCompleted: boolean;
  createdAt: string | null; lastLogin: string | null;
}

interface Assessment {
  id: string; assessmentType: string; phq9Score: number; gad7Score: number;
  clinicalScore: number; riskLevel: string; status: string; createdAt: string;
  patientName?: string;
}

interface Appointment {
  id: string | number;
  providerId?: string;
  providerName?: string; 
  providerRole?: string;
  patientId?: string;
  patientName?: string;  
  date: string;
  time: string;
  type: string;          
  status: string;        
}

interface PatientRecord {
  id: string;
  name: string;
  status: string;        
  lastSession: string;
}

// ─── Helper Styles ───
const riskStyle = (level: string): React.CSSProperties => {
  const map: Record<string, { background: string; color: string; border: string }> = {
    Low:      { background: '#ECFDF5', color: '#065F46', border: '#A7F3D0' },
    Mild:     { background: '#FFFBEB', color: '#92400E', border: '#FDE68A' },
    Moderate: { background: '#FFF7ED', color: '#9A3412', border: '#FED7AA' },
    High:     { background: '#FEF2F2', color: '#991B1B', border: '#FECACA' },
  };
  const s = map[level] || { background: '#F8FAFC', color: '#475569', border: '#E2E8F0' };
  return { background: s.background, color: s.color, border: `1px solid ${s.border}` };
};

const statusStyle = (s: string): React.CSSProperties =>
  s === 'Reviewed'
    ? { background: '#ECFDF5', color: '#065F46', border: '1px solid #A7F3D0' }
    : { background: '#F8FAFC', color: '#6B7280', border: '1px solid #E5E7EB' };

const NavIcon = ({ type }: { type: string }) => {
  const paths: Record<string, JSX.Element> = {
    calendar:  <><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></>,
    clipboard: <><path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></>,
    users:     <><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></>,
    bell:      <><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></>,
    trash:     <><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></>,
    video:     <><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2"/></>,
    check:     <><polyline points="20 6 9 17 4 12"/></>,
  };
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {paths[type]}
    </svg>
  );
};

// ─── Main Dashboard Component ───
const Dashboard: React.FC = () => {
  const [user, setUser] = useState<UserData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [patientsList, setPatientsList] = useState<PatientRecord[]>([]);
  const [assessmentToDelete, setAssessmentToDelete] = useState<string | null>(null);
  
  const navigate  = useNavigate();

  useEffect(() => {
    const load = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) { navigate('/login', { replace: true }); return; }

      let parsed: any;
      try { parsed = JSON.parse(stored); } catch { navigate('/login'); return; }

      const email = parsed.email;
      if (!email) { navigate('/login'); return; }

      let role = parsed.role || 'PATIENT';

      // 1. Dynamic Profile Fetch
      try {
        const pRes = await fetch(`http://localhost:8083/api/dashboard/profile?email=${encodeURIComponent(email)}`);
        const pData = await pRes.json();
        if (pData.success) {
          setUser(pData as UserData);
          role = pData.role;
        } else {
          setUser({ ...parsed, role });
        }
      } catch {
        setUser({ ...parsed, role });
      }

      // 2. Dynamic Assessments Fetch (SECURE DOCTOR QUEUE LOGIC)
      try {
        const endpoint = role === 'DOCTOR' 
          ? `http://localhost:8083/api/assessments/doctor-queue?doctorEmail=${encodeURIComponent(email)}` 
          : `http://localhost:8083/api/assessments/user?email=${encodeURIComponent(email)}`;

        const aRes = await fetch(endpoint);
        const aData = await aRes.json();
        if (aData.success) {
          const sorted = aData.assessments.sort((a: Assessment, b: Assessment) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
          setAssessments(sorted);
        } else {
          setAssessments([]);
        }
      } catch (err) {
        console.error("Failed to load assessments:", err);
        setAssessments([]);
      }

      // 3. Dynamic Appointments Fetch
      try {
        const aptRes = await fetch(`http://localhost:8083/api/appointments/user?email=${encodeURIComponent(email)}`);
        const aptData = await aptRes.json();
        if (aptData.success) {
          setAppointments(aptData.appointments);
        } else {
          setAppointments([]);
        }
      } catch (err) {
        console.error("Failed to load appointments:", err);
        setAppointments([]);
      }

      // 4. Dynamic Patient Roster Fetch (Doctor Only)
      if (role === 'DOCTOR') {
        try {
          const patRes = await fetch(`http://localhost:8083/api/patients/doctor?email=${encodeURIComponent(email)}`);
          const patData = await patRes.json();
          if (patData.success) {
            setPatientsList(patData.patients);
          } else {
            setPatientsList([]);
          }
        } catch (err) {
          console.error("Failed to load patient roster:", err);
          setPatientsList([]);
        }
      }

      setIsLoading(false);
    };
    load();
  }, [navigate]);

  const handleConfirmDelete = async () => {
    if (!assessmentToDelete) return;
    try {
      await fetch(`http://localhost:8083/api/assessments/${assessmentToDelete}`, { method: 'DELETE' });
    } catch (error) {
      console.error("Failed to delete from API", error);
    }
    setAssessments(prev => prev.filter(a => a.id !== assessmentToDelete));
    if (user && user.email) {
      const localHistory = JSON.parse(localStorage.getItem(`assessments_${user.email}`) || '[]');
      const filtered = localHistory.filter((a: any) => a.id !== assessmentToDelete);
      localStorage.setItem(`assessments_${user.email}`, JSON.stringify(filtered));
    }
    setAssessmentToDelete(null);
  };

  if (isLoading) return (
    <SidebarLayout title="Dashboard">
      <div className="db-loading" style={{ height: '50vh', background: 'transparent' }}>
        <div className="db-spinner-wrap"><div className="db-spinner" /><div className="db-spinner-ring" /></div>
        <p>Loading your dashboard…</p>
      </div>
    </SidebarLayout>
  );

  if (!user) return null;

  const isDoctor  = user.role === 'DOCTOR';
  const firstName = user.fullName?.split(' ')[0] || 'there';

  return (
    <SidebarLayout title="Dashboard">
      <div className="db-welcome">
        <div className="db-welcome-text">
          <h2>Good to see you, {isDoctor ? `Dr. ${firstName}` : firstName}</h2>
          <p>{isDoctor ? 'Here is a summary of your practice activity today.' : 'Here is a summary of your recent activity and upcoming sessions.'}</p>
        </div>
      </div>

      {isDoctor
        ? <DoctorView 
            assessments={assessments} 
            appointments={appointments} 
            patientsList={patientsList} 
            navigate={navigate} 
            onDeleteClick={setAssessmentToDelete} 
          />
        : <PatientView 
            assessments={assessments} 
            appointments={appointments} 
            navigate={navigate} 
            onDeleteClick={setAssessmentToDelete} 
          />
      }

      {assessmentToDelete && (
        <div className="db-modal-overlay" onClick={() => setAssessmentToDelete(null)}>
          <div className="db-modal-card" onClick={e => e.stopPropagation()}>
            <div className="db-modal-icon"><NavIcon type="trash" /></div>
            <h2 className="db-modal-title">Delete Assessment?</h2>
            <p className="db-modal-text">Are you sure you want to delete this record? This action cannot be undone.</p>
            <div className="db-modal-actions">
              <button className="db-btn-outline" onClick={() => setAssessmentToDelete(null)}>Cancel</button>
              <button className="db-btn-danger" onClick={handleConfirmDelete}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </SidebarLayout>
  );
};

/* ─── Patient View ──────────────────────────────────────────────────────── */
const PatientView: React.FC<{ assessments: Assessment[]; appointments: Appointment[]; navigate: any; onDeleteClick: (id: string) => void }> = ({ assessments, appointments, navigate, onDeleteClick }) => {
  const displayedAssessments = assessments.slice(0, 5);
  const hasMoreAssessments = assessments.length > 5;
  
  const upcomingApt = appointments.find(a => a.status === 'Scheduled');
  const lastCompletedApt = appointments.find(a => a.status === 'Completed');

  return (
    <>
      <div className="db-grid-2">
        <div className="db-section-card session-card">
          <div className="db-section-header">
            <span className="db-section-title">Your next session</span>
            {upcomingApt && <span className="db-card-chip upcoming">Upcoming</span>}
          </div>
          
          {upcomingApt ? (
            <div className="db-session-row">
              <div className="db-session-left">
                <div className="db-session-avatar">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                </div>
                <div>
                  <div className="db-session-name">{upcomingApt.providerName}</div>
                  <div className="db-session-role">{upcomingApt.providerRole || 'Licensed Therapist'}</div>
                  <div className="db-session-date">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                    {upcomingApt.date} at {upcomingApt.time}
                  </div>
                </div>
              </div>
              <button className="db-join-btn" onClick={() => navigate('/appointments')}>Join video call</button>
            </div>
          ) : lastCompletedApt ? (
            <div className="db-empty" style={{ padding: '20px 0', alignItems: 'flex-start', textAlign: 'left' }}>
              <p style={{ margin: '0 0 4px 0', fontSize: 16, fontWeight: 600, color: 'var(--text-main)' }}>Time to check in?</p>
              <span style={{ fontSize: 14, color: 'var(--text-sub)', marginBottom: '16px', display: 'block' }}>
                Your last session with {lastCompletedApt.providerName} was on {lastCompletedApt.date}.
              </span>
              <button 
                className="db-join-btn" 
                style={{ width: 'auto', padding: '10px 20px' }}
                onClick={() => navigate(`/therapists/${lastCompletedApt.providerId || 1}`)}
              >
                Book Next Session
              </button>
            </div>
          ) : (
            <div className="db-empty" style={{ padding: '20px 0' }}>
              <p style={{ margin: 0, fontSize: 15 }}>No upcoming sessions.</p>
              <span style={{ fontSize: 13 }}>Book an appointment to get started.</span>
            </div>
          )}
        </div>

        <div className="db-section-card find-care-card">
          <div className="db-section-header">
            <span className="db-section-title">Find Care</span>
            <span className="db-card-chip">Cebu & Online</span>
          </div>
          <div className="db-find-care-content">
            <p className="db-body-text">Search for licensed professionals in Cebu or book an online telehealth session that fits your schedule.</p>
            <button className="db-primary-btn" onClick={() => navigate('/therapists')}>Browse Directory</button>
          </div>
        </div>
      </div>

      <div className="db-section-card">
        <div className="db-section-header">
          <div>
            <span className="db-section-title">Recent assessments</span>
            {assessments.length > 0 && <span className="db-count-chip">{assessments.length}</span>}
          </div>
          <button className="db-new-btn" onClick={() => navigate('/assessment')}>+ New assessment</button>
        </div>

        {assessments.length === 0 ? (
          <div className="db-empty">
            <div className="db-empty-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></svg>
            </div>
            <p>No assessments yet</p>
            <span>Take your first Triage Assessment to get a picture of how you've been feeling.</span>
            <br />
            <button className="db-take-btn" onClick={() => navigate('/assessment')}>Take an assessment</button>
          </div>
        ) : (
          <div className="db-table-wrap">
            <table>
              <thead>
                <tr>{['Date', 'Assessment', 'Risk score', 'Status', 'Actions'].map(h => <th key={h}>{h}</th>)}</tr>
              </thead>
              <tbody>
                {displayedAssessments.map(a => (
                  <tr key={a.id}>
                    <td className="db-td-muted">{a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : 'N/A'}</td>
                    <td className="db-td-strong">{a.assessmentType}</td>
                    <td>
                      <span className="db-risk-badge" style={riskStyle(a.riskLevel)}>
                        {a.riskLevel} <span style={{ opacity: 0.6 }}>·</span> {a.clinicalScore}
                      </span>
                    </td>
                    <td><span className="db-status-badge" style={statusStyle(a.status)}>{a.status}</span></td>
                    <td>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button className="db-action-btn" title="View details" onClick={() => navigate(`/assessment-result/${a.id}`, { state: { result: a } })}>
                          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        </button>
                        <button className="db-action-btn danger" title="Delete" onClick={() => onDeleteClick(a.id)}>
                          <NavIcon type="trash" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {hasMoreAssessments && (
              <div style={{ textAlign: 'center', marginTop: '24px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle)' }}>
                <button className="db-btn-outline" onClick={() => navigate('/assessments-history')}>Show all assessments</button>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
};

/* ─── Doctor View ───────────────────────────────────────────────────────── */
const DoctorView: React.FC<{ assessments: Assessment[]; appointments: Appointment[]; patientsList: PatientRecord[]; navigate: any; onDeleteClick: (id: string) => void }> = ({ assessments, appointments, patientsList, navigate }) => {
  
  // THE TRIAGE QUEUE LOGIC 
  const pending = assessments.filter(a => a.status === 'Pending').sort((a, b) => {
    const riskWeight: Record<string, number> = { High: 4, Moderate: 3, Mild: 2, Low: 1 };
    const weightA = riskWeight[a.riskLevel] || 0;
    const weightB = riskWeight[b.riskLevel] || 0;
    if (weightA !== weightB) return weightB - weightA;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  const displayedPending = pending.slice(0, 5);
  const hasMorePending = pending.length > 5;
  const highRiskCount = pending.filter(a => a.riskLevel === 'High' || a.riskLevel === 'Moderate').length;

  const upcomingAppointments = appointments.filter(a => a.status === 'Scheduled');
  const displayedAppointments = upcomingAppointments.slice(0, 3);
  const hasMoreAppointments = upcomingAppointments.length > 3;

  const stats = [
    { label: "Upcoming appointments", value: String(upcomingAppointments.length), icon: 'calendar', accent: 'sage'    },
    { label: 'Total patients',        value: String(patientsList.length),         icon: 'users',    accent: 'lavender' },
    { label: 'Pending reviews',       value: String(pending.length),              icon: 'clipboard', accent: pending.length > 0 ? 'amber' : 'sage' },
    { label: 'Urgent alerts',         value: String(highRiskCount),               icon: 'bell',     accent: highRiskCount > 0 ? 'red' : 'teal' },
  ];

  return (
    <>
      <div className="db-stats-grid">
        {stats.map(s => (
          <div key={s.label} className={`db-stat-card accent-${s.accent}`}>
            <div className="db-stat-icon"><NavIcon type={s.icon} /></div>
            <div className="db-stat-val">{s.value}</div>
            <div className="db-stat-label">{s.label}</div>
          </div>
        ))}
      </div>

      <div className="db-grid-2">
        <div className="db-section-card" style={{ marginBottom: 0 }}>
          <div className="db-section-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span className="db-section-title">Triage Queue</span>
              {pending.length > 0 && <span className="db-pending-badge">{pending.length} pending</span>}
            </div>
          </div>
          {pending.length === 0 ? (
            <div className="db-empty" style={{ padding: '24px 0' }}>
              <div className="db-empty-icon success"><NavIcon type="check" /></div>
              <p>All caught up</p>
              <span>No pending assessments to review.</span>
            </div>
          ) : (
            <div className="db-triage-list">
              {displayedPending.map(a => (
                <div key={a.id} className={`db-triage-item ${a.riskLevel === 'High' ? 'urgent' : ''}`}>
                  <div className="db-triage-top">
                    <span className="db-triage-date">{a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'N/A'}</span>
                    <span className="db-risk-badge" style={riskStyle(a.riskLevel)}>{a.riskLevel} Risk</span>
                  </div>
                  <div className="db-triage-type">
                    <strong style={{ color: 'var(--text-main)' }}>{a.patientName || 'Unknown Patient'}</strong> • {a.assessmentType}
                  </div>
                  <div className="db-triage-actions">
                    <button className="db-action-btn" title="View details" onClick={() => navigate(`/assessment-result/${a.id}`, { state: { result: a } })}>View Results</button>
                    <button className="db-mark-btn" onClick={async () => {
                      await fetch(`http://localhost:8083/api/assessments/${a.id}/review`, { method: 'PATCH' });
                      window.location.reload();
                    }}>
                      <NavIcon type="check" /> Mark Reviewed
                    </button>
                  </div>
                </div>
              ))}
              {hasMorePending && (
                <button className="db-btn-text" onClick={() => navigate('/assessments-history')} style={{ marginTop: 12, width: '100%' }}>View all {pending.length} pending</button>
              )}
            </div>
          )}
        </div>

        <div className="db-section-card" style={{ marginBottom: 0 }}>
          <div className="db-section-header">
            <span className="db-section-title">Upcoming Schedule</span>
            <button className="db-action-btn" onClick={() => navigate('/appointments')}><NavIcon type="calendar" /></button>
          </div>
          <div className="db-schedule-list">
            {upcomingAppointments.length === 0 ? (
               <div className="db-empty" style={{ padding: '24px 0' }}><p>No upcoming appointments.</p></div>
            ) : (
              displayedAppointments.map(apt => (
                <div key={apt.id} className="db-schedule-item">
                  <div className="db-sch-time">
                    <span style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)' }}>{apt.date}</span>
                    {apt.time}
                  </div>
                  <div className="db-sch-details">
                    <div className="db-sch-name">{apt.patientName}</div>
                    <div className="db-sch-type">{apt.type}</div>
                  </div>
                  {apt.type === 'Telehealth' ? (
                    <button className="db-join-btn small" onClick={() => navigate('/appointments')}><NavIcon type="video" /> Join</button>
                  ) : (
                    <span className="db-card-chip" style={{ background: '#F3F4F6', color: '#4B5563' }}>Clinic</span>
                  )}
                </div>
              ))
            )}
            
            {hasMoreAppointments && (
              <button 
                className="db-btn-text" 
                onClick={() => navigate('/appointments')} 
                style={{ marginTop: 12, width: '100%' }}
              >
                View all {upcomingAppointments.length} appointments
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="db-section-card" style={{ marginTop: 24 }}>
        <div className="db-section-header">
          <span className="db-section-title">Recent Patients</span>
          <button className="db-btn-outline" style={{ padding: '6px 16px', fontSize: 13 }} onClick={() => navigate('/patients')}>View All</button>
        </div>
        {patientsList.length === 0 ? (
          <div className="db-empty" style={{ padding: '24px 0' }}><p>No active patients.</p></div>
        ) : (
          <div className="db-table-wrap">
            <table>
              <thead>
                <tr><th>Patient Name</th><th>Status</th><th>Last Session</th><th>Action</th></tr>
              </thead>
              <tbody>
                {patientsList.slice(0, 3).map((p) => (
                  <tr key={p.id}>
                    <td className="db-td-strong">{p.name}</td>
                    <td>
                      <span className="db-status-badge" style={{ background: p.status === 'Active' ? '#ECFDF5' : '#EFF6FF', color: p.status === 'Active' ? '#065F46' : '#1D4ED8' }}>
                        {p.status}
                      </span>
                    </td>
                    <td className="db-td-muted">{p.lastSession}</td>
                    <td>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button className="db-action-btn">Open File</button>
                        <button className="db-action-btn" style={{ background: 'var(--bg-alt)', color: 'var(--text-main)', border: '1px solid var(--border)' }}>Schedule Follow-up</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
};

export default Dashboard;