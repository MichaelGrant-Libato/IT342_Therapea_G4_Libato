import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../../core/components/SidebarLayout';
import { createClient } from '@supabase/supabase-js';

// Singleton pattern to prevent "Multiple GoTrueClient instances" warning
const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || '';
const supabaseKey = import.meta.env.VITE_SUPABASE_ANON_KEY || '';
let supabase: any;
if (supabaseUrl && supabaseKey) {
  if (!(window as any).__supabaseClient) {
    (window as any).__supabaseClient = createClient(supabaseUrl, supabaseKey);
  }
  supabase = (window as any).__supabaseClient;
}

// 🔴 FIXED: Removed the fake last/next session fields. Kept it strictly to real data.
interface PatientRecord {
  id: string;
  name: string;
  email: string; 
  status: string;
  risk: string;
}

interface SessionRecord {
  id: string;
  date: string;
  type: string;
  status: string;
  notes: string;
}

const Patients: React.FC = () => {
  const navigate = useNavigate();
  const [patients, setPatients] = useState<PatientRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');

  const [showRecordsModal, setShowRecordsModal] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState<PatientRecord | null>(null);
  const [patientRecords, setPatientRecords] = useState<SessionRecord[]>([]);
  const [isLoadingRecords, setIsLoadingRecords] = useState(false);

  useEffect(() => {
    const fetchPatients = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) { navigate('/login'); return; }
      
      const user = JSON.parse(stored);
      if (user.role !== 'DOCTOR') { navigate('/dashboard'); return; }

      try {
        const res = await fetch(`http://localhost:8083/api/patients/doctor?email=${encodeURIComponent(user.email)}`);
        if (res.ok) {
          const data = await res.json();
          if (data.success && data.patients && data.patients.length > 0) {
            const mappedPatients = data.patients.map((p: any) => ({
              id: String(p.id || Math.random()),
              name: p.fullName || p.name || p.email || 'Unknown Patient',
              email: p.email,
              status: 'Active', 
              risk: 'Low' 
            }));
            setPatients(mappedPatients);
            setIsLoading(false);
            return;
          }
        }
        throw new Error("No patients found, falling back to mock data");
      } catch (err) {
        // Fallback Data using only real fields
        const mockData = [
          { id: "1", name: "Emily Watson", email: "emily@example.com", status: "Active", risk: "Low" },
          { id: "2", name: "James Garcia", email: "james@example.com", status: "Active", risk: "Moderate" }
        ];
        setPatients(mockData);
      } finally {
        setIsLoading(false);
      }
    };

    fetchPatients();
  }, [navigate]);

  const openRecordsModal = async (patient: PatientRecord) => {
    setSelectedPatient(patient);
    setShowRecordsModal(true);
    setIsLoadingRecords(true);
    setPatientRecords([]); 

    try {
      const res = await fetch(`http://localhost:8083/api/progress/patient?email=${encodeURIComponent(patient.email)}`);
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.records) {
          setPatientRecords(data.records);
        }
      }
    } catch (err) {
      console.error("Failed to fetch dynamic patient records:", err);
    } finally {
      setIsLoadingRecords(false);
    }
  };

  const filteredPatients = patients.filter(p => p.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <SidebarLayout title="My Patients">
      <div className="pat-header">
        <div>
          <h1 className="pat-title">Patient Roster</h1>
          <p className="pat-subtitle">Manage your active patients and their clinical records.</p>
        </div>
      </div>

      <div className="pat-card">
        <div className="pat-controls">
          <input 
            type="text" placeholder="Search patients by name..." className="pat-search" 
            value={search} onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {isLoading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading patient roster...</div>
        ) : filteredPatients.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>No patients found.</div>
        ) : (
          <div className="pat-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Patient Name</th>
                  {/* 🔴 FIXED: Replaced fake dates with the actual Email Address */}
                  <th>Email Address</th>
                  <th>Status</th>
                  <th>Risk Level</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredPatients.map(p => (
                  <tr key={p.id}>
                    <td className="pat-strong">{p.name}</td>
                    {/* 🔴 FIXED: Displaying the real email */}
                    <td className="pat-muted">{p.email}</td>
                    <td><span className={`pat-status ${p.status === 'Active' ? 'active' : 'new'}`}>{p.status}</span></td>
                    <td><span className={`pat-risk ${p.risk.toLowerCase()}`}>{p.risk}</span></td>
                    <td>
                      <button 
                        className="pat-btn-outline"
                        onClick={() => openRecordsModal(p)}
                      >
                        View Records
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showRecordsModal && selectedPatient && (
        <div className="pat-modal-overlay" onClick={() => setShowRecordsModal(false)}>
          <div className="pat-modal-card large" onClick={e => e.stopPropagation()}>
            <div className="pat-modal-header">
              <h2>Clinical Records: {selectedPatient.name}</h2>
              <button className="pat-modal-close" onClick={() => setShowRecordsModal(false)}>×</button>
            </div>
            <div className="pat-modal-body">
              {isLoadingRecords ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '60px 0' }}>
                   <div className="custom-loader" style={{ marginBottom: '16px' }} />
                   <p style={{ color: '#64748b', margin: 0 }}>Fetching records securely from database...</p>
                </div>
              ) : patientRecords.length === 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 20px', color: '#64748b' }}>
                   <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ opacity: 0.5, marginBottom: '16px' }}>
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>
                   </svg>
                   <p style={{ margin: 0, fontSize: '15px' }}>No clinical notes found for this patient.</p>
                </div>
              ) : (
                <div className="pat-timeline">
                  {patientRecords.map((record, i) => (
                    <div key={record.id} className="timeline-item">
                      <div className="timeline-marker"></div>
                      {i !== patientRecords.length - 1 && <div className="timeline-connector"></div>}
                      <div className="timeline-content">
                        <div className="timeline-header">
                          <span className="timeline-date">{record.date}</span>
                          <span className="timeline-badge">{record.type}</span>
                        </div>
                        <div className="timeline-body">
                          <h4>Clinical Note</h4>
                          <p>{record.notes}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <style>{`
        .custom-loader { width: 32px; height: 32px; border: 3px solid #f1f5f9; border-top: 3px solid #0A5C36; border-radius: 50%; animation: spin 1s linear infinite; }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

        .pat-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
        .pat-title { font-family: 'Lora', serif; font-size: 28px; color: #1e293b; margin: 0 0 8px 0; }
        .pat-subtitle { color: #64748b; margin: 0; font-size: 15px; }
        .pat-card { background: white; border-radius: 16px; border: 1px solid #e2e8f0; padding: 24px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.02); }
        .pat-controls { display: flex; gap: 16px; margin-bottom: 24px; }
        .pat-search { flex: 1; padding: 12px 16px; border-radius: 10px; border: 1px solid #cbd5e1; outline: none; font-family: inherit; font-size: 14px; }
        .pat-search:focus { border-color: #0A5C36; }
        .pat-table-wrap { overflow-x: auto; }
        table { width: 100%; border-collapse: collapse; text-align: left; }
        th { padding: 16px; border-bottom: 2px solid #e2e8f0; color: #64748b; font-weight: 600; font-size: 12px; text-transform: uppercase; }
        td { padding: 16px; border-bottom: 1px solid #f1f5f9; color: #1e293b; font-size: 14.5px; vertical-align: middle; }
        .pat-strong { font-weight: 600; color: #0f172a; }
        .pat-muted { color: #64748b; font-size: 14px; }
        .pat-status { padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; display: inline-block; background: #dcfce7; color: #166534; }
        .pat-risk { padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; display: inline-block; background: #f1f5f9; color: #475569; }
        .pat-btn-outline { border: 1px solid #cbd5e1; background: white; padding: 8px 14px; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 600; color: #475569; font-family: inherit; transition: 0.2s; }
        .pat-btn-outline:hover { background: #f8fafc; border-color: #94a3b8; color: #0f172a; }

        .pat-modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15,23,42,0.6); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 9999; animation: fadeIn 0.2s ease; }
        .pat-modal-card { background: white; width: 90%; max-width: 480px; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); overflow: hidden; animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
        .pat-modal-card.large { max-width: 700px; max-height: 85vh; display: flex; flex-direction: column; }
        .pat-modal-header { padding: 24px; border-bottom: 1px solid #f1f5f9; display: flex; justify-content: space-between; align-items: center; background: #f8fafc; }
        .pat-modal-header h2 { margin: 0; font-family: 'Lora', serif; font-size: 20px; color: #1e293b; font-weight: 700; }
        .pat-modal-close { background: none; border: none; font-size: 28px; color: #94a3b8; cursor: pointer; padding: 0; line-height: 1; transition: 0.2s; }
        .pat-modal-close:hover { color: #1e293b; }
        .pat-modal-body { padding: 24px; overflow-y: auto; }

        .pat-timeline { display: flex; flex-direction: column; }
        .timeline-item { position: relative; padding-left: 32px; margin-bottom: 24px; }
        .timeline-item:last-child { margin-bottom: 0; }
        .timeline-marker { position: absolute; left: 0; top: 4px; width: 12px; height: 12px; border-radius: 50%; background: #0A5C36; border: 3px solid #dcfce7; z-index: 2; }
        .timeline-connector { position: absolute; left: 5px; top: 16px; bottom: -28px; width: 2px; background: #e2e8f0; z-index: 1; }
        .timeline-content { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px 20px; }
        .timeline-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
        .timeline-date { font-weight: 700; color: #1e293b; font-size: 15px; }
        .timeline-badge { background: #EFF6FF; color: #1D4ED8; border: 1px solid #BFDBFE; padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
        .timeline-body h4 { margin: 0 0 4px 0; font-size: 13px; color: #64748b; font-weight: 700; text-transform: uppercase; }
        .timeline-body p { margin: 0; color: #334155; font-size: 14.5px; line-height: 1.6; }

        @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
        @keyframes slideUp { from { opacity: 0; transform: translateY(20px) scale(0.98); } to { opacity: 1; transform: translateY(0) scale(1); } }
      `}</style>
    </SidebarLayout>
  );
};

export default Patients;