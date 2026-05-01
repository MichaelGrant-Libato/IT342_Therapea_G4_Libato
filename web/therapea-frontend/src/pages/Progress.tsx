import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import '../styles/Progress.css';

// ─── Interfaces ───
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
  email: string;
}

interface ChartData {
  date: string;
  phq: number;
  gad: number;
}

const Progress: React.FC = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  // Data States
  const [historyData, setHistoryData] = useState<ChartData[]>([]);
  const [patients, setPatients] = useState<PatientRecord[]>([]);
  const [selectedPatientEmail, setSelectedPatientEmail] = useState<string>('');

  // ─── Searchable Dropdown States ───
  const [searchQuery, setSearchQuery] = useState('');
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

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
  }, [navigate]);

  // Click outside to close dropdown
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

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
    setSelectedPatientEmail(patient.email);
    setSearchQuery(patient.name); // Set input to patient's name
    setIsDropdownOpen(false);
    fetchAssessments(patient.email);
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

  if (isLoading || !user) return <SidebarLayout title="Progress"><div style={{ padding: 40 }}>Loading...</div></SidebarLayout>;

  const isDoctor = user.role === 'DOCTOR';
  const phqTrend = getTrend('phq');
  const gadTrend = getTrend('gad');

  // Filter patients based on search query
  const filteredPatients = patients.filter(p => 
    p.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <SidebarLayout title={isDoctor ? "Patient Progress" : "My Progress"}>
      <div className="prg-header">
        <div>
          <h1 className="prg-title">{isDoctor ? "Patient Trajectory" : "Clinical Progress"}</h1>
          <p className="prg-subtitle">
            {isDoctor 
              ? "Search and select a patient to review their clinical scores." 
              : "Track your PHQ-9 (Depression) and GAD-7 (Anxiety) scores over time."}
          </p>
        </div>

        {/* ─── Searchable Autocomplete Dropdown ─── */}
        {isDoctor && (
          <div className="prg-search-wrap" ref={dropdownRef}>
            <div className="prg-search-input-box">
              <svg className="prg-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input 
                type="text" 
                className="prg-search-input" 
                placeholder="Search patient name..." 
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setIsDropdownOpen(true);
                  if (e.target.value === '') setSelectedPatientEmail(''); // Reset if cleared
                }}
                onFocus={() => setIsDropdownOpen(true)}
              />
            </div>

            {isDropdownOpen && (
              <div className="prg-dropdown-list">
                {filteredPatients.length > 0 ? (
                  filteredPatients.map(p => (
                    <button 
                      key={p.id} 
                      className="prg-dropdown-item" 
                      onClick={() => handleSelectPatient(p)}
                    >
                      {p.name}
                    </button>
                  ))
                ) : (
                  <div className="prg-dropdown-empty">No patients found.</div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {isDoctor && !selectedPatientEmail && (
        <div className="prg-empty-state">
          <p>Please search and select a patient to view their clinical charts.</p>
        </div>
      )}

      {(!isDoctor || selectedPatientEmail) && historyData.length === 0 && (
        <div className="prg-empty-state">
          <p>No assessment data available yet.</p>
          {!isDoctor && <span>Take your first Triage Assessment to start tracking your progress.</span>}
        </div>
      )}

      {historyData.length > 0 && (
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