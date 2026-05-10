import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SidebarLayout } from '../../core/components/SidebarLayout';
import './Progress.css';

interface UserData {
  email: string;
  role: string;
}

interface Assessment {
  id: string;
  createdAt: string;
  phq9Score: number;
  gad7Score: number;
}

interface PatientRecord {
  id: string;
  name: string;
  fullName?: string;
  email: string;
}

interface ChartData {
  date: string;
  phq: number;
  gad: number;
}

const Progress: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const targetPatientEmail = searchParams.get('patient'); 

  const [user, setUser] = useState<UserData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const [historyData, setHistoryData] = useState<ChartData[]>([]);
  const [patients, setPatients] = useState<PatientRecord[]>([]);
  const [selectedPatient, setSelectedPatient] = useState<PatientRecord | null>(null);

  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    const init = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) {
        navigate('/login', { replace: true });
        return;
      }
      const parsedUser = JSON.parse(stored);
      setUser(parsedUser);

      if (parsedUser.role === 'DOCTOR') {
        try {
          const res = await fetch(`http://localhost:8083/api/patients/doctor?email=${encodeURIComponent(parsedUser.email)}`);
          const data = await res.json();
          if (data.success) {
            setPatients(data.patients);

            // If an email is in the URL, automatically select them
            if (targetPatientEmail) {
              const matchedPatient = data.patients.find((p: any) => p.email === targetPatientEmail);
              if (matchedPatient) {
                setSelectedPatient(matchedPatient);
                fetchAssessments(targetPatientEmail);
              } else {
                // Fallback if patient is passed but not in list
                setSelectedPatient({ id: '0', name: targetPatientEmail, email: targetPatientEmail });
                fetchAssessments(targetPatientEmail);
              }
            }
          }
        } catch (err) {
          console.error("Failed to fetch patients", err);
        }
      } else {
        fetchAssessments(parsedUser.email);
      }
      setIsLoading(false);
    };

    init();
  }, [navigate, targetPatientEmail]);

  const fetchAssessments = async (targetEmail: string) => {
    if (!targetEmail) {
      setHistoryData([]);
      return;
    }

    try {
      const res = await fetch(`http://localhost:8083/api/assessments/user?email=${encodeURIComponent(targetEmail)}`);
      const data = await res.json();
      
      if (data.success && data.assessments.length > 0) {
        const sorted = data.assessments.sort((a: Assessment, b: Assessment) => 
          new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        const recent = sorted.slice(-6);

        const formattedChartData = recent.map((a: Assessment) => ({
          date: new Date(a.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
          phq: a.phq9Score || 0,
          gad: a.gad7Score || 0
        }));

        setHistoryData(formattedChartData);
      } else {
        setHistoryData([]);
      }
    } catch (err) {
      console.error("Failed to fetch assessments", err);
      setHistoryData([]);
    }
  };

  const handleSelectPatient = (patient: PatientRecord) => {
    setSelectedPatient(patient);
    setSearchParams({ patient: patient.email });
    fetchAssessments(patient.email);
  };

  const clearSelection = () => {
    setSelectedPatient(null);
    setHistoryData([]);
    setSearchParams({}); // Clear URL params
  };

  const getTrend = (metric: 'phq' | 'gad') => {
    if (historyData.length < 2) return { text: "Need more data to establish trend", class: "neutral" };
    
    const latest = historyData[historyData.length - 1][metric];
    const previous = historyData[historyData.length - 2][metric];
    const diff = latest - previous;

    if (diff < 0) return { text: "↓ Trending down (Improvement)", class: "success" };
    if (diff > 0) return { text: "↑ Trending up (Worsening)", class: "danger" };
    return { text: "→ Stable", class: "neutral" };
  };

  if (isLoading || !user) {
    return (
      <SidebarLayout title="Progress">
        <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', height: '70vh', gap: '16px' }}>
          <div className="custom-loader" />
          <p style={{ color: '#64748b', fontWeight: 500, fontSize: '15px' }}>Loading progress data...</p>
        </div>
        <style>{`
          .custom-loader { width: 40px; height: 40px; border: 4px solid #e2e8f0; border-top: 4px solid #0A5C36; border-radius: 50%; animation: spin 1s linear infinite; }
          @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        `}</style>
      </SidebarLayout>
    );
  }

  const isDoctor = user.role === 'DOCTOR';
  const phqTrend = getTrend('phq');
  const gadTrend = getTrend('gad');

  const filteredPatients = patients.filter(p => 
    (p.name || p.fullName || '').toLowerCase().includes(searchQuery.toLowerCase()) || 
    p.email.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <SidebarLayout title={isDoctor ? "Patient Progress" : "My Progress"}>
      
      <style>{`
        .prg-roster-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; margin-top: 24px; }
        .prg-patient-card { background: white; border: 1px solid #e2e8f0; border-radius: 16px; padding: 20px; cursor: pointer; transition: 0.2s; box-shadow: 0 2px 4px rgba(0,0,0,0.02); display: flex; flex-direction: column; gap: 12px; }
        .prg-patient-card:hover { border-color: #cbd5e1; transform: translateY(-2px); box-shadow: 0 10px 15px -3px rgba(0,0,0,0.05); }
        .prg-patient-avatar { width: 44px; height: 44px; background: #EEF4EE; color: #0A5C36; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; }
        .prg-patient-info h3 { margin: 0; font-size: 16px; color: #1e293b; font-weight: 700; }
        .prg-patient-info p { margin: 4px 0 0 0; font-size: 13.5px; color: #64748b; }
        .prg-view-btn { margin-top: auto; background: #f8fafc; color: #0A5C36; border: 1px solid #e2e8f0; padding: 10px; border-radius: 8px; font-weight: 600; text-align: center; font-size: 13.5px; transition: 0.2s; }
        .prg-patient-card:hover .prg-view-btn { background: #0A5C36; color: white; border-color: #0A5C36; }
      `}</style>

      {/* --- DOCTOR: VIEWING SPECIFIC PATIENT --- */}
      {isDoctor && selectedPatient ? (
        <>
          <button 
             onClick={clearSelection}
             style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, fontSize: '14px', marginBottom: '24px', padding: 0 }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="15 18 9 12 15 6" /></svg>
            Back to Patient List
          </button>
          
          <div className="prg-header">
            <div>
              <h1 className="prg-title">Trajectory: {selectedPatient.name || selectedPatient.fullName || selectedPatient.email}</h1>
              <p className="prg-subtitle">Reviewing self-assessment clinical scores over time.</p>
            </div>
          </div>
        </>
      ) : (
        /* --- DEFAULT HEADER (Patient or Doctor viewing grid) --- */
        <div className="prg-header">
          <div>
            <h1 className="prg-title">{isDoctor ? "Patient Trajectory" : "Clinical Progress"}</h1>
            <p className="prg-subtitle">
              {isDoctor 
                ? "Select a patient from your roster to review their clinical scores." 
                : "Track your PHQ-9 (Depression) and GAD-7 (Anxiety) scores over time."}
            </p>
          </div>

          {/* Search bar only visible when looking at the patient grid */}
          {isDoctor && !selectedPatient && (
            <div className="prg-search-input-box" style={{ maxWidth: '300px' }}>
              <svg className="prg-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input 
                type="text" 
                className="prg-search-input" 
                placeholder="Search patients..." 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          )}
        </div>
      )}

      {/* --- DOCTOR: VIEWING PATIENT GRID --- */}
      {isDoctor && !selectedPatient && (
        <>
          {filteredPatients.length > 0 ? (
            <div className="prg-roster-grid">
              {filteredPatients.map(p => {
                const displayName = p.name || p.fullName || 'Unknown Patient';
                const initials = displayName.substring(0, 2).toUpperCase();
                return (
                  <div key={p.id} className="prg-patient-card" onClick={() => handleSelectPatient(p)}>
                    <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                      <div className="prg-patient-avatar">{initials}</div>
                      <div className="prg-patient-info">
                        <h3>{displayName}</h3>
                        <p>{p.email}</p>
                      </div>
                    </div>
                    <div className="prg-view-btn">View Trajectory Charts</div>
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="prg-empty-state" style={{ marginTop: '40px' }}>
              <p>No patients found.</p>
              <span>{searchQuery ? 'Try adjusting your search.' : 'You currently have no patients assigned to you.'}</span>
            </div>
          )}
        </>
      )}

      {/* --- EMPTY STATE FOR CHARTS --- */}
      {(!isDoctor || selectedPatient) && historyData.length === 0 && (
        <div className="prg-empty-state" style={{ marginTop: '40px' }}>
          <div style={{ background: '#f1f5f9', padding: '16px', borderRadius: '50%', marginBottom: '16px', display: 'inline-flex' }}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="1.5">
              <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
          </div>
          <p>No assessment data available yet.</p>
          {!isDoctor && <span>Take your first Triage Assessment to start tracking your progress.</span>}
        </div>
      )}

      {/* --- CHARTS --- */}
      {(!isDoctor || selectedPatient) && historyData.length > 0 && (
        <div className="prg-grid">
          <div className="prg-card">
            <h2>PHQ-9 Trajectory (Depression)</h2>
            <div className="prg-chart">
              {historyData.map((h, i) => (
                <div key={`phq-${h.date}-${i}`} className="prg-bar-group">
                  <div className="prg-bar-track">
                    <div className="prg-bar fill-phq" style={{ height: `${(h.phq / 27) * 100}%` }}>
                      <span className="prg-tooltip">{h.phq}</span>
                    </div>
                  </div>
                  <span className="prg-label">{h.date}</span>
                </div>
              ))}
            </div>
            <p className={`prg-trend ${phqTrend.class}`}>{phqTrend.text}</p>
          </div>

          <div className="prg-card">
            <h2>GAD-7 Trajectory (Anxiety)</h2>
            <div className="prg-chart">
              {historyData.map((h, i) => (
                <div key={`gad-${h.date}-${i}`} className="prg-bar-group">
                  <div className="prg-bar-track">
                    <div className="prg-bar fill-gad" style={{ height: `${(h.gad / 21) * 100}%` }}>
                      <span className="prg-tooltip">{h.gad}</span>
                    </div>
                  </div>
                  <span className="prg-label">{h.date}</span>
                </div>
              ))}
            </div>
            <p className={`prg-trend ${gadTrend.class}`}>{gadTrend.text}</p>
          </div>
        </div>
      )}
    </SidebarLayout>
  );
};

export default Progress;