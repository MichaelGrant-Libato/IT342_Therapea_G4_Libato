import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './index.css';

import LandingPage from './pages/LandingPage';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Assessment from './pages/Assessment';
import EmergencyMap from './pages/EmergencyMap';
import Profile from './pages/Profile';
import FindTherapist from './pages/FindTherapist';
import Appointments from './pages/Appointments';
import TherapistProfile from './pages/TherapistProfile';
import Checkout from './pages/Checkout';
import Patients from './pages/Patients'; 
import Messages from './pages/Messages'; 
import Progress from './pages/Progress';
import Settings from './pages/Settings';

// ── NEW IMPORTS ──
import AssessmentResult from './pages/AssessmentResult';
import AssessmentHistory from './pages/AssessmentHistory';

// Placeholder pages for nav items not yet built
const ComingSoon = ({ page }: { page: string }) => (
  <div style={{
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily: 'DM Sans, sans-serif',
    background: 'var(--bg)',
    gap: 12,
  }}>
    <p style={{ fontSize: 14, color: 'var(--text-muted)', margin: 0, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
      Coming soon
    </p>
    <h1 style={{ fontFamily: 'Lora, serif', fontSize: 32, fontWeight: 700, color: 'var(--text-main)', margin: 0, letterSpacing: '-0.02em' }}>{page}</h1>
    <p style={{ fontSize: 15, color: 'var(--text-sub)', margin: 0 }}>This page is still being built.</p>
    <a
      href="/dashboard"
      style={{
        marginTop: 16,
        padding: '12px 28px',
        background: 'linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%)',
        color: '#fff',
        borderRadius: 9999,
        textDecoration: 'none',
        fontWeight: 600,
        fontSize: 14.5,
        boxShadow: '0 2px 8px rgba(82, 112, 80, 0.25)',
        transition: 'transform 0.2s, box-shadow 0.2s',
      }}
    >
      ← Back to Dashboard
    </a>
  </div>
);

function App() {
  return (
    <Router>
      <Routes>
        {/* ── Public ── */}
        <Route path="/"          element={<LandingPage />} />
        <Route path="/login"     element={<Login />} />
        <Route path="/register"  element={<Register />} />
        <Route path="/emergency" element={<EmergencyMap />} />

        {/* ── Core Navigation (Sidebar Pages) ── */}
        <Route path="/dashboard"    element={<Dashboard />} />
        <Route path="/profile"      element={<Profile />} />
        <Route path="/appointments" element={<Appointments />} />
        <Route path="/patients"     element={<Patients />} />
        <Route path="/messages"     element={<Messages />} />
        <Route path="/progress"     element={<Progress />} />

        {/* ── Therapist / Booking flow ── */}
        <Route path="/therapists"     element={<FindTherapist />} />
        <Route path="/therapists/:id" element={<TherapistProfile />} />
        <Route path="/checkout"       element={<Checkout />} />

        {/* ── Assessment ── */}
        <Route path="/assessment"            element={<Assessment />} />
        <Route path="/assessment-result/:id" element={<AssessmentResult />} />
        <Route path="/assessments-history"   element={<AssessmentHistory />} />

        <Route path="/settings"     element={<Settings />} />

        {/* ── Fallback ── */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}

export default App;