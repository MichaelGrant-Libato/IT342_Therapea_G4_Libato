import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import "../styles/Login.css";

const Login: React.FC = () => {
  const navigate = useNavigate();
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8083';

  const [formData, setFormData]     = useState({ email: '', password: '' });
  const [error, setError]           = useState('');
  const [success, setSuccess]       = useState('');
  const [isLoading, setIsLoading]   = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [googleEmail, setGoogleEmail] = useState('');
  const [showOtpFlow, setShowOtpFlow] = useState(false);
  const [otpCode, setOtpCode]         = useState('');

  // ==========================================
  // SMART ERROR PARSER
  // ==========================================
  const parseError = async (res: Response) => {
    try {
      const data = await res.json();
      return data.error || data.message || "Something went wrong. Please try again.";
    } catch {
      return "We are having trouble connecting. Please check your internet and try again.";
    }
  };

  // ✅ Helper to route users based on role
  const handleRoleBasedNavigation = (role: string) => {
    if (role === 'ADMIN') {
      navigate('/admin', { replace: true });
    } else {
      navigate('/dashboard', { replace: true });
    }
  };

  useEffect(() => {
    const rawUser = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (rawUser) {
      const user = JSON.parse(rawUser);
      handleRoleBasedNavigation(user.role);
      return;
    }
  }, [navigate]);

  useEffect(() => {
    window.history.pushState(null, '', window.location.href);
    const handlePopState = () => {
      window.history.pushState(null, '', window.location.href);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    const params  = new URLSearchParams(window.location.search);
    const gEmail  = params.get('googleEmail');
    const err     = params.get('error');

    window.history.replaceState({}, document.title, '/login');

    if (err === 'cancelled') return;
    if (err) { setError('Google sign-in failed. Please try again.'); return; }

    if (gEmail) {
      setGoogleEmail(gEmail);
      sendOtp(gEmail);
    }
  }, []);

  const sendOtp = async (email: string) => {
    setIsLoading(true);
    setError('');
    try {
      const res  = await fetch(`${API_BASE_URL}/api/auth/send-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, type: 'LOGIN' }),
      });
      if (res.ok) {
        setShowOtpFlow(true);
        setSuccess(`Verification code sent to ${email}`);
      } else {
        setError(await parseError(res));
      }
    } catch {
      setError('We are having trouble connecting. Please check your internet and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');
    try {
      // 1. Verify the OTP First
      const verifyRes  = await fetch(`${API_BASE_URL}/api/auth/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: googleEmail, otp: otpCode }),
      });

      if (!verifyRes.ok) {
        setError(await parseError(verifyRes));
        setIsLoading(false);
        return;
      }

      // ✅ 2. THE FIX: Route through the official google-login endpoint to hit the Bouncer Check!
      const userRes = await fetch(`${API_BASE_URL}/api/auth/google-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: googleEmail })
      });
      
      if (userRes.ok) {
        const sessionData = await userRes.json(); 

        if (rememberMe) {
          localStorage.setItem('user', JSON.stringify(sessionData));
        } else {
          sessionStorage.setItem('user', JSON.stringify(sessionData));
          sessionStorage.setItem('sessionStart', Date.now().toString());
        }

        setSuccess('Login successful! Redirecting...');
        setTimeout(() => handleRoleBasedNavigation(sessionData.role), 1500);
      } else {
        // ✅ If the user is a PENDING or REJECTED doctor, the backend will throw an error here,
        // and the frontend will display it perfectly without logging them in!
        setError(await parseError(userRes));
      }
    } catch {
      setError('We are having trouble connecting. Please check your internet and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(''); setSuccess(''); setIsLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      
      if (res.ok) {
        const data = await res.json();
        if (rememberMe) {
          localStorage.setItem('user', JSON.stringify(data));
        } else {
          sessionStorage.setItem('user', JSON.stringify(data));
          sessionStorage.setItem('sessionStart', Date.now().toString());
        }
        setSuccess('Login successful! Redirecting...');
        setTimeout(() => handleRoleBasedNavigation(data.role), 1500);
      } else {
        setError(await parseError(res));
      }
    } catch {
      setError('We are having trouble connecting. Please check your internet and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setIsLoading(true); setError('');
    try {
      const res  = await fetch(`${API_BASE_URL}/api/auth/google-register-url`);
      if (res.ok) {
        const data = await res.json();
        sessionStorage.setItem('oauth_state', data.state);

        const popup = window.open(
          data.url,
          'google-oauth',
          'width=500,height=600,scrollbars=yes,resizable=yes'
        );

        if (!popup) {
          setError('Your browser blocked the popup. Please allow popups for this site.');
          setIsLoading(false);
          return;
        }

        const handleMessage = async (event: MessageEvent) => {
          if (event.origin !== API_BASE_URL) return;
          window.removeEventListener('message', handleMessage);
          setIsLoading(false);

          const { type, email } = event.data;

          if (type === 'error') {
            setError('Google sign-in failed. Please try again.');
            return;
          }

          if (type === 'existing') {
            setGoogleEmail(email);
            sendOtp(email);
            return;
          }

          if (type === 'new') {
            navigate(`/register?email=${encodeURIComponent(email)}&name=${encodeURIComponent(event.data.name || '')}`);
          }
        };

        window.addEventListener('message', handleMessage);

        const pollClosed = setInterval(() => {
          if (popup.closed) {
            clearInterval(pollClosed);
            window.removeEventListener('message', handleMessage);
            setIsLoading(false);
          }
        }, 500);

      } else {
        setError(await parseError(res));
        setIsLoading(false);
      }
    } catch {
      setError('Failed to connect to Google. Please check your internet connection.');
      setIsLoading(false);
    }
  };

  const CheckIcon = () => (
    <svg width="10" height="8" viewBox="0 0 12 10" fill="none">
      <path d="M1 5l3.5 3.5L11 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );

  if (showOtpFlow) {
    return (
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
            <h2>Continue your wellness journey</h2>
            <p>Verify your identity to securely access your account.</p>
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
              <h1 className="login-title">Verify Your Identity</h1>
              <p className="login-subtitle">
                Enter the OTP to sign in as <strong>{googleEmail}</strong>
              </p>
            </div>

            {error   && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            <form onSubmit={handleVerifyOtp} className="login-form">
              <div className="form-group">
                <label htmlFor="otp" className="form-label">One-Time Password (OTP)</label>
                <input type="text" id="otp" value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/[^0-9]/g, ''))}
                  placeholder="Enter 6-digit code" className="form-input"
                  style={{ textAlign: 'center', fontSize: '20px', letterSpacing: '8px', fontWeight: 600 }}
                  maxLength={6} required />
              </div>
              <div className="form-group">
                <label className="checkbox-label">
                  <input type="checkbox" checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)} />
                  &nbsp;Remember me
                </label>
              </div>
              <button type="submit" className="login-button" disabled={isLoading || otpCode.length < 6}>
                {isLoading ? 'Verifying…' : 'Verify & Sign In'}
              </button>
            </form>

            <div style={{ marginTop: '12px', textAlign: 'center' }}>
              <button type="button" className="back-button"
                onClick={() => sendOtp(googleEmail)} disabled={isLoading}>
                Resend OTP
              </button>
            </div>
            <p className="signup-link" style={{ marginTop: '16px' }}>
              <button type="button" onClick={() => setShowOtpFlow(false)}
                style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', fontSize: '13px' }}>
                ← Back to Login
              </button>
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
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
          <h2>Continue your wellness journey</h2>
          <p>Sign in to connect with your care team, track your progress, and attend your sessions.</p>
          {['Secure HIPAA-compliant platform', 'Licensed mental health professionals', 'Flexible scheduling, any time'].map(f => (
            <div key={f} className="left-feature">
              <div className="left-feature-dot"><CheckIcon /></div>
              <span>{f}</span>
            </div>
          ))}
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
            <h1 className="login-title">Welcome Back</h1>
            <p className="login-subtitle">Sign in to your account to continue your wellness journey</p>
          </div>

          {error   && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          <form onSubmit={handleLogin} className="login-form">
            <div className="form-group">
              <label htmlFor="email" className="form-label">Email Address</label>
              <input type="email" id="email" name="email" value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder="Enter your email" className="form-input" required />
            </div>
            <div className="form-group">
              <label htmlFor="password" className="form-label">Password</label>
              <input type="password" id="password" name="password" value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                placeholder="Enter your password" className="form-input" required />
              <div className="form-footer">
                <a href="#" className="forgot-link">Forgot password?</a>
              </div>
            </div>
            <div className="form-group">
              <label className="checkbox-label">
                <input type="checkbox" checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)} />
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

          <button onClick={handleGoogleLogin} className="google-button" disabled={isLoading}>
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
            <button type="button" onClick={() => navigate('/register')}
              style={{ background: 'none', border: 'none', color: 'inherit',
                cursor: 'pointer', textDecoration: 'underline', padding: 0 }}>
              Sign up
            </button>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;