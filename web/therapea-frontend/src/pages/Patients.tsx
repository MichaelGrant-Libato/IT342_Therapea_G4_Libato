import React, { useState, useEffect } from 'react';
import { SidebarLayout } from '../components/SidebarLayout';
import { useNavigate } from 'react-router-dom';
import '../styles/Patients.css';

interface PatientRecord {
  id: string;
  name: string;
  status: string;
  lastSession: string;
  nextSession: string;
  risk: string;
}

const Patients: React.FC = () => {
  const navigate = useNavigate();
  const [patients, setPatients] = useState<PatientRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');

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
          if (data.success) {
            setPatients(data.patients);
          }
        }
      } catch (err) {
        // Fallback for UI visualization
        const mockData = [
          { id: "1", name: "Emily Watson", status: "Active", lastSession: "Oct 18, 2026", nextSession: "Oct 25, 2026", risk: "Low" },
          { id: "2", name: "James Garcia", status: "Active", lastSession: "Oct 12, 2026", nextSession: "Today", risk: "Moderate" }
        ];
        setPatients(mockData);
      } finally {
        setIsLoading(false);
      }
    };

    fetchPatients();
  }, [navigate]);

  const filteredPatients = patients.filter(p => 
    p.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <SidebarLayout title="My Patients">
      <div className="pat-header">
        <div>
          <h1 className="pat-title">Patient Roster</h1>
          <p className="pat-subtitle">Manage your active patients and their clinical records.</p>
        </div>
        <button className="pat-btn-primary">+ Invite Patient</button>
      </div>

      <div className="pat-card">
        <div className="pat-controls">
          <input 
            type="text" 
            placeholder="Search patients by name..." 
            className="pat-search" 
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select className="pat-filter">
            <option>All Statuses</option>
            <option>Active</option>
            <option>New Patient</option>
          </select>
        </div>

        {isLoading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Loading patient roster...
          </div>
        ) : filteredPatients.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            {search ? 'No patients match your search.' : 'You have no patients yet. Your roster will automatically update when a patient books a session with you.'}
          </div>
        ) : (
          <div className="pat-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Patient Name</th>
                  <th>Status</th>
                  <th>Last Session</th>
                  <th>Next Session</th>
                  <th>Risk Level</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredPatients.map(p => (
                  <tr key={p.id}>
                    <td className="pat-strong">{p.name}</td>
                    <td>
                      <span className={`pat-status ${p.status === 'Active' ? 'active' : 'new'}`}>
                        {p.status}
                      </span>
                    </td>
                    <td className="pat-muted">{p.lastSession}</td>
                    <td className="pat-muted">{p.nextSession}</td>
                    <td>
                      <span className={`pat-risk ${p.risk.toLowerCase()}`}>
                        {p.risk}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button className="pat-btn-outline">View File</button>
                        {/* ── NEW: Schedule Follow-up Button ── */}
                        <button className="pat-btn-outline" style={{ background: 'var(--bg-alt)', color: 'var(--text-main)' }}>
                          Schedule Follow-up
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </SidebarLayout>
  );
};

export default Patients;