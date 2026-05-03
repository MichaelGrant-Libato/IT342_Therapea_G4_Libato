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
import Reference from './pages/Reference';
import AdminApprovals from './pages/AdminApprovals';

// ── NEW IMPORTS ──
import AssessmentResult from './pages/AssessmentResult';
import AssessmentHistory from './pages/AssessmentHistory';

// ── ROLE-BASED PROTECTED ROUTE COMPONENT ──
const ProtectedRoute = ({ children, allowedRoles }: { children: React.ReactNode, allowedRoles?: string[] }) => {
  const rawUser = localStorage.getItem('user') || sessionStorage.getItem('user');
  
  if (!rawUser) {
    // If not logged in, go to login
    return <Navigate to="/login" replace />;
  }

  const user = JSON.parse(rawUser);

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    // If logged in but wrong role (e.g. Patient trying to see Admin), go to landing
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

function App() {
  return (
    <Router>
      <Routes>
        {/* ── Public ── */}
        <Route path="/"          element={<LandingPage />} />
        <Route path="/login"     element={<Login />} />
        <Route path="/register"  element={<Register />} />
        <Route path="/emergency" element={<EmergencyMap />} />
        <Route path="/reference" element={<Reference />} />

        {/* ── Protected: All Roles ── */}
        <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/profile"   element={<ProtectedRoute><Profile /></ProtectedRoute>} />
        <Route path="/settings"  element={<ProtectedRoute><Settings /></ProtectedRoute>} />
        <Route path="/messages"  element={<ProtectedRoute><Messages /></ProtectedRoute>} />

        {/* ── Protected: Patient Only ── */}
        <Route path="/therapists"     element={<ProtectedRoute allowedRoles={['PATIENT']}><FindTherapist /></ProtectedRoute>} />
        <Route path="/therapists/:id" element={<ProtectedRoute allowedRoles={['PATIENT']}><TherapistProfile /></ProtectedRoute>} />
        <Route path="/checkout"       element={<ProtectedRoute allowedRoles={['PATIENT']}><Checkout /></ProtectedRoute>} />
        <Route path="/assessment"     element={<ProtectedRoute allowedRoles={['PATIENT']}><Assessment /></ProtectedRoute>} />
        <Route path="/assessment-result/:id" element={<ProtectedRoute allowedRoles={['PATIENT']}><AssessmentResult /></ProtectedRoute>} />
        <Route path="/assessments-history"   element={<ProtectedRoute allowedRoles={['PATIENT']}><AssessmentHistory /></ProtectedRoute>} />
        <Route path="/progress"       element={<ProtectedRoute allowedRoles={['PATIENT']}><Progress /></ProtectedRoute>} />

        {/* ── Protected: Doctor Only ── */}
        <Route path="/patients"      element={<ProtectedRoute allowedRoles={['DOCTOR']}><Patients /></ProtectedRoute>} />
        <Route path="/appointments"  element={<ProtectedRoute allowedRoles={['DOCTOR', 'PATIENT']}><Appointments /></ProtectedRoute>} />

        {/* ── Protected: Admin Only ── */}
        <Route 
          path="/admin" 
          element={<Navigate to="/admin/approvals" replace />} 
        />
        <Route 
          path="/admin/approvals" 
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminApprovals />
            </ProtectedRoute>
          } 
        />

        {/* ── Fallback ── */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}

export default App;