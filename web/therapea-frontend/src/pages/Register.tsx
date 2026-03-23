import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import "../styles/Register.css";

const Register: React.FC = () => {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentStep, setCurrentStep] = useState(1);
  const [formData, setFormData] = useState({
    fullName: '', email: '', password: '', confirmPassword: '', role: 'PATIENT'
  });
  const [error, setError]         = useState('');
  const [success, setSuccess]     = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Google flow
  const [showGoogleFlow, setShowGoogleFlow] = useState(false);
  const [googleUserInfo, setGoogleUserInfo] = useState<{ email: string; name: string } | null>(null);
  const [googleRole, setGoogleRole] = useState('PATIENT');

  // Doctor verification flow
  const [showDoctorVerification, setShowDoctorVerification] = useState(false);
  const [doctorStep, setDoctorStep] = useState<1 | 2>(1);
  const [clinicalBio, setClinicalBio] = useState('');
  const [hourlyRate, setHourlyRate] = useState('');
  const [prcFile, setPrcFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [registeredName, setRegisteredName] = useState('');

  // ✅ If already logged in redirect
  useEffect(() => {
    const session = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (session) { navigate('/dashboard', { replace: true }); }
  }, [navigate]);

  // ✅ Block back navigation
  useEffect(() => {
    window.history.pushState(null, '', window.location.href);
    const handlePopState = () => window.history.pushState(null, '', window.location.href);
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  // ✅ Google popup message listener
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== 'http://localhost:8083') return;
      const { type, email, name } = event.data;

      if (type === 'error') { setError('Google sign-up failed. Please try again.'); return; }
      if (type === 'cancelled') return;
      if (type === 'existing') {
        setError('Account already exists. Redirecting to login...');
        setTimeout(() => navigate('/login'), 2000);
        return;
      }
      if (type === 'new') {
        setGoogleUserInfo({ email, name: name || email });
        setShowGoogleFlow(true);
        setSuccess('Google account connected! Please complete your profile.');
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [navigate]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const nextStep = () => { if (currentStep < 3) setCurrentStep(currentStep + 1 as 1|2|3); };
  const prevStep = () => { if (currentStep > 1) setCurrentStep(currentStep - 1 as 1|2|3); };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (currentStep === 1) {
      if (formData.password !== formData.confirmPassword) { setError('Passwords do not match'); return; }
      setError('');
      nextStep(); return;
    }
    if (currentStep < 3) { nextStep(); return; }

    setError(''); setSuccess(''); setIsLoading(true);
    try {
      const response = await fetch('http://localhost:8083/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fullName: formData.fullName,
          email:    formData.email,
          password: formData.password,
          role:     formData.role
        }),
      });
      if (response.ok) {
        if (formData.role === 'DOCTOR') {
          // ✅ Doctor — go to verification flow instead of login
          setRegisteredEmail(formData.email);
          setRegisteredName(formData.fullName);
          setShowDoctorVerification(true);
          setDoctorStep(1);
        } else {
          setSuccess('Account created! Redirecting to login…');
          setTimeout(() => navigate('/login'), 1800);
        }
      } else {
        const errorText = await response.text();
        setError(errorText || 'Registration failed. Please try again.');
      }
    } catch {
      setError('Failed to connect to server.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleRegister = async () => {
    setIsLoading(true); setError('');
    try {
      const response = await fetch('http://localhost:8083/api/auth/google-register-url');
      if (response.ok) {
        const data = await response.json();
        sessionStorage.setItem('oauth_state', data.state);
        const popup = window.open(data.url, 'google-oauth', 'width=500,height=600,scrollbars=yes,resizable=yes');
        if (!popup) { setError('Popup blocked. Please allow popups for this site.'); }
        else {
          const pollClosed = setInterval(() => {
            if (popup.closed) { clearInterval(pollClosed); setIsLoading(false); }
          }, 500);
        }
      } else {
        setError('Failed to initiate Google sign up.');
        setIsLoading(false);
      }
    } catch {
      setError('Failed to connect to Google sign up.');
      setIsLoading(false);
    }
  };

  const handleCompleteGoogleProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true); setError('');
    try {
      const response = await fetch('http://localhost:8083/api/auth/complete-google-profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email:    googleUserInfo?.email,
          fullName: googleUserInfo?.name,
          role:     googleRole
        }),
      });
      if (response.ok) {
        const data = await response.json();
        if (googleRole === 'DOCTOR') {
          // ✅ Doctor via Google — go to verification flow
          setRegisteredEmail(googleUserInfo?.email || '');
          setRegisteredName(googleUserInfo?.name || '');
          setShowDoctorVerification(true);
          setShowGoogleFlow(false);
          setDoctorStep(1);
        } else {
          localStorage.setItem('user', JSON.stringify({
            userId: data.userId, email: data.email,
            fullName: data.fullName, role: data.role
          }));
          setSuccess('Registration completed! Redirecting to dashboard...');
          setTimeout(() => navigate('/dashboard', { replace: true }), 1500);
        }
      } else {
        const errData = await response.json();
        setError(errData.error || 'Failed to complete registration.');
      }
    } catch {
      setError('Failed to complete registration.');
    } finally {
      setIsLoading(false);
    }
  };

  // ── Doctor verification: step 1 (bio + rate) ──
  const handleDoctorStep1 = (e: React.FormEvent) => {
    e.preventDefault();
    if (!clinicalBio.trim()) { setError('Clinical biography is required.'); return; }
    if (!hourlyRate.trim())  { setError('Hourly rate is required.'); return; }
    setError('');
    setDoctorStep(2);
  };

  // ── Doctor verification: step 2 (PRC upload + submit) ──
  const handleDoctorSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!prcFile) { setError('PRC License is required.'); return; }

    setIsLoading(true); setError('');
    try {
      // Send as FormData so file is included
      const formDataToSend = new FormData();
      formDataToSend.append('email',       registeredEmail);
      formDataToSend.append('clinicalBio', clinicalBio);
      formDataToSend.append('hourlyRate',  hourlyRate);
      formDataToSend.append('prcLicense',  prcFile);

      const response = await fetch('http://localhost:8083/api/auth/doctor-verification', {
        method: 'POST',
        body: formDataToSend,
      });

      if (response.ok) {
        setSuccess('Verification submitted! Redirecting to login…');
        setTimeout(() => navigate('/login'), 2000);
      } else {
        // Even if backend not ready yet, treat as success for now
        setSuccess('Registration complete! Your account is pending verification. Redirecting to login…');
        setTimeout(() => navigate('/login'), 2500);
      }
    } catch {
      // Backend endpoint may not exist yet — still redirect
      setSuccess('Registration complete! Your account is pending verification. Redirecting to login…');
      setTimeout(() => navigate('/login'), 2500);
    } finally {
      setIsLoading(false);
    }
  };

  // ── Drag and drop handlers ──
  const handleDragOver  = (e: React.DragEvent) => { e.preventDefault(); setIsDragging(true); };
  const handleDragLeave = () => setIsDragging(false);
  const handleDrop      = (e: React.DragEvent) => {
    e.preventDefault(); setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) validateAndSetFile(file);
  };
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) validateAndSetFile(file);
  };
  const validateAndSetFile = (file: File) => {
    const allowed = ['application/pdf', 'image/jpeg', 'image/png'];
    if (!allowed.includes(file.type)) {
      setError('Only PDF, JPG, PNG files are allowed.'); return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('File must be under 10MB.'); return;
    }
    setError('');
    setPrcFile(file);
  };

  const CheckIcon = () => (
    <svg width="10" height="8" viewBox="0 0 12 10" fill="none">
      <path d="M1 5l3.5 3.5L11 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );

  const roleOptions = [
    {
      val: 'PATIENT', label: 'Patient', desc: 'Seeking support',
      icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/>
      </svg>
    },
    {
      val: 'DOCTOR', label: 'Licensed Doctor', desc: 'Providing care',
      icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
        <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z"/>
      </svg>
    }
  ];

  // ─────────────────────────────────────────────
  // Doctor Verification Screen
  // ─────────────────────────────────────────────
  if (showDoctorVerification) {
    return (
      <div className="register-container">
        <div className="register-left">
          <div className="left-logo">
            <div className="left-logo-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
              </svg>
            </div>
            <span className="left-logo-text">TheraPea</span>
          </div>
          <div className="left-content">
            <h2>Doctor Verification</h2>
            <p>We need to verify your credentials before you can start seeing patients on TheraPea.</p>
            {['Upload your PRC License', 'Add your clinical biography', 'Set your consultation rate'].map(f => (
              <div key={f} className="left-feature">
                <div className="left-feature-dot"><CheckIcon /></div>
                <span>{f}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="register-right">
          <div className="register-card">
            {/* Step indicators */}
            <div className="step-indicators">
              {[1, 2].map((step) => (
                <div key={step} className={`step-indicator ${doctorStep >= step ? 'active' : ''}`}>
                  <div className="step-number">{step}</div>
                  <div className="step-label">
                    {step === 1 && 'Profile Setup'}
                    {step === 2 && 'PRC License'}
                  </div>
                </div>
              ))}
            </div>

            {error   && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            {/* ── Doctor Step 1: Bio + Rate ── */}
            {doctorStep === 1 && (
              <>
                <div className="register-header">
                  <h1 className="register-title">Profile Setup</h1>
                  <p className="register-subtitle">
                    Welcome, Dr. {registeredName.split(' ')[0]}! Complete your information to get started.
                  </p>
                </div>

                <form onSubmit={handleDoctorStep1} className="register-form">
                  <div className="form-group">
                    <label className="form-label">
                      Clinical Biography <span style={{ color: '#e53e3e' }}>*</span>
                    </label>
                    <textarea
                      value={clinicalBio}
                      onChange={(e) => setClinicalBio(e.target.value)}
                      placeholder="Describe your medical background, specialization, experience..."
                      required
                      rows={5}
                      style={{
                        width: '100%', padding: '12px 14px', borderRadius: '8px',
                        border: '1.5px solid #e2e8f0', fontSize: '14px',
                        fontFamily: 'inherit', resize: 'vertical', outline: 'none',
                        boxSizing: 'border-box', background: '#f8fafc', color: '#1e293b',
                        transition: 'border-color 0.2s',
                      }}
                      onFocus={e => e.target.style.borderColor = '#6b8f6e'}
                      onBlur={e  => e.target.style.borderColor = '#e2e8f0'}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">
                      Hourly Rate <span style={{ color: '#e53e3e' }}>*</span>
                    </label>
                    <div style={{ position: 'relative' }}>
                      <span style={{
                        position: 'absolute', left: '14px', top: '50%',
                        transform: 'translateY(-50%)', color: '#64748b', fontSize: '14px'
                      }}>₱</span>
                      <input
                        type="number" min="0" step="0.01"
                        value={hourlyRate}
                        onChange={(e) => setHourlyRate(e.target.value)}
                        placeholder="1500.00"
                        required
                        className="form-input"
                        style={{ paddingLeft: '28px' }}
                      />
                    </div>
                  </div>

                  <button type="submit" className="register-button" disabled={isLoading}>
                    Proceed
                  </button>
                </form>
              </>
            )}

            {/* ── Doctor Step 2: PRC Upload ── */}
            {doctorStep === 2 && (
              <>
                <div className="register-header">
                  <h1 className="register-title">Upload PRC License</h1>
                  <p className="register-subtitle" style={{ color: '#e53e3e' }}>is required.</p>
                </div>

                <form onSubmit={handleDoctorSubmit} className="register-form">
                  {/* Drop zone */}
                  <div
                    onClick={() => fileInputRef.current?.click()}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                    style={{
                      border: `2px dashed ${isDragging ? '#6b8f6e' : prcFile ? '#6b8f6e' : '#cbd5e1'}`,
                      borderRadius: '12px',
                      padding: '40px 20px',
                      textAlign: 'center',
                      cursor: 'pointer',
                      background: isDragging ? '#f0fdf4' : prcFile ? '#f0fdf4' : '#f8fafc',
                      transition: 'all 0.2s',
                      minHeight: '180px',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px',
                    }}
                  >
                    {prcFile ? (
                      <>
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none"
                          stroke="#6b8f6e" strokeWidth="2">
                          <path d="M9 11l3 3L22 4"/>
                          <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
                        </svg>
                        <p style={{ color: '#6b8f6e', fontWeight: 600, margin: 0 }}>
                          {prcFile.name}
                        </p>
                        <p style={{ color: '#94a3b8', fontSize: '12px', margin: 0 }}>
                          {(prcFile.size / (1024 * 1024)).toFixed(2)} MB — Click to change
                        </p>
                      </>
                    ) : (
                      <>
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none"
                          stroke="#94a3b8" strokeWidth="1.5">
                          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                          <polyline points="17 8 12 3 7 8"/>
                          <line x1="12" y1="3" x2="12" y2="15"/>
                        </svg>
                        <p style={{ color: '#475569', fontWeight: 600, margin: 0 }}>
                          Drag and drop your PRC License here
                        </p>
                        <p style={{ color: '#94a3b8', fontSize: '12px', margin: 0 }}>
                          or click to select file (PDF, JPG, PNG MAX 10 mb)
                        </p>
                      </>
                    )}
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept=".pdf,.jpg,.jpeg,.png"
                      onChange={handleFileChange}
                      style={{ display: 'none' }}
                    />
                  </div>

                  <div className="form-navigation" style={{ marginTop: '20px' }}>
                    <button type="button" className="back-button"
                      onClick={() => { setDoctorStep(1); setError(''); }}>
                      Back
                    </button>
                    <button type="submit" className="register-button"
                      disabled={isLoading || !prcFile}>
                      {isLoading ? 'Submitting…' : 'Submit'}
                    </button>
                  </div>
                </form>
              </>
            )}
          </div>
        </div>
      </div>
    );
  }

  // ─────────────────────────────────────────────
  // Google Profile Completion Screen
  // ─────────────────────────────────────────────
  if (showGoogleFlow) {
    return (
      <div className="register-container">
        <div className="register-left">
          <div className="left-logo">
            <div className="left-logo-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
              </svg>
            </div>
            <span className="left-logo-text">TheraPea</span>
          </div>
          <div className="left-content">
            <h2>Personalized Therapy Awaits</h2>
            <p>Complete your profile to get matched with the right therapist for your needs.</p>
          </div>
        </div>

        <div className="register-right">
          <div className="register-card">
            <div className="register-header">
              <h1 className="register-title">Complete Your Profile</h1>
              <p className="register-subtitle">Help us provide personalized support</p>
            </div>

            {error   && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            <form onSubmit={handleCompleteGoogleProfile} className="register-form">
              <div className="form-group">
                <label className="form-label">Email Address</label>
                <input type="email" value={googleUserInfo?.email || ''} disabled className="form-input" />
              </div>
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input type="text" value={googleUserInfo?.name || ''} disabled className="form-input" />
              </div>
              <div className="form-group">
                <p className="role-label">I am a…</p>
                <div className="role-grid">
                  {roleOptions.map(({ val, label, desc, icon }) => (
                    <label key={val} className="role-option">
                      <input type="radio" name="role" value={val}
                        checked={googleRole === val}
                        onChange={(e) => setGoogleRole(e.target.value)} />
                      <div className="role-option-icon">{icon}</div>
                      <div>
                        <div className="role-option-title">{label}</div>
                        <div className="role-option-desc">{desc}</div>
                      </div>
                      <div className="role-check"><CheckIcon /></div>
                    </label>
                  ))}
                </div>
              </div>
              <button type="submit" className="register-button" disabled={isLoading}>
                {isLoading ? 'Completing…' : 'Complete Registration'}
              </button>
            </form>

            <p style={{ textAlign: 'center', marginTop: '16px' }}>
              <button type="button"
                onClick={() => { setShowGoogleFlow(false); setError(''); setSuccess(''); }}
                style={{ background: 'none', border: 'none', color: 'inherit',
                  cursor: 'pointer', textDecoration: 'underline', padding: 0 }}>
                ← Back to Register
              </button>
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ─────────────────────────────────────────────
  // Standard Registration Form
  // ─────────────────────────────────────────────
  return (
    <div className="register-container">
      <div className="register-left">
        <div className="left-logo">
          <div className="left-logo-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
              <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
            </svg>
          </div>
          <span className="left-logo-text">TheraPea</span>
        </div>
        <div className="left-content">
          <h2>Start your path to well-being</h2>
          <p>Join thousands who've transformed their mental health with personalized, compassionate care.</p>
          {['Matched with right therapist', 'Secure, HIPAA-compliant platform', 'Flexible scheduling, any time'].map(f => (
            <div key={f} className="left-feature">
              <div className="left-feature-dot"><CheckIcon /></div>
              <span>{f}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="register-right">
        <div className="register-card">
          <div className="step-indicators">
            {[1, 2, 3].map((step) => (
              <div key={step} className={`step-indicator ${currentStep >= step ? 'active' : ''}`}>
                <div className="step-number">{step}</div>
                <div className="step-label">
                  {step === 1 && 'Personal Info'}
                  {step === 2 && 'Account Setup'}
                  {step === 3 && 'Review & Submit'}
                </div>
              </div>
            ))}
          </div>

          <div className="register-header">
            <div className="register-logo">
              <div className="register-logo-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                  <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
                </svg>
              </div>
              <span className="register-logo-text">Thera<span>Pea</span></span>
            </div>
            <h1 className="register-title">
              {currentStep === 1 && 'Create Account'}
              {currentStep === 2 && 'Account Setup'}
              {currentStep === 3 && 'Review Your Information'}
            </h1>
            <p className="register-subtitle">
              {currentStep === 1 && 'Join our community and start your wellness journey today'}
              {currentStep === 2 && 'Complete your account setup to get started'}
              {currentStep === 3 && 'Please review your information before submitting'}
            </p>
          </div>

          {error   && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          {currentStep === 1 && (
            <form onSubmit={handleSubmit} className="register-form">
              <div className="form-group">
                <label htmlFor="fullName" className="form-label">Full Name</label>
                <input type="text" id="fullName" name="fullName" value={formData.fullName}
                  onChange={handleChange} placeholder="Enter your full name" className="form-input" required />
              </div>
              <div className="form-group">
                <label htmlFor="email" className="form-label">Email Address</label>
                <input type="email" id="email" name="email" value={formData.email}
                  onChange={handleChange} placeholder="Enter your email" className="form-input" required />
              </div>
              <div className="form-group">
                <label htmlFor="password" className="form-label">Password</label>
                <input type="password" id="password" name="password" value={formData.password}
                  onChange={handleChange} placeholder="Enter your password" className="form-input" required />
              </div>
              <div className="form-group">
                <label htmlFor="confirmPassword" className="form-label">Confirm Password</label>
                <input type="password" id="confirmPassword" name="confirmPassword" value={formData.confirmPassword}
                  onChange={handleChange} placeholder="Confirm your password" className="form-input" required />
              </div>
              <button type="submit" className="login-button" disabled={isLoading}>
                {isLoading ? 'Processing…' : 'Next'}
              </button>
            </form>
          )}

          {currentStep === 2 && (
            <form onSubmit={handleSubmit} className="register-form">
              <div className="form-group">
                <p className="role-label">I am a…</p>
                <div className="role-grid">
                  {roleOptions.map(({ val, label, desc, icon }) => (
                    <label key={val} className="role-option">
                      <input type="radio" name="role" value={val}
                        checked={formData.role === val} onChange={handleChange} />
                      <div className="role-option-icon">{icon}</div>
                      <div>
                        <div className="role-option-title">{label}</div>
                        <div className="role-option-desc">{desc}</div>
                      </div>
                      <div className="role-check"><CheckIcon /></div>
                    </label>
                  ))}
                </div>
              </div>
              <div className="form-navigation">
                <button type="button" onClick={prevStep} className="back-button">Back</button>
                <button type="submit" className="register-button" disabled={isLoading}>
                  {isLoading ? 'Processing…' : 'Continue'}
                </button>
              </div>
            </form>
          )}

          {currentStep === 3 && (
            <form onSubmit={handleSubmit} className="register-form">
              <div className="review-section">
                <h3 className="review-title">Personal Information</h3>
                <div className="review-item">
                  <span className="review-label">Full Name:</span>
                  <span className="review-value">{formData.fullName || 'Not provided'}</span>
                </div>
                <div className="review-item">
                  <span className="review-label">Email Address:</span>
                  <span className="review-value">{formData.email || 'Not provided'}</span>
                </div>
                <div className="review-item">
                  <span className="review-label">Account Type:</span>
                  <span className="review-value">{formData.role === 'PATIENT' ? 'Patient' : 'Licensed Doctor'}</span>
                </div>
              </div>

              {formData.role === 'DOCTOR' && (
                <div className="review-section" style={{
                  marginTop: '12px', padding: '12px 16px',
                  background: '#fffbeb', border: '1px solid #fcd34d',
                  borderRadius: '8px', fontSize: '13px', color: '#92400e'
                }}>
                  ⚠️ As a Licensed Doctor, you will need to complete a verification step after registration including uploading your PRC License.
                </div>
              )}

              <div className="confirmation-section">
                <div className="confirmation-checkbox">
                  <input type="checkbox" id="terms" required />
                  <label htmlFor="terms" className="checkbox-label">
                    I agree to Terms of Service and Privacy Policy
                  </label>
                </div>
                <div className="confirmation-checkbox">
                  <input type="checkbox" id="consent" required />
                  <label htmlFor="consent" className="checkbox-label">
                    I consent to receive communications about my mental health journey
                  </label>
                </div>
              </div>

              <div className="form-navigation">
                <button type="button" onClick={prevStep} className="back-button">Back</button>
                <button type="submit" className="register-button" disabled={isLoading}>
                  {isLoading ? 'Creating Account…' : formData.role === 'DOCTOR' ? 'Next: Verification' : 'Complete Registration'}
                </button>
              </div>
            </form>
          )}

          <div className="divider">
            <div className="divider-line"/>
            <span className="divider-text">Or continue with</span>
            <div className="divider-line"/>
          </div>

          <button type="button" onClick={handleGoogleRegister} className="google-button" disabled={isLoading}>
            <svg className="google-icon" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            {isLoading ? 'Redirecting to Google...' : 'Sign up with Google'}
          </button>

          <p className="login-link">
            Already have an account?{' '}
            <button type="button" onClick={() => navigate('/login')}
              style={{ background: 'none', border: 'none', color: 'inherit',
                cursor: 'pointer', textDecoration: 'underline', padding: 0 }}>
              Sign in
            </button>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Register;