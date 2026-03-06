import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const baseInputStyle: React.CSSProperties = {
  width: '100%',
  padding: '12px 16px',
  fontSize: '14px',
  color: '#111827',
  background: '#F9FAFB',
  border: '1px solid #E5E7EB',
  borderRadius: '12px',
  outline: 'none',
  fontFamily: "'Inter', sans-serif",
  transition: 'border-color 0.15s, box-shadow 0.15s, background 0.15s',
};

interface InputFieldProps {
  type: string; name: string; value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder: string; required?: boolean;
}

const InputField: React.FC<InputFieldProps> = ({ type, name, value, onChange, placeholder, required }) => (
  <input
    type={type} name={name} value={value} onChange={onChange}
    placeholder={placeholder} required={required} style={baseInputStyle}
    onFocus={(e) => {
      e.currentTarget.style.borderColor = '#2563EB';
      e.currentTarget.style.boxShadow = '0 0 0 3px rgba(37,99,235,0.12)';
      e.currentTarget.style.background = '#ffffff';
      e.currentTarget.style.color = '#111827';
    }}
    onBlur={(e) => {
      e.currentTarget.style.borderColor = '#E5E7EB';
      e.currentTarget.style.boxShadow = 'none';
      e.currentTarget.style.background = '#F9FAFB';
      e.currentTarget.style.color = '#111827';
    }}
  />
);

const Register: React.FC = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState({ fullName: '', email: '', password: '', role: 'PATIENT' });
  const [statusMessage, setStatusMessage] = useState('');
  const [isError, setIsError] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleNextStep = (e: React.FormEvent) => { e.preventDefault(); setStep(2); };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      const data = await response.text();
      if (response.ok) {
        setIsError(false);
        setStatusMessage('Account created! Redirecting…');
        setTimeout(() => navigate('/login'), 1500);
      } else {
        setIsError(true);
        setStatusMessage(data || 'Registration failed. Please try again.');
      }
    } catch {
      setIsError(true);
      setStatusMessage('Failed to connect to the server.');
    } finally {
      setIsLoading(false);
    }
  };

  const CheckIcon = () => (
    <svg width="10" height="8" viewBox="0 0 12 10" fill="none">
      <path d="M1 5l3.5 3.5L11 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );

  return (
    <>
      <style>{`
        html, body, #root { margin: 0; padding: 0; width: 100%; min-height: 100vh; box-sizing: border-box; }
        *, *::before, *::after { box-sizing: border-box; }
        input, button { font-family: 'Inter', sans-serif; }
        input::placeholder { color: #9CA3AF !important; }
        input { color: #111827 !important; }
        @media (min-width: 1024px) { .therapea-left-reg { display: flex !important; } }
      `}</style>

      <div style={{ minHeight: '100vh', width: '100vw', display: 'flex', fontFamily: "'Inter', sans-serif", background: '#ffffff' }}>

        {/* ── Left Panel ── */}
        <div
          className="therapea-left-reg"
          style={{
            width: '50%', display: 'none', flexDirection: 'column',
            justifyContent: 'space-between', padding: '48px', position: 'relative',
            overflow: 'hidden', background: 'linear-gradient(135deg, #6d28d9 0%, #7C3AED 60%, #2563EB 100%)',
          }}
        >
          <div style={{ position: 'absolute', top: -64, right: -64, width: 256, height: 256, borderRadius: '50%', background: 'rgba(255,255,255,0.07)' }} />
          <div style={{ position: 'absolute', bottom: -80, left: -80, width: 288, height: 288, borderRadius: '50%', background: 'rgba(255,255,255,0.07)' }} />

          {/* Logo */}
          <div style={{ position: 'relative', zIndex: 10, display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 36, height: 36, background: '#fff', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <span style={{ color: '#7C3AED', fontWeight: 700, fontSize: 18 }}>T</span>
            </div>
            <span style={{ color: '#fff', fontWeight: 700, fontSize: 20 }}>TheraPea</span>
          </div>

          {/* Hero */}
          <div style={{ position: 'relative', zIndex: 10 }}>
            <div style={{ width: 56, height: 56, background: 'rgba(255,255,255,0.18)', borderRadius: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24 }}>
              <svg width="26" height="26" viewBox="0 0 24 24" fill="white">
                <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z" />
              </svg>
            </div>
            <h2 style={{ color: '#fff', fontSize: 30, fontWeight: 700, lineHeight: 1.25, marginBottom: 12 }}>
              Start your path<br />to well-being
            </h2>
            <p style={{ color: 'rgba(221,214,254,0.9)', fontSize: 15, lineHeight: 1.6, marginBottom: 32 }}>
              Join thousands who've transformed their mental health with personalized, compassionate care.
            </p>
            {['Matched with the right therapist for you', 'Secure, HIPAA-compliant platform', 'Flexible scheduling, any time'].map((f) => (
              <div key={f} style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                <div style={{ width: 20, height: 20, borderRadius: '50%', background: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <CheckIcon />
                </div>
                <p style={{ color: 'rgba(221,214,254,0.9)', fontSize: 14 }}>{f}</p>
              </div>
            ))}
          </div>

          <p style={{ position: 'relative', zIndex: 10, color: 'rgba(196,181,253,0.6)', fontSize: 12 }}>© 2024 TheraPea. All rights reserved.</p>
        </div>

        {/* ── Right Panel ── */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', padding: '48px 32px', background: '#ffffff' }}>
          <div style={{ maxWidth: 380, width: '100%' }}>

            {/* Step indicator */}
            <div style={{ marginBottom: 32 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 0, marginBottom: 20 }}>
                {[1, 2].map((s, i) => (
                  <React.Fragment key={s}>
                    <div style={{
                      width: 28, height: 28, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                      background: step >= s ? '#2563EB' : '#E5E7EB',
                      color: step >= s ? '#fff' : '#9CA3AF',
                      fontSize: 12, fontWeight: 700, flexShrink: 0,
                    }}>
                      {step > s ? <CheckIcon /> : s}
                    </div>
                    {i < 1 && (
                      <div style={{ flex: 1, height: 2, background: step > s ? '#2563EB' : '#E5E7EB', margin: '0 4px' }} />
                    )}
                  </React.Fragment>
                ))}
              </div>
              <p style={{ fontSize: 12, color: '#9CA3AF', fontWeight: 500, marginBottom: 4 }}>Step {step} of 2</p>
              <h1 style={{ fontSize: 28, fontWeight: 700, color: '#111827', marginBottom: 4 }}>
                {step === 1 ? 'Create account' : 'Your role'}
              </h1>
              <p style={{ fontSize: 14, color: '#6B7280' }}>
                {step === 1 ? 'Start your free mental health journey' : 'How will you be using TheraPea?'}
              </p>
            </div>

            {step === 1 ? (
              <form onSubmit={handleNextStep} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Full name</label>
                  <InputField type="text" name="fullName" value={formData.fullName} onChange={handleChange} placeholder="Jane Smith" required />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email address</label>
                  <InputField type="email" name="email" value={formData.email} onChange={handleChange} placeholder="you@example.com" required />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Password</label>
                  <InputField type="password" name="password" value={formData.password} onChange={handleChange} placeholder="Minimum 8 characters" required />
                </div>
                <button
                  type="submit"
                  style={{
                    width: '100%', padding: '13px 16px', borderRadius: 12, border: 'none',
                    background: '#2563EB', color: '#fff', fontSize: 14, fontWeight: 600,
                    cursor: 'pointer', boxShadow: '0 4px 14px rgba(37,99,235,0.35)',
                    fontFamily: "'Inter', sans-serif",
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = '#1d4ed8'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = '#2563EB'; }}
                >
                  Continue →
                </button>
              </form>
            ) : (
              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {/* Patient */}
                {(['PATIENT', 'DOCTOR'] as const).map((roleVal) => {
                  const isSelected = formData.role === roleVal;
                  return (
                    <label
                      key={roleVal}
                      style={{
                        display: 'flex', alignItems: 'flex-start', padding: '16px', borderRadius: 14, cursor: 'pointer',
                        border: `2px solid ${isSelected ? '#2563EB' : '#E5E7EB'}`,
                        background: isSelected ? '#EFF6FF' : '#ffffff',
                        transition: 'border-color 0.15s, background 0.15s',
                      }}
                    >
                      <input type="radio" name="role" value={roleVal} checked={isSelected} onChange={handleChange} style={{ display: 'none' }} />
                      <div style={{
                        width: 40, height: 40, borderRadius: 12, flexShrink: 0, marginRight: 12, marginTop: 2,
                        background: isSelected ? '#DBEAFE' : '#F3F4F6',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        {roleVal === 'PATIENT' ? (
                          <svg width="20" height="20" viewBox="0 0 24 24" fill={isSelected ? '#2563EB' : '#9CA3AF'}>
                            <path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z" />
                          </svg>
                        ) : (
                          <svg width="20" height="20" viewBox="0 0 24 24" fill={isSelected ? '#2563EB' : '#9CA3AF'}>
                            <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z" />
                          </svg>
                        )}
                      </div>
                      <div style={{ flex: 1 }}>
                        <p style={{ fontSize: 14, fontWeight: 600, color: '#111827', marginBottom: 2 }}>
                          {roleVal === 'PATIENT' ? "I'm a Patient" : "I'm a Licensed Doctor"}
                        </p>
                        <p style={{ fontSize: 12, color: '#6B7280' }}>
                          {roleVal === 'PATIENT' ? 'Seeking therapy and mental health support' : 'Providing telehealth services to patients'}
                        </p>
                      </div>
                      {isSelected && (
                        <div style={{ width: 20, height: 20, borderRadius: '50%', background: '#2563EB', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginLeft: 8 }}>
                          <CheckIcon />
                        </div>
                      )}
                    </label>
                  );
                })}

                {statusMessage && (
                  <div style={{
                    padding: '12px 16px', borderRadius: 12, fontSize: 13, fontWeight: 500,
                    background: isError ? '#FEF2F2' : '#F0FDF4',
                    color: isError ? '#EF4444' : '#10B981',
                    border: `1px solid ${isError ? '#FECACA' : '#A7F3D0'}`,
                  }}>
                    {statusMessage}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isLoading}
                  style={{
                    width: '100%', padding: '13px 16px', borderRadius: 12, border: 'none',
                    background: isLoading ? '#93C5FD' : '#2563EB', color: '#fff',
                    fontSize: 14, fontWeight: 600, cursor: isLoading ? 'not-allowed' : 'pointer',
                    boxShadow: isLoading ? 'none' : '0 4px 14px rgba(37,99,235,0.35)',
                    fontFamily: "'Inter', sans-serif", marginTop: 4,
                  }}
                  onMouseEnter={(e) => { if (!isLoading) e.currentTarget.style.background = '#1d4ed8'; }}
                  onMouseLeave={(e) => { if (!isLoading) e.currentTarget.style.background = '#2563EB'; }}
                >
                  {isLoading ? 'Creating account…' : 'Complete Registration'}
                </button>

                <button
                  type="button"
                  onClick={() => setStep(1)}
                  style={{
                    width: '100%', padding: '11px 16px', borderRadius: 12,
                    border: '1px solid #E5E7EB', background: '#fff',
                    color: '#6B7280', fontSize: 14, fontWeight: 500, cursor: 'pointer',
                    fontFamily: "'Inter', sans-serif",
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = '#F9FAFB'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; }}
                >
                  ← Back
                </button>
              </form>
            )}

            <p style={{ textAlign: 'center', marginTop: 24, fontSize: 14, color: '#6B7280' }}>
              Already have an account?{' '}
              <span onClick={() => navigate('/login')} style={{ color: '#2563EB', fontWeight: 600, cursor: 'pointer' }}>
                Sign in
              </span>
            </p>
          </div>
        </div>
      </div>
    </>
  );
};

export default Register;