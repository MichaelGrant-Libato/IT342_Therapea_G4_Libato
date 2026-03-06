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
  type: string;
  name: string;
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder: string;
  required?: boolean;
}

const InputField: React.FC<InputFieldProps> = ({ type, name, value, onChange, placeholder, required }) => (
  <input
    type={type}
    name={name}
    value={value}
    onChange={onChange}
    placeholder={placeholder}
    required={required}
    style={baseInputStyle}
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

const Login: React.FC = () => {
  const [formData, setFormData] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (response.ok) {
        const userData = await response.json();
        localStorage.setItem('user', JSON.stringify(userData));
        navigate('/dashboard');
      } else {
        const errText = await response.text();
        setError(errText || 'Invalid email or password.');
      }
    } catch {
      setError('Failed to connect to the server.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <>
      <style>{`
        html, body, #root { margin: 0; padding: 0; width: 100%; min-height: 100vh; box-sizing: border-box; }
        *, *::before, *::after { box-sizing: border-box; }
        input, button { font-family: 'Inter', sans-serif; }
        input::placeholder { color: #9CA3AF !important; }
        input { color: #111827 !important; }
        @media (min-width: 1024px) { .therapea-left { display: flex !important; } }
      `}</style>

      <div style={{ minHeight: '100vh', width: '100vw', display: 'flex', fontFamily: "'Inter', sans-serif", background: '#ffffff' }}>

        {/* ── Left Panel ── */}
        <div
          className="therapea-left"
          style={{
            width: '50%',
            display: 'none',
            flexDirection: 'column',
            justifyContent: 'space-between',
            padding: '48px',
            position: 'relative',
            overflow: 'hidden',
            background: 'linear-gradient(135deg, #1d4ed8 0%, #2563EB 50%, #7C3AED 100%)',
          }}
        >
          {/* Decorative blobs */}
          <div style={{ position: 'absolute', top: -96, right: -96, width: 384, height: 384, borderRadius: '50%', background: 'rgba(255,255,255,0.07)' }} />
          <div style={{ position: 'absolute', bottom: -96, left: -96, width: 320, height: 320, borderRadius: '50%', background: 'rgba(255,255,255,0.07)' }} />

          {/* Logo */}
          <div style={{ position: 'relative', zIndex: 10, display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 36, height: 36, background: '#fff', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 4px 12px rgba(0,0,0,0.15)' }}>
              <span style={{ color: '#2563EB', fontWeight: 700, fontSize: 18 }}>T</span>
            </div>
            <span style={{ color: '#fff', fontWeight: 700, fontSize: 20 }}>TheraPea</span>
          </div>

          {/* Hero */}
          <div style={{ position: 'relative', zIndex: 10 }}>
            <div style={{ width: 64, height: 64, background: 'rgba(255,255,255,0.18)', borderRadius: 18, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24 }}>
              <svg width="30" height="30" viewBox="0 0 24 24" fill="white">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z" />
              </svg>
            </div>
            <h2 style={{ color: '#fff', fontSize: 30, fontWeight: 700, lineHeight: 1.25, marginBottom: 12 }}>
              Your mental health<br />journey starts here
            </h2>
            <p style={{ color: 'rgba(219,234,254,0.9)', fontSize: 15, lineHeight: 1.6, marginBottom: 32 }}>
              Connect with licensed therapists, track your progress, and take control of your well-being.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
              {[{ value: '500+', label: 'Therapists' }, { value: '10k+', label: 'Patients' }, { value: '4.9★', label: 'Rating' }].map((s) => (
                <div key={s.label} style={{ background: 'rgba(255,255,255,0.15)', borderRadius: 14, padding: '16px 12px' }}>
                  <p style={{ color: '#fff', fontWeight: 700, fontSize: 20, marginBottom: 2 }}>{s.value}</p>
                  <p style={{ color: 'rgba(219,234,254,0.8)', fontSize: 12 }}>{s.label}</p>
                </div>
              ))}
            </div>
          </div>

          <p style={{ position: 'relative', zIndex: 10, color: 'rgba(191,219,254,0.65)', fontSize: 12 }}>© 2024 TheraPea. All rights reserved.</p>
        </div>

        {/* ── Right Panel ── */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', padding: '48px 32px', background: '#ffffff' }}>
          <div style={{ maxWidth: 380, width: '100%' }}>

            {/* Mobile logo */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 40 }} className="mobile-logo">
              <div style={{ width: 32, height: 32, borderRadius: 8, background: '#2563EB', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ color: '#fff', fontWeight: 700 }}>T</span>
              </div>
              <span style={{ fontWeight: 700, color: '#111827', fontSize: 18 }}>TheraPea</span>
            </div>

            <div style={{ marginBottom: 32 }}>
              <h1 style={{ fontSize: 30, fontWeight: 700, color: '#111827', marginBottom: 6 }}>Welcome back</h1>
              <p style={{ fontSize: 14, color: '#6B7280' }}>Sign in to continue your journey</p>
            </div>

            <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>Email address</label>
                <InputField type="email" name="email" value={formData.email} onChange={handleChange} placeholder="you@example.com" required />
              </div>

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <label style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>Password</label>
                  <a href="#" style={{ fontSize: 12, fontWeight: 500, color: '#2563EB', textDecoration: 'none' }}>Forgot password?</a>
                </div>
                <InputField type="password" name="password" value={formData.password} onChange={handleChange} placeholder="••••••••" required />
              </div>

              {error && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', borderRadius: 12, background: '#FEF2F2', border: '1px solid #FECACA', color: '#EF4444', fontSize: 13, fontWeight: 500 }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" style={{ flexShrink: 0 }}>
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                  </svg>
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={isLoading}
                style={{
                  width: '100%', padding: '13px 16px', borderRadius: 12, border: 'none',
                  background: isLoading ? '#93C5FD' : '#2563EB', color: '#ffffff',
                  fontSize: 14, fontWeight: 600, cursor: isLoading ? 'not-allowed' : 'pointer',
                  boxShadow: isLoading ? 'none' : '0 4px 14px rgba(37,99,235,0.35)',
                  transition: 'background 0.15s', fontFamily: "'Inter', sans-serif",
                }}
                onMouseEnter={(e) => { if (!isLoading) e.currentTarget.style.background = '#1d4ed8'; }}
                onMouseLeave={(e) => { if (!isLoading) e.currentTarget.style.background = '#2563EB'; }}
              >
                {isLoading ? 'Signing in…' : 'Sign in'}
              </button>
            </form>

            <p style={{ textAlign: 'center', marginTop: 28, fontSize: 14, color: '#6B7280' }}>
              Don't have an account?{' '}
              <span onClick={() => navigate('/register')} style={{ color: '#2563EB', fontWeight: 600, cursor: 'pointer' }}>
                Create one
              </span>
            </p>
          </div>
        </div>
      </div>
    </>
  );
};

export default Login;