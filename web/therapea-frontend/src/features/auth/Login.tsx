import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import "./Login.css";

const Login: React.FC = () => {
  const navigate = useNavigate();
  const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string) || 'http://localhost:8083';

  // Base Login State
  const [formData, setFormData]     = useState({ email: '', password: '' });
  const [error, setError]           = useState('');
  const [success, setSuccess]       = useState('');
  const [isLoading, setIsLoading]   = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  
  // Google OTP State
  const [googleEmail, setGoogleEmail] = useState('');
  const [showOtpFlow, setShowOtpFlow] = useState(false);
  const [otpCode, setOtpCode]         = useState('');
  const [storedIdToken, setStoredIdToken] = useState(''); // Holds the token string for multi-step verification

  // FORGOT PASSWORD MODAL STATE
  const [forgotStep, setForgotStep] = useState(0); 
  const [forgotEmail, setForgotEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [modalError, setModalError] = useState('');
  const [modalSuccess, setModalSuccess] = useState('');

  const parseError = async (res: Response, preParsedData?: any) => {
    try {
      const data = preParsedData || await res.json();
      return data.error || data.message || "Something went wrong. Please try again.";
    } catch {
      return "Connection error. Please try again.";
    }
  };

  const handleRoleBasedNavigation = (role: string) => {
    if (role === 'ADMIN') navigate('/admin', { replace: true });
    else navigate('/dashboard', { replace: true });
  };

  useEffect(() => {
    const rawUser = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (rawUser) {
      handleRoleBasedNavigation(JSON.parse(rawUser).role);
      return;
    }

    const params  = new URLSearchParams(window.location.search);
    const gEmail  = params.get('googleEmail');
    const err     = params.get('error');

    window.history.replaceState({}, document.title, '/login');

    if (err === 'cancelled') return;
    if (err) { setError('Google sign-in failed. Please try again.'); return; }

    if (gEmail) {
      setGoogleEmail(gEmail);
      sendOtp(gEmail, 'LOGIN');
    }
  }, [navigate]);

  const sendOtp = async (email: string, type: 'LOGIN' | 'FORGOT_PASSWORD') => {
    setIsLoading(true);
    if (type === 'LOGIN') { setError(''); setSuccess(''); } 
    else { setModalError(''); setModalSuccess(''); }
    
    try {
      const res  = await fetch(`${API_BASE_URL}/api/auth/send-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, type }),
      });
      
      const data = await res.json().catch(() => ({}));

      if (res.ok) {
        if (type === 'LOGIN') {
          setShowOtpFlow(true);
          setSuccess(data.message || `Verification code sent to ${email}`);
        } else {
          if (data.requiresOtp === false) {
            setForgotStep(3); 
            setModalSuccess('');
          } else {
            setForgotStep(2); 
            setModalSuccess(data.message || `Code sent to ${email}`);
          }
        }
      } else {
        const errorMsg = data.error || data.message || "Connection error. Please try again.";
        if (type === 'LOGIN') setError(errorMsg); else setModalError(errorMsg);
      }
    } catch {
      const msg = 'Network error. Please try again.';
      if (type === 'LOGIN') setError(msg); else setModalError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  // ─── LOGIN HANDLERS ───
  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true); setError(''); setSuccess('');
    try {
      // 1. Verify that the user input matches the issued OTP record transaction
      const verifyRes  = await fetch(`${API_BASE_URL}/api/auth/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: googleEmail, otp: otpCode }),
      });

      if (!verifyRes.ok) { setError(await parseError(verifyRes)); setIsLoading(false); return; }

      // 2. Route back to complete authentication using the token fallback reference context
      const userRes = await fetch(`${API_BASE_URL}/api/auth/google-check`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken: storedIdToken })
      });
      
      if (userRes.ok) {
        const sessionData = await userRes.json(); 
        if (rememberMe) localStorage.setItem('user', JSON.stringify(sessionData));
        else {
          sessionStorage.setItem('user', JSON.stringify(sessionData));
          sessionStorage.setItem('sessionStart', Date.now().toString());
        }
        setSuccess('Login successful! Redirecting...');
        setTimeout(() => handleRoleBasedNavigation(sessionData.role), 1500);
      } else {
        setError(await parseError(userRes));
      }
    } catch { setError('Connection error handling profile mapping.'); } 
    finally { setIsLoading(false); }
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(''); setSuccess(''); setIsLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ formData }),
      });
      
      const data = await res.json().catch(() => ({}));

      if (data.requiresOtp) {
        setGoogleEmail(data.email || formData.email);
        setShowOtpFlow(true);
        setSuccess(data.message || `Verification code sent to your email.`);
        setIsLoading(false);
        return;
      }

      if (res.ok) {
        if (rememberMe) localStorage.setItem('user', JSON.stringify(data));
        else {
          sessionStorage.setItem('user', JSON.stringify(data));
          sessionStorage.setItem('sessionStart', Date.now().toString());
        }
        setSuccess('Login successful! Redirecting...');
        setTimeout(() => handleRoleBasedNavigation(data.role), 1500);
      } else { 
        setError(await parseError(res, data)); 
      }
    } catch { setError('Connection error.'); } 
    finally { setIsLoading(false); }
  };

  const handleGoogleCredentialResponse = async (response: any) => {
    setIsLoading(true); setError(''); setSuccess('');
    try {
      const token = response.credential;
      setStoredIdToken(token); // Save the signature globally for step 2 verification parsing

      const res = await fetch(`${API_BASE_URL}/api/auth/google-check`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken: token }),
      });

      const data = await res.json();

      // If the backend indicates an initial successful profile match, intercept and fire the OTP prompt chain
      if (res.ok || (res.status === 200 && data.email)) {
        setGoogleEmail(data.email);
        sendOtp(data.email, 'LOGIN');
      } else if (res.status === 404 && data.email) {
        // Handle unmapped accounts context cleanly
        setGoogleEmail(data.email);
        sendOtp(data.email, 'LOGIN');
      } else {
        setError(data.error || 'Google verification routing error.');
      }
    } catch {
      setError('Failed to process secure Google validation sequence cascade.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if ((window as any).google) {
      (window as any).google.accounts.id.initialize({
        client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID || "275088762622-rehu8gq0gi8m0fhspele0dp7q2g4bg3d.apps.googleusercontent.com",
        callback: handleGoogleCredentialResponse,
        ux_mode: "popup",
        itp_support: true
      });
    }
  }, [API_BASE_URL]); 

  const triggerGooglePopup = () => {
    if ((window as any).google) {
      (window as any).google.accounts.id.prompt((notification: any) => {
        if (notification.isSkippedMoment() || notification.isDismissedMoment()) {
          (window as any).google.accounts.id.prompt();
        }
      });
    } else {
      setError("Google SDK failed to load. Please refresh the page.");
    }
  };

  // ─── FORGOT PASSWORD MODAL HANDLERS ───
  const handleForgotSubmitEmail = (e: React.FormEvent) => {
    e.preventDefault();
    if (!forgotEmail) { setModalError('Please enter an email address.'); return; }
    sendOtp(forgotEmail, 'FORGOT_PASSWORD');
  };

  const handleForgotVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true); setModalError(''); setModalSuccess('');
    try {
      const verifyRes  = await fetch(`${API_BASE_URL}/api/auth/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: forgotEmail, otp: otpCode }),
      });

      if (!verifyRes.ok) {
        setModalError(await parseError(verifyRes));
      } else {
        setForgotStep(3); 
        setModalSuccess('');
        setOtpCode('');
      }
    } catch { setModalError('Network error verifying code.'); } 
    finally { setIsLoading(false); }
  };

  const handleForgotSetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) { setModalError("Passwords do not match."); return; }
    if (newPassword.length < 6) { setModalError("Password must be at least 6 characters."); return; }

    setIsLoading(true); setModalError(''); setModalSuccess('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: forgotEmail, newPassword: newPassword }),
      });
      
      if (res.ok) {
        closeModal(); 
        setSuccess('Password updated successfully! You can now sign in.');
      } else { setModalError(await parseError(res)); }
    } catch { setModalError('Network error updating password.'); } 
    finally { setIsLoading(false); }
  };

  const closeModal = () => {
    setForgotStep(0); setModalError(''); setModalSuccess('');
    setForgotEmail(''); setOtpCode(''); setNewPassword(''); setConfirmPassword('');
  };

  const handleBackToLogin = () => {
    setShowOtpFlow(false);
    setError(''); 
    setSuccess(''); 
    setOtpCode('');
  };

  const CheckIcon = () => (
    <svg width="10" height="8" viewBox="0 0 12 10" fill="none"><path d="M1 5l3.5 3.5L11 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
  );

  return (
    <>
      <style>{`
        .tp-modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 9999; }
        .tp-modal-card { background: #fff; width: 90%; max-width: 440px; padding: 32px 32px 40px; border-radius: 24px; position: relative; box-shadow: 0 20px 40px rgba(0,0,0,0.15); animation: popIn 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
        .tp-modal-close { position: absolute; top: 20px; right: 20px; background: none; border: none; font-size: 24px; color: #94a3b8; cursor: pointer; transition: 0.2s; line-height: 1; padding: 4px; border-radius: 50%; }
        .tp-modal-close:hover { color: #0f172a; background: #f1f5f9; }
        .tp-modal-title { margin: 0 0 12px 0; color: #0f172a; font-size: 26px; font-weight: 700; font-family: 'Lora', serif; }
        .tp-modal-desc { margin: 0 0 24px 0; color: #475569; font-size: 15px; line-height: 1.5; }
        .tp-modal-input { width: 100%; padding: 14px 16px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; font-size: 15px; color: #1e293b; outline: none; transition: 0.2s; box-sizing: border-box; margin-bottom: 20px; }
        .tp-modal-input:focus { border-color: #7A9E78; box-shadow: 0 0 0 3px rgba(122,158,120,0.15); background: #fff; }
        .tp-modal-btn { width: 100%; background: #7A9E78; color: white; border: none; padding: 14px; border-radius: 12px; font-weight: 600; font-size: 15px; cursor: pointer; transition: 0.2s; box-shadow: 0 4px 12px rgba(122,158,120,0.25); }
        .tp-modal-btn:hover { background: #527050; transform: translateY(-1px); }
        .tp-modal-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }
        @keyframes popIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
      `}</style>

      {/* ─── FORGOT PASSWORD MODAL ─── */}
      {forgotStep > 0 && (
        <div className="tp-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) closeModal(); }}>
          <div className="tp-modal-card">
            <button type="button" className="tp-modal-close" onClick={closeModal}>×</button>
            
            {forgotStep === 1 && (
              <form onSubmit={handleForgotSubmitEmail}>
                <h2 className="tp-modal-title">Reset Password</h2>
                <p className="tp-modal-desc">Enter your registered email address and we'll verify it in the system.</p>
                {modalError && <div className="error-message" style={{ marginBottom: '16px' }}>{modalError}</div>}
                <input type="email" placeholder="Email address" className="tp-modal-input" value={forgotEmail} onChange={e => setForgotEmail(e.target.value)} required />
                <button type="submit" className="tp-modal-btn" disabled={isLoading}>{isLoading ? 'Sending...' : 'Proceed'}</button>
              </form>
            )}

            {forgotStep === 2 && (
              <form onSubmit={handleForgotVerifyOtp}>
                <h2 className="tp-modal-title">Verify Email</h2>
                <p className="tp-modal-desc">Enter the 6-digit code sent to your email <strong>{forgotEmail}</strong></p>
                {modalError && <div className="error-message" style={{ marginBottom: '16px' }}>{modalError}</div>}
                {modalSuccess && <div className="success-message" style={{ marginBottom: '16px' }}>{modalSuccess}</div>}
                <input type="text" placeholder="000000" className="tp-modal-input" maxLength={6} style={{ textAlign: 'center', fontSize: '24px', letterSpacing: '12px', fontWeight: 600 }} value={otpCode} onChange={e => setOtpCode(e.target.value.replace(/[^0-9]/g, ''))} required />
                <button type="submit" className="tp-modal-btn" disabled={isLoading || otpCode.length < 6}>{isLoading ? 'Verifying...' : 'Verify Code'}</button>
                <div style={{ textAlign: 'center', marginTop: '16px' }}>
                  <button type="button" onClick={() => sendOtp(forgotEmail, 'FORGOT_PASSWORD')} style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', textDecoration: 'underline' }}>Resend Code</button>
                </div>
              </form>
            )}

            {forgotStep === 3 && (
              <form onSubmit={handleForgotSetPassword}>
                <h2 className="tp-modal-title">Create New Password</h2>
                <p className="tp-modal-desc">Please securely create a new password.</p>
                {modalError && <div className="error-message" style={{ marginBottom: '16px' }}>{modalError}</div>}
                <input type="password" placeholder="New Password (min 6 chars)" className="tp-modal-input" style={{ marginBottom: '12px' }} value={newPassword} onChange={e => setNewPassword(e.target.value)} minLength={6} required />
                <input type="password" placeholder="Confirm New Password" className="tp-modal-input" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} minLength={6} required />
                <button type="submit" className="tp-modal-btn" disabled={isLoading}>{isLoading ? 'Updating...' : 'Update Password'}</button>
              </form>
            )}
          </div>
        </div>
      )}

      {/* ─── MAIN LOGIN SCREEN ─── */}
      <div className="login-container">
        <div className="login-left">
          <div className="left-logo">
            <div className="left-logo-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
              </svg>
            </div>
            <span className="left-logo-text">TheraPea</span>
          </div>
          <div className="left-content">
            {showOtpFlow ? (
              <>
                <h2>Verify your identity</h2>
                <p>For your security, please complete the two-step verification process to access your dashboard.</p>
              </>
            ) : (
              <>
                <h2>Continue your wellness journey</h2>
                <p>Sign in to connect with your care team, track your progress, and attend your sessions.</p>
                {['Secure HIPAA-compliant platform', 'Licensed mental health professionals', 'Flexible scheduling, any time'].map(f => (
                  <div key={f} className="left-feature">
                    <div className="left-feature-dot"><CheckIcon /></div>
                    <span>{f}</span>
                  </div>
                ))}
              </>
            )}
          </div>
        </div>

        <div className="login-right">
          <div className="login-card">
            <div className="login-header">
              <div className="login-logo">
                <div className="login-logo-icon">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                    <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
                  </svg>
                </div>
                <span className="login-logo-text">Thera<span>Pea</span></span>
              </div>
              
              {showOtpFlow ? (
                <><h1 className="login-title">Verify Your Identity</h1><p className="login-subtitle">Enter the verification code to sign in as <strong>{googleEmail}</strong></p></>
              ) : (
                <><h1 className="login-title">Welcome Back</h1><p className="login-subtitle">Sign in to your account to continue your wellness journey</p></>
              )}
            </div>

            {error   && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            {showOtpFlow ? (
              <form onSubmit={handleVerifyOtp} className="login-form">
                <div className="form-group">
                  <label className="form-label">Verification Code (OTP)</label>
                  <input type="text" value={otpCode} onChange={(e) => setOtpCode(e.target.value.replace(/[^0-9]/g, ''))} placeholder="Enter 6-digit code" className="form-input" style={{ textAlign: 'center', fontSize: '20px', letterSpacing: '8px', fontWeight: 600 }} maxLength={6} required />
                </div>
                <button type="submit" className="login-button" disabled={isLoading || otpCode.length < 6}>{isLoading ? 'Verifying…' : 'Verify & Sign In'}</button>
                <div style={{ marginTop: '12px', textAlign: 'center' }}>
                  <button type="button" className="back-button" onClick={() => { setError(''); setSuccess(''); sendOtp(googleEmail, 'LOGIN'); }} disabled={isLoading}>Resend OTP</button>
                </div>
                <div style={{ marginTop: '16px', textAlign: 'center' }}>
                  <button type="button" onClick={handleBackToLogin} style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '14px', fontWeight: 500 }}>← Back to Login</button>
                </div>
              </form>
            ) : (
              <>
                <form onSubmit={handleLogin} className="login-form">
                  <div className="form-group">
                    <label htmlFor="email" className="form-label">Email Address</label>
                    <input type="email" id="email" name="email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} placeholder="Enter your email" className="form-input" required />
                  </div>
                  <div className="form-group">
                    <label htmlFor="password" className="form-label">Password</label>
                    <input type="password" id="password" name="password" value={formData.password} onChange={(e) => setFormData({ ...formData, password: e.target.value })} placeholder="Enter your password" className="form-input" required />
                    <div className="form-footer">
                      <button type="button" onClick={() => { setError(''); setSuccess(''); setForgotStep(1); }} className="forgot-link" style={{ background: 'none', border: 'none', padding: 0, font: 'inherit', cursor: 'pointer' }}>
                        Forgot password?
                      </button>
                    </div>
                  </div>
                  <div className="form-group">
                    <label className="checkbox-label">
                      <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
                      &nbsp;Remember me
                    </label>
                  </div>
                  <button type="submit" className="login-button" disabled={isLoading}>
                    {isLoading ? 'Signing in…' : 'Sign In'}
                  </button>
                </form>

                <div className="divider">
                  <div className="divider-line"/>
                  <span className="divider-text">Or</span>
                  <div className="divider-line"/>
                </div>

                {/* Clean, fully integrated custom HTML button layout matching your theme configuration styles */}
                <button type="button" onClick={triggerGooglePopup} className="google-button" disabled={isLoading}>
                  <svg className="google-icon" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                  </svg>
                  {isLoading ? 'Connecting...' : 'Sign in with Google'}
                </button>

                <p className="signup-link">
                  Don't have an account?{' '}
                  <button type="button" onClick={() => navigate('/register')} style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}>
                    Sign up
                  </button>
                </p>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
};

export default Login;