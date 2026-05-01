import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/AssessmentHistory.css';

interface Assessment {
  id: string; assessmentType: string; phq9Score: number; gad7Score: number;
  clinicalScore: number; riskLevel: string; status: string; createdAt: string;
}

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

const AssessmentHistory: React.FC = () => {
  const navigate = useNavigate();
  const [assessments, setAssessments] = useState<Assessment[]>([]);

  useEffect(() => {
    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (!stored) { navigate('/login'); return; }
    
    const user = JSON.parse(stored);
    
    const load = async () => {
      try {
        const aRes = await fetch(`http://localhost:8083/api/assessments/user?email=${encodeURIComponent(user.email)}`);
        const aData = await aRes.json();
        if (aData.success) {
          const sorted = aData.assessments.sort((a: Assessment, b: Assessment) => 
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          );
          setAssessments(sorted);
        }
      } catch (err) {
         // Fallback to local storage if API fails
         const localHistory = JSON.parse(localStorage.getItem(`assessments_${user.email}`) || '[]');
         setAssessments(localHistory);
      }
    };
    
    load();
  }, [navigate]);

  return (
    <div className="ah-container">
      <div className="ah-layout">
        
        <button className="ah-back-btn" onClick={() => navigate('/dashboard')}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          Back to Dashboard
        </button>

        <h1 className="ah-title">Assessment History</h1>
        <p className="ah-subtitle">A complete record of all your past clinical check-ins.</p>

        <div className="ah-card">
          <div className="ah-table-wrap">
            <table>
              <thead>
                <tr>
                  {['Date', 'Assessment', 'Risk score', 'Status', 'Actions'].map(h => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {assessments.map(a => (
                  <tr key={a.id}>
                    <td className="ah-td-muted">
                      {a.createdAt
                        ? new Date(a.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })
                        : 'N/A'}
                    </td>
                    <td className="ah-td-strong">{a.assessmentType}</td>
                    <td>
                      <span className="ah-risk-badge" style={riskStyle(a.riskLevel)}>
                        {a.riskLevel} <span className="ah-badge-dot">·</span> {a.clinicalScore}
                      </span>
                    </td>
                    <td>
                      <span className="ah-status-badge" style={statusStyle(a.status)}>
                        {a.status}
                      </span>
                    </td>
                    <td>
                      <button 
                        className="ah-action-btn" 
                        title="View details"
                        onClick={() => navigate(`/assessment-result/${a.id}`, { state: { result: a } })}
                      >
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
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
    </div>
  );
};

export default AssessmentHistory;