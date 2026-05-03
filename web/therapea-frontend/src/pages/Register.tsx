import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import "../styles/Register.css";

const Register: React.FC = () => {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8083';

  // ─── Standard Registration State ───
  const [currentStep, setCurrentStep] = useState(1);
  const [formData, setFormData] = useState({
    fullName: '', email: '', password: '', confirmPassword: '', role: 'PATIENT'
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // ─── OTP / Google Flow State ───
  const [showOtpVerification, setShowOtpVerification] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [isGoogleOtpFlow, setIsGoogleOtpFlow] = useState(false); 

  const [showGoogleFlow, setShowGoogleFlow] = useState(false);
  const [googleStep, setGoogleStep] = useState<1 | 2>(1); // ✅ ADDED: To track Google flow steps
  const [googleUserInfo, setGoogleUserInfo] = useState<{ email: string; name: string } | null>(null);
  const [googleRole, setGoogleRole] = useState('PATIENT');

  // ─── Doctor Flow State ───
  const [showDoctorVerification, setShowDoctorVerification] = useState(false);
  const [doctorStep, setDoctorStep] = useState<1 | 2 | 3>(1); 
  const [clinicalBio, setClinicalBio] = useState('');
  const [hourlyRate, setHourlyRate] = useState('');
  const [prcFile, setPrcFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [registeredName, setRegisteredName] = useState('');
  const [referenceNumber, setReferenceNumber] = useState(''); 

  // ==========================================
  // SMART ERROR PARSER (NON-TECHY)
  // ==========================================
  const parseError = async (res: Response) => {
    if (res.status === 413) {
      return "Oops! Your file is a little too big. Please upload a document that is smaller than 10MB.";
    }
    try {
      const data = await res.json();
      return data.error || data.message || "Something went wrong. Please try again.";
    } catch {
      return "We are having trouble connecting. Please check your internet and try again.";
    }
  };

  // ==========================================
  // 1. GOOGLE FLOW (REQUIRES OTP)
  // ==========================================
  const requestOtpForGoogle = async (email: string) => {
    setError(''); setSuccess(''); setIsLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/send-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, type: 'REGISTER' }),
      });
      
      if (response.ok) {
        setSuccess(`Verification code sent to Gmail: ${email}`);
        setIsGoogleOtpFlow(true);
        setShowOtpVerification(true); 
      } else {
        setError(await parseError(response));
      }
    } catch {
      setError('We are having trouble connecting. Please check your internet and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const session = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (session) { navigate('/dashboard', { replace: true }); }
  }, [navigate]);

  useEffect(() => {
    window.history.pushState(null, '', window.location.href);
    const handlePopState = () => window.history.pushState(null, '', window.location.href);
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const email = params.get('email');
    const name = params.get('name');

    if (email) {
      window.history.replaceState({}, document.title, '/register');
      setGoogleUserInfo({ email, name: name || email });
      requestOtpForGoogle(email); 
    }
  }, []);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== API_BASE_URL) return;
      const { type, email, name } = event.data;

      if (type === 'error') { setError('Google sign-up failed. Please try again.'); return; }
      if (type === 'cancelled') return;

      if (type === 'existing') {
        setSuccess('You already have an account! Taking you to sign in…');
        setTimeout(() => navigate('/login'), 1800);
        return;
      }

      if (type === 'new') {
        setGoogleUserInfo({ email, name: name || email });
        requestOtpForGoogle(email); 
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [navigate, API_BASE_URL]);

  const handleVerifyOtpAndRegister = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsLoading(true); setError('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/auth/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: googleUserInfo?.email, otp: otpCode }),
      });

      if (res.ok) {
        setShowOtpVerification(false);
        setShowGoogleFlow(true); 
      } else {
        setError(await parseError(res));
      }
    } catch { 
      setError('We are having trouble connecting. Please check your internet and try again.'); 
    } finally { setIsLoading(false); }
  };

  const handleCompleteGoogleProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // ✅ FIX: Force the user to review their details before submitting
    if (googleStep === 1) {
      setGoogleStep(2);
      return;
    }

    if (googleRole === 'DOCTOR') {
      setRegisteredEmail(googleUserInfo!.email);
      setRegisteredName(googleUserInfo!.name);
      setShowGoogleFlow(false);
      setShowDoctorVerification(true);
    } else {
      setIsLoading(true); setError('');
      try {
        const res = await fetch(`${API_BASE_URL}/api/auth/finalize-google-profile`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ 
              email: googleUserInfo?.email, 
              fullName: googleUserInfo?.name, 
              role: googleRole, 
              password: "" 
          }),
        });
        if (res.ok) {
           // ✅ FIX: Do not log the patient in immediately. Send them to Login just like standard flow.
           setSuccess('Account created! Taking you to login...');
           setTimeout(() => navigate('/login'), 1500);
        } else {
           setError(await parseError(res));
        }
      } catch { 
        setError('Something went wrong finishing your profile. Please try again.'); 
      } finally { setIsLoading(false); }
    }
  };

  const handleGoogleRegister = async () => {
    setIsLoading(true); setError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/google-register-url`);
      if (response.ok) {
        const data = await response.json();
        sessionStorage.setItem('oauth_state', data.state);
        const popup = window.open(data.url, 'google-oauth', 'width=500,height=600,scrollbars=yes,resizable=yes');
        if (!popup) {
          setError('It looks like your browser blocked the popup. Please allow popups for this site.');
          setIsLoading(false);
        } else {
          const pollClosed = setInterval(() => {
            if (popup.closed) { clearInterval(pollClosed); setIsLoading(false); }
          }, 500);
        }
      } else {
        setError(await parseError(response));
        setIsLoading(false);
      }
    } catch {
      setError('Failed to connect to Google. Please check your internet connection.');
      setIsLoading(false);
    }
  };

  // ==========================================
  // 2. STANDARD FLOW (SKIPS OTP)
  // ==========================================
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const nextStep = () => { if (currentStep < 3) setCurrentStep(currentStep + 1 as 1|2|3); };
  const prevStep = () => { if (currentStep > 1) setCurrentStep(currentStep - 1 as 1|2|3); };

  const handleRegularSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (currentStep === 1) {
      if (formData.password !== formData.confirmPassword) { setError('Those passwords do not match. Please try typing them again.'); return; }
      setError(''); nextStep(); return;
    }
    if (currentStep === 2) { nextStep(); return; }

    if (formData.role === 'DOCTOR') {
      setRegisteredEmail(formData.email);
      setRegisteredName(formData.fullName);
      setShowDoctorVerification(true); 
    } else {
      registerPatient(); 
    }
  };

  const registerPatient = async () => {
    setIsLoading(true); setError('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (res.ok) {
        setSuccess('Account created! Taking you to login...');
        setTimeout(() => navigate('/login'), 1500);
      } else {
        setError(await parseError(res));
      }
    } catch {
      setError('We are having trouble connecting. Please check your internet and try again.');
    } finally { setIsLoading(false); }
  };

  // ==========================================
  // 3. DOCTOR UPLOAD FLOW (STRICT VALIDATION)
  // ==========================================
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) {
        setError('We only accept PDF, JPG, or PNG files. Please choose a different file format.');
        return;
      }
      if (file.size > 10 * 1024 * 1024) { 
        setError('Oops! This file is too big. Please choose a document smaller than 10MB.'); 
        return; 
      }
      setPrcFile(file);
      setError('');
    }
  };

  const handleDragOver = (e: React.DragEvent) => { e.preventDefault(); setIsDragging(true); };
  const handleDragLeave = () => setIsDragging(false);
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault(); setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) {
      if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) {
        setError('We only accept PDF, JPG, or PNG files. Please choose a different file format.');
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        setError('Oops! This file is too big. Please choose a document smaller than 10MB.');
        return;
      }
      setPrcFile(file);
      setError('');
    }
  };

  const handleDoctorSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!prcFile) { setError('Please upload your PRC License before continuing.'); return; }

    const MAX_FILE_SIZE = 10 * 1024 * 1024;
    if (prcFile.size > MAX_FILE_SIZE) {
      setError(`Your file is ${(prcFile.size / 1024 / 1024).toFixed(2)} MB, which is a bit too large. Please upload a file under 10MB.`);
      return;
    }

    setIsLoading(true); setError('');

    try {
      const isGoogle = isGoogleOtpFlow;
      const endpoint = isGoogle ? '/api/auth/register-google-doctor' : '/api/auth/register-doctor';

      const fd = new FormData();
      fd.append('fullName', isGoogle ? googleUserInfo!.name : formData.fullName);
      fd.append('email', isGoogle ? googleUserInfo!.email : formData.email);
      
      if (!isGoogle) {
        fd.append('password', formData.password);
      }
      
      fd.append('clinicalBio', clinicalBio);
      fd.append('hourlyRate',  hourlyRate);
      fd.append('prcLicense',  prcFile);
      
      const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: 'POST', 
        body: fd,
      });

      if (response.ok) {
        const docData = await response.json();
        setReferenceNumber(docData.referenceNumber);
        setDoctorStep(3); 
        
        // SAFETY NET: Forcibly wipe the session out completely
        localStorage.removeItem('user');
        sessionStorage.removeItem('user');
      } else {
        setError(await parseError(response));
      }
    } catch { 
      setError('We are having trouble connecting. Please check your internet and try again.'); 
    } finally { 
      setIsLoading(false); 
    }
  };

  const CheckIcon = () => (
    <svg width="10" height="8" viewBox="0 0 12 10" fill="none"><path d="M1 5l3.5 3.5L11 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
  );

  const roleOptions = [
    {
      val: 'PATIENT', label: 'Patient', desc: "I'm looking for support",
      icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/></svg>
    },
    {
      val: 'DOCTOR', label: 'Licensed Doctor', desc: "I provide care to patients",
      icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z"/></svg>
    },
  ];

  const LeftPanel = ({ title, subtitle, features }: { title: string; subtitle: string; features: string[] }) => (
    <div className="register-left">
      <div className="left-logo">
        <div className="left-logo-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="white"><path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/></svg>
        </div>
        <span className="left-logo-text">TheraPea</span>
      </div>
      <div className="left-content">
        <h2>{title}</h2>
        <p>{subtitle}</p>
        {features.map(f => (
          <div key={f} className="left-feature">
            <div className="left-feature-dot"><CheckIcon /></div>
            <span>{f}</span>
          </div>
        ))}
      </div>
    </div>
  );

  if (showDoctorVerification) {
    return (
      <div className="register-container">
        <LeftPanel title="Doctor Verification" subtitle="We verify every doctor before they see patients on TheraPea. It only takes a few minutes." features={['Upload your PRC License', 'Add your clinical biography', 'Set your consultation rate']} />
        <div className="register-right">
          <div className="register-card">
            {doctorStep !== 3 && (
              <div className="step-indicators">
                {[1, 2].map((step) => (
                  <div key={step} className={`step-indicator ${doctorStep >= step ? 'active' : ''}`}>
                    <div className="step-number">{step}</div>
                    <div className="step-label">{step === 1 ? 'Profile Setup' : 'PRC License'}</div>
                  </div>
                ))}
              </div>
            )}
            
            {error && <div className="error-message">{error}</div>}

            {doctorStep === 1 && (
              <>
                <div className="register-header">
                  <h1 className="register-title">Profile Setup</h1>
                  <p className="register-subtitle">Welcome, Dr. {registeredName.split(' ')[0]}! Tell patients a bit about yourself.</p>
                </div>
                <form onSubmit={(e) => { e.preventDefault(); setDoctorStep(2); }} className="register-form">
                  <div className="form-group">
                    <label className="form-label">Clinical Biography <span style={{ color: '#e53e3e' }}>*</span></label>
                    <textarea value={clinicalBio} onChange={(e) => setClinicalBio(e.target.value)} placeholder="Describe your background, specialization..." required rows={5}
                      style={{ width: '100%', padding: '12px 14px', borderRadius: '8px', border: '1.5px solid #e2e8f0', fontSize: '14px', fontFamily: 'inherit', resize: 'vertical', outline: 'none', boxSizing: 'border-box', background: '#f8fafc', color: '#1e293b', transition: 'border-color 0.2s', lineHeight: 1.6 }}
                      onFocus={e => e.target.style.borderColor = '#8BA888'} onBlur={e  => e.target.style.borderColor = '#e2e8f0'}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Hourly Rate <span style={{ color: '#e53e3e' }}>*</span></label>
                    <div style={{ position: 'relative' }}>
                      <span style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: '#64748b', fontSize: '14px' }}>₱</span>
                      <input type="number" min="0" step="0.01" value={hourlyRate} onChange={(e) => setHourlyRate(e.target.value)} placeholder="1500.00" required className="form-input" style={{ paddingLeft: '28px' }} />
                    </div>
                  </div>
                  <button type="submit" className="register-button">Continue</button>
                </form>
              </>
            )}

            {doctorStep === 2 && (
              <>
                <div className="register-header">
                  <h1 className="register-title">Upload PRC License</h1>
                  <p className="register-subtitle">Upload a clear copy of your PRC license so we can verify your credentials.</p>
                </div>
                <form onSubmit={handleDoctorSubmit} className="register-form">
                  <div onClick={() => fileInputRef.current?.click()} onDragOver={handleDragOver} onDragLeave={handleDragLeave} onDrop={handleDrop}
                    style={{ border: `2px dashed ${isDragging || prcFile ? '#8BA888' : '#cbd5e1'}`, borderRadius: '12px', padding: '40px 20px', textAlign: 'center', cursor: 'pointer', background: isDragging || prcFile ? '#f0fdf4' : '#f8fafc', transition: 'all 0.2s', minHeight: '180px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                    {prcFile ? (
                      <>
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#8BA888" strokeWidth="2"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>
                        <p style={{ color: '#8BA888', fontWeight: 600, margin: 0 }}>{prcFile.name}</p>
                        <p style={{ color: '#94a3b8', fontSize: '12px', margin: 0 }}>{(prcFile.size / (1024 * 1024)).toFixed(2)} MB — Click to change</p>
                      </>
                    ) : (
                      <>
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                        <p style={{ color: '#475569', fontWeight: 600, margin: 0 }}>Drag your PRC License here, or click to browse</p>
                        <p style={{ color: '#94a3b8', fontSize: '12px', margin: 0 }}>Required: PDF, JPG, or PNG — Max: 10 MB</p>
                      </>
                    )}
                    <input ref={fileInputRef} type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={handleFileChange} style={{ display: 'none' }} />
                  </div>
                  <div className="form-navigation" style={{ marginTop: '20px' }}>
                    <button type="button" className="back-button" onClick={() => { setDoctorStep(1); setError(''); }}>Back</button>
                    <button type="submit" className="register-button" disabled={isLoading}>{isLoading ? 'Submitting…' : 'Submit for Review'}</button>
                  </div>
                </form>
              </>
            )}

            {doctorStep === 3 && (
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <div style={{ width: '64px', height: '64px', background: '#f0fdf4', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px auto' }}>
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#0A5C36" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
                </div>
                <h1 className="register-title">Application Submitted!</h1>
                <p className="register-subtitle" style={{ marginBottom: '24px' }}>Thank you for applying to join TheraPea, Dr. {registeredName.split(' ')[0]}. Your application is still under review. Please wait for an approval email.</p>
                {isGoogleOtpFlow ? (
                  <div style={{ background: '#f8fafc', border: '1px solid #cbd5e1', padding: '24px', borderRadius: '12px', marginBottom: '32px' }}>
                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#475569" strokeWidth="1.5" style={{ marginBottom: '12px' }}><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
                    <p style={{ fontSize: '15px', color: '#1e293b', fontWeight: 500, marginBottom: '8px' }}>Confirmation Sent</p>
                    <p style={{ fontSize: '14px', color: '#475569', lineHeight: 1.6 }}>We have sent a formal confirmation to <strong>{registeredEmail}</strong>. You will receive another email notifying you the moment the administration team approves or declines your account.</p>
                  </div>
                ) : (
                  <>
                    <div style={{ background: '#f8fafc', border: '1px dashed #cbd5e1', padding: '20px', borderRadius: '12px', marginBottom: '24px' }}>
                      <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '8px', fontWeight: 500 }}>YOUR REFERENCE NUMBER</p>
                      <p style={{ fontSize: '28px', color: '#1e293b', fontWeight: 800, letterSpacing: '2px' }}>{referenceNumber}</p>
                    </div>
                    <p style={{ fontSize: '14px', color: '#475569', lineHeight: 1.6, marginBottom: '32px' }}>Please save this reference number. You can use it on our <strong>Track Application</strong> page to check your status at any time.</p>
                  </>
                )}
                <button type="button" className="register-button" onClick={() => navigate('/')}>Return to Home</button>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  if (showOtpVerification) {
    return (
      <div className="register-container">
        <LeftPanel title="Verify your email" subtitle="To protect our community, we need to make sure this email belongs to you." features={['Secure account creation', 'Prevents spam and fake profiles', 'Ensures you receive appointment updates']} />
        <div className="register-right">
          <div className="register-card">
            <div className="register-header" style={{ textAlign: 'center' }}>
              <div style={{ width: '48px', height: '48px', background: '#f0fdf4', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px auto' }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#0A5C36" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
              </div>
              <h1 className="register-title">Enter Verification Code</h1>
              <p className="register-subtitle">We've sent a 6-digit code to <strong>{googleUserInfo?.email}</strong>.</p>
            </div>
            {error   && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}
            <form onSubmit={handleVerifyOtpAndRegister} className="register-form">
              <div className="form-group" style={{ textAlign: 'center' }}>
                <input type="text" maxLength={6} value={otpCode} onChange={(e) => setOtpCode(e.target.value.replace(/[^0-9]/g, ''))} placeholder="000000" className="form-input" style={{ textAlign: 'center', fontSize: '24px', letterSpacing: '12px', fontWeight: 600, padding: '16px', color: '#0A5C36' }} required />
              </div>
              <button type="submit" className="register-button" disabled={isLoading}>{isLoading ? 'Verifying...' : 'Verify & Continue'}</button>
            </form>
            <p style={{ textAlign: 'center', marginTop: '20px', fontSize: '13px', color: '#6B7280' }}>
              Didn't receive the email? <button type="button" onClick={() => requestOtpForGoogle(googleUserInfo?.email || '')} disabled={isLoading} style={{ background: 'none', border: 'none', color: '#8BA888', cursor: 'pointer', textDecoration: 'underline', padding: 0, fontSize: '13px' }}>Click to resend</button>
            </p>
            <p style={{ textAlign: 'center', marginTop: '8px' }}>
              <button type="button" onClick={() => { setShowOtpVerification(false); setIsGoogleOtpFlow(false); setGoogleUserInfo(null); }} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', fontSize: '13px' }}>← Back to registration</button>
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ✅ UPDATED: showGoogleFlow now has a 2-step process just like the regular registration flow.
  if (showGoogleFlow) {
    return (
      <div className="register-container">
        <LeftPanel title="Almost there" subtitle="Your Google account is connected. Just tell us who you are and you're good to go." features={['No password needed', 'Your name and email are already set', "Pick your role and you're done"]} />
        <div className="register-right">
          <div className="register-card">
            <div className="register-header">
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', background: '#F0F4F0', border: '1px solid #D1E0D0', borderRadius: '999px', padding: '6px 14px', fontSize: '13px', color: '#3D6B39', fontWeight: 600, marginBottom: '20px' }}>
                <svg viewBox="0 0 24 24" width="14" height="14">
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                </svg>
                Signed in as {googleUserInfo?.email}
              </div>
              <h1 className="register-title">
                {googleStep === 1 ? 'One last step' : 'Review & Confirm'}
              </h1>
              <p className="register-subtitle">
                {googleStep === 1 ? 'How will you be using TheraPea?' : 'Take a look before we finalize your setup.'}
              </p>
            </div>

            {error   && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            <form onSubmit={handleCompleteGoogleProfile} className="register-form">
              {googleStep === 1 && (
                <div className="form-group">
                  <div className="role-grid">
                    {roleOptions.map(({ val, label, desc, icon }) => (
                      <label key={val} className="role-option">
                        <input type="radio" name="role" value={val} checked={googleRole === val} onChange={(e) => setGoogleRole(e.target.value)} />
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
              )}

              {googleStep === 2 && (
                <>
                  <div className="review-section">
                    <h3 className="review-title">Your Details</h3>
                    <div className="review-item"><span className="review-label">Full Name</span><span className="review-value">{googleUserInfo?.name || '—'}</span></div>
                    <div className="review-item"><span className="review-label">Email</span><span className="review-value">{googleUserInfo?.email || '—'}</span></div>
                    <div className="review-item"><span className="review-label">Account Type</span><span className="review-value">{googleRole === 'PATIENT' ? 'Patient' : 'Licensed Doctor'}</span></div>
                  </div>
                  {googleRole === 'DOCTOR' && (
                    <div style={{ marginTop: '12px', padding: '12px 14px', background: '#fffbeb', border: '1px solid #fcd34d', borderRadius: '10px', fontSize: '13px', color: '#92400e', lineHeight: 1.5 }}>
                      You will be asked to upload your PRC license and fill in your clinical profile next.
                    </div>
                  )}
                  <div className="confirmation-section">
                    <div className="confirmation-checkbox"><input type="checkbox" id="g-terms" required /><label htmlFor="g-terms" className="checkbox-label">I agree to the Terms of Service and Privacy Policy</label></div>
                    <div className="confirmation-checkbox"><input type="checkbox" id="g-consent" required /><label htmlFor="g-consent" className="checkbox-label">I'm okay receiving emails about my care and account</label></div>
                  </div>
                </>
              )}

              <div className="form-navigation">
                {googleStep === 2 && <button type="button" onClick={() => setGoogleStep(1)} className="back-button">Back</button>}
                <button type="submit" className="register-button" disabled={isLoading}>
                  {isLoading ? 'Setting up your account…' : (googleStep === 1 ? 'Next' : (googleRole === 'DOCTOR' ? 'Continue to Verification' : 'Create My Account'))}
                </button>
              </div>
            </form>

            <p style={{ textAlign: 'center', marginTop: '16px', fontSize: '13px', color: '#6B7280' }}>
              Wrong account? <button type="button" onClick={() => { setShowGoogleFlow(false); setIsGoogleOtpFlow(false); setGoogleUserInfo(null); setError(''); setSuccess(''); setGoogleStep(1); }} style={{ background: 'none', border: 'none', color: '#8BA888', cursor: 'pointer', textDecoration: 'underline', padding: 0, fontSize: '13px' }}>Start over</button>
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="register-container">
      <LeftPanel title="Start your path to well-being" subtitle="Create an account and connect with a licensed therapist who fits your needs." features={[ 'Matched with the right therapist', 'Secure, HIPAA-compliant platform', 'Flexible scheduling, any time' ]} />
      <div className="register-right">
        <div className="register-card">
          <div className="step-indicators">
            {[1, 2, 3].map((step) => (
              <div key={step} className={`step-indicator ${currentStep >= step ? 'active' : ''}`}>
                <div className="step-number">{step}</div>
                <div className="step-label">
                  {step === 1 && 'Personal Info'}
                  {step === 2 && 'Account Setup'}
                  {step === 3 && 'Review'}
                </div>
              </div>
            ))}
          </div>
          <div className="register-header">
            <div className="register-logo">
              <div className="register-logo-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="white"><path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/></svg>
              </div>
              <span className="register-logo-text">Thera<span>Pea</span></span>
            </div>
            <h1 className="register-title">
              {currentStep === 1 && 'Create Your Account'}
              {currentStep === 2 && 'Who are you?'}
              {currentStep === 3 && 'Review & Confirm'}
            </h1>
            <p className="register-subtitle">
              {currentStep === 1 && 'Fill in your details to get started.'}
              {currentStep === 2 && 'This helps us set up the right experience for you.'}
              {currentStep === 3 && 'Take a look before we finalize your setup.'}
            </p>
          </div>

          {error   && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          <form onSubmit={handleRegularSubmit} className="register-form">
            {currentStep === 1 && (
              <>
                <div className="form-group"><label htmlFor="fullName" className="form-label">Full Name</label><input type="text" id="fullName" name="fullName" value={formData.fullName} onChange={handleChange} placeholder="Your full name" className="form-input" required /></div>
                <div className="form-group"><label htmlFor="email" className="form-label">Email Address</label><input type="email" id="email" name="email" value={formData.email} onChange={handleChange} placeholder="you@example.com" className="form-input" required /></div>
                <div className="form-group"><label htmlFor="password" className="form-label">Password</label><input type="password" id="password" name="password" value={formData.password} onChange={handleChange} placeholder="Create a password" className="form-input" required /></div>
                <div className="form-group"><label htmlFor="confirmPassword" className="form-label">Confirm Password</label><input type="password" id="confirmPassword" name="confirmPassword" value={formData.confirmPassword} onChange={handleChange} placeholder="Repeat your password" className="form-input" required /></div>
              </>
            )}

            {currentStep === 2 && (
              <div className="form-group">
                <div className="role-grid">
                  {roleOptions.map(({ val, label, desc, icon }) => (
                    <label key={val} className="role-option">
                      <input type="radio" name="role" value={val} checked={formData.role === val} onChange={handleChange} />
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
            )}

            {currentStep === 3 && (
              <>
                <div className="review-section">
                  <h3 className="review-title">Your Details</h3>
                  <div className="review-item"><span className="review-label">Full Name</span><span className="review-value">{formData.fullName || '—'}</span></div>
                  <div className="review-item"><span className="review-label">Email</span><span className="review-value">{formData.email || '—'}</span></div>
                  <div className="review-item"><span className="review-label">Account Type</span><span className="review-value">{formData.role === 'PATIENT' ? 'Patient' : 'Licensed Doctor'}</span></div>
                </div>
                {formData.role === 'DOCTOR' && (
                  <div style={{ marginTop: '12px', padding: '12px 14px', background: '#fffbeb', border: '1px solid #fcd34d', borderRadius: '10px', fontSize: '13px', color: '#92400e', lineHeight: 1.5 }}>
                    You will be asked to upload your PRC license and fill in your clinical profile next.
                  </div>
                )}
                <div className="confirmation-section">
                  <div className="confirmation-checkbox"><input type="checkbox" id="terms" required /><label htmlFor="terms" className="checkbox-label">I agree to the Terms of Service and Privacy Policy</label></div>
                  <div className="confirmation-checkbox"><input type="checkbox" id="consent" required /><label htmlFor="consent" className="checkbox-label">I'm okay receiving emails about my care and account</label></div>
                </div>
              </>
            )}

            <div className="form-navigation">
              {currentStep > 1 && <button type="button" onClick={prevStep} className="back-button">Back</button>}
              <button type="submit" className="register-button" disabled={isLoading}>
                {isLoading ? 'Processing…' : (currentStep === 3 ? (formData.role === 'DOCTOR' ? 'Continue to Verification' : 'Register Account') : 'Next')}
              </button>
            </div>
          </form>

          <div className="divider"><div className="divider-line"/><span className="divider-text">Or</span><div className="divider-line"/></div>

          <button type="button" onClick={handleGoogleRegister} className="google-button" disabled={isLoading}>
            <svg className="google-icon" viewBox="0 0 24 24"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
            {isLoading ? 'Connecting to Google…' : 'Sign up with Google'}
          </button>

          <p className="login-link">
            Already have an account? <button type="button" onClick={() => navigate('/login')} style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}>Sign in</button>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Register;