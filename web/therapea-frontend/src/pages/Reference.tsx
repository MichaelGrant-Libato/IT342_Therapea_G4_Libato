import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import "../styles/Register.css"; // Re-using register styles for clean layout

const Reference: React.FC = () => {
  const navigate = useNavigate();
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8083';

  const [formData, setFormData] = useState({ referenceNumber: '', email: '' });
  const [statusResult, setStatusResult] = useState<{ status: string, message: string } | null>(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleCheckStatus = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');
    setStatusResult(null);

    try {
      // NOTE: You will need to build this endpoint on your backend
      const response = await fetch(`${API_BASE_URL}/api/auth/check-status?ref=${encodeURIComponent(formData.referenceNumber)}&email=${encodeURIComponent(formData.email)}`);
      
      if (response.ok) {
        const data = await response.json();
        setStatusResult(data); 
        // Expecting { status: 'PENDING' | 'APPROVED' | 'REJECTED', message: 'Admin feedback here if declined' }
      } else {
        setError('No application found. If your application was recently rejected, your record has been cleared so you may register again.');
      }
    } catch {
      setError('Failed to connect to the server. Please try again later.');
    } finally {
      setIsLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    if (status === 'APPROVED') return '#0A5C36'; // Dark Green
    if (status === 'REJECTED') return '#DC2626'; // Red
    return '#D97706'; // Orange/Pending
  };

  return (
    <div className="register-container" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F8FAFC' }}>
      <div className="register-card" style={{ maxWidth: '500px', width: '100%', padding: '40px', boxShadow: '0 4px 20px rgba(0,0,0,0.08)' }}>
        
        <div className="register-header" style={{ textAlign: 'center', marginBottom: '30px' }}>
          <div className="register-logo" style={{ justifyContent: 'center', marginBottom: '20px' }}>
            <div className="register-logo-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="white"><path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/></svg>
            </div>
            <span className="register-logo-text" style={{ color: '#1E293B' }}>Thera<span style={{ color: '#8BA888' }}>Pea</span></span>
          </div>
          <h1 className="register-title">Track Application</h1>
          <p className="register-subtitle">Enter your reference number and email to check your approval status.</p>
        </div>

        {error && <div className="error-message">{error}</div>}

        {!statusResult ? (
          <form onSubmit={handleCheckStatus} className="register-form">
            <div className="form-group">
              <label className="form-label">Reference Number</label>
              <input 
                type="text" 
                value={formData.referenceNumber}
                onChange={(e) => setFormData({ ...formData, referenceNumber: e.target.value })}
                placeholder="e.g. TRK-123456" 
                className="form-input" 
                required 
              />
            </div>
            <div className="form-group">
              <label className="form-label">Registered Email</label>
              <input 
                type="email" 
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder="doctor@example.com" 
                className="form-input" 
                required 
              />
            </div>
            <button type="submit" className="login-button" disabled={isLoading} style={{ marginTop: '10px' }}>
              {isLoading ? 'Checking...' : 'Check Status'}
            </button>
          </form>
        ) : (
          <div style={{ textAlign: 'center', padding: '20px 0' }}>
            <h2 style={{ fontSize: '24px', fontWeight: 'bold', color: getStatusColor(statusResult.status), marginBottom: '16px' }}>
              Status: {statusResult.status}
            </h2>
            
            {statusResult.status === 'PENDING' && (
              <p style={{ color: '#64748b', fontSize: '15px', lineHeight: '1.6' }}>
                Your application is currently under review by our administration team. We will send you an email as soon as a decision is made.
              </p>
            )}

            {statusResult.status === 'APPROVED' && (
              <p style={{ color: '#64748b', fontSize: '15px', lineHeight: '1.6' }}>
                Congratulations! Your account has been approved and activated. You can now log in to your provider dashboard.
              </p>
            )}

            {statusResult.status === 'REJECTED' && (
              <div style={{ background: '#FEF2F2', padding: '16px', borderRadius: '8px', border: '1px solid #FECACA', textAlign: 'left' }}>
                <p style={{ color: '#991B1B', fontWeight: 600, fontSize: '14px', marginBottom: '4px' }}>Reason for Rejection:</p>
                <p style={{ color: '#B91C1C', fontSize: '14px' }}>{statusResult.message || 'Please contact support for more details.'}</p>
              </div>
            )}

            <button 
              className="back-button" 
              onClick={() => setStatusResult(null)} 
              style={{ marginTop: '24px', width: '100%' }}
            >
              Check Another Reference
            </button>

            {statusResult.status === 'APPROVED' && (
              <button 
                className="login-button" 
                onClick={() => navigate('/login')} 
                style={{ marginTop: '12px', width: '100%' }}
              >
                Go to Login
              </button>
            )}
          </div>
        )}

        <div style={{ textAlign: 'center', marginTop: '24px' }}>
          <button onClick={() => navigate('/')} style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '14px', textDecoration: 'underline' }}>
            Return to Home
          </button>
        </div>
      </div>
    </div>
  );
};

export default Reference;