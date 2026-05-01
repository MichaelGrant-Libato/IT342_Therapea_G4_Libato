import React, { useState } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import '../styles/AssessmentResult.css';

const AssessmentResult: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  const { result } = location.state || {};

  if (!result) return <Navigate to="/dashboard" replace />;

  const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
  const user = stored ? JSON.parse(stored) : null;
  const isDoctor = user?.role === 'DOCTOR';

  const dotClass = result.riskLevel.toLowerCase();
  const formattedDate = new Date(result.createdAt).toLocaleDateString('en-US', { 
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' 
  });

  const handleConfirmDelete = async () => {
    setIsProcessing(true);
    try {
      await fetch(`http://localhost:8083/api/assessments/${result.id}`, { method: 'DELETE' });
    } catch (error) {
      console.error("Failed to delete via API", error);
    }

    if (user) {
      const localHistory = JSON.parse(localStorage.getItem(`assessments_${user.email}`) || '[]');
      const filtered = localHistory.filter((a: any) => a.id !== result.id);
      localStorage.setItem(`assessments_${user.email}`, JSON.stringify(filtered));
    }

    navigate('/dashboard', { replace: true });
  };

  const handleMarkReviewed = async () => {
    setIsProcessing(true);
    try {
      await fetch(`http://localhost:8083/api/assessments/${result.id}/review`, { method: 'PATCH' });
      navigate('/dashboard', { replace: true });
    } catch (error) {
      console.error("Failed to mark as reviewed", error);
      setIsProcessing(false);
    }
  };

  return (
    <>
      <div className={`ar-container ${showDeleteModal ? 'blurred' : ''}`}>
        <div className="ar-card">
          
          <button className="ar-back-btn" onClick={() => navigate(-1)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polyline points="15 18 9 12 15 6" />
            </svg>
            Back
          </button>

          <div className="ar-header">
            <h2 className="ar-title">Assessment Record</h2>
            <p className="ar-date">{formattedDate}</p>
          </div>

          <div className="ar-risk-box">
            <div className="ar-risk-label">Recorded risk level</div>
            <div className="ar-risk-val-row">
              <span className="ar-risk-val">{result.riskLevel}</span>
              <div className={`ar-risk-dot ${dotClass}`} />
            </div>
          </div>

          <div className="ar-score-wrap">
            <div className="ar-score-label">Clinical score</div>
            <div className="ar-score-num">{result.clinicalScore}</div>
            <div className="ar-score-max">out of 100</div>
          </div>

          <div className="ar-subscores">
            <div className="ar-sub-card">
              <div className="ar-sub-label">PHQ-9 Depression</div>
              <div className="ar-sub-val">{result.phq9Score}</div>
              <div className="ar-sub-desc">/ 27</div>
            </div>
            <div className="ar-sub-card">
              <div className="ar-sub-label">GAD-7 Anxiety</div>
              <div className="ar-sub-val">{result.gad7Score}</div>
              <div className="ar-sub-desc">/ 21</div>
            </div>
          </div>

          <div className="ar-actions">
            {isDoctor ? (
              result.status === 'Pending' ? (
                <button 
                  className="ar-btn-primary" 
                  onClick={handleMarkReviewed} 
                  disabled={isProcessing}
                  style={{ width: '100%', padding: '16px', borderRadius: '12px', background: 'var(--primary)', color: 'white', border: 'none', fontWeight: 600, cursor: 'pointer' }}
                >
                  {isProcessing ? 'Processing...' : 'Mark as Reviewed'}
                </button>
              ) : (
                <div style={{ width: '100%', textAlign: 'center', padding: '16px', background: '#ECFDF5', color: '#065F46', borderRadius: '12px', fontWeight: 600 }}>
                  ✓ This assessment has been reviewed
                </div>
              )
            ) : (
              <>
                <button className="ar-btn-outline" onClick={() => navigate('/therapists')}>View Therapists</button>
                <button className="ar-btn-outline" onClick={() => navigate('/emergency')}>Emergency Resources</button>
              </>
            )}
          </div>

          {!isDoctor && (
            <button className="ar-btn-delete" onClick={() => setShowDeleteModal(true)}>
              Delete this record
            </button>
          )}
        </div>
      </div>

      {showDeleteModal && (
        <div className="ar-modal-overlay" onClick={() => setShowDeleteModal(false)}>
          <div className="ar-modal-card" onClick={e => e.stopPropagation()}>
            <div className="ar-modal-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/>
              </svg>
            </div>
            <h2 className="ar-modal-title">Delete Assessment?</h2>
            <p className="ar-modal-text">
              Are you sure you want to delete this record? This action cannot be undone.
            </p>
            <div className="ar-modal-actions">
              <button 
                className="ar-btn-modal-cancel"
                onClick={() => setShowDeleteModal(false)}
                disabled={isProcessing}
              >
                Cancel
              </button>
              <button 
                className="ar-btn-modal-delete"
                onClick={handleConfirmDelete}
                disabled={isProcessing}
              >
                {isProcessing ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AssessmentResult;