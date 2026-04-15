import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Assessment from './pages/Assessment';
import EmergencyMap from './pages/EmergencyMap';
import FindTherapist from './pages/FindTherapist';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="*" element={<Navigate to="/" />} />
        <Route path="/assessment" element={<Assessment />} />
        <Route path="/emergency" element={<EmergencyMap />} />
        <Route path="/therapists" element={<FindTherapist />} />
      </Routes>
    </Router>
  );
}

export default App;