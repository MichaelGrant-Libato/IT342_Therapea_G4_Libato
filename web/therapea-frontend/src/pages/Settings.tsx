import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import '../styles/Settings.css';

const Settings: React.FC = () => {
  const navigate = useNavigate();
  const [userEmail, setUserEmail] = useState('');
  
  // Navigation State
  const [activeTab, setActiveTab] = useState<'security' | 'notifications' | 'preferences'>('security');

  // Password Form State
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  
  // UI State
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState({ text: '', type: '' });

  // Mock states for UI demonstration
  const [emailAlerts, setEmailAlerts] = useState(true);
  const [smsAlerts, setSmsAlerts] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (!stored) {
      navigate('/login', { replace: true });
      return;
    }
    const parsed = JSON.parse(stored);
    setUserEmail(parsed.email);
  }, [navigate]);

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage({ text: '', type: '' });

    if (newPassword !== confirmPassword) {
      setMessage({ text: "New passwords do not match.", type: "error" });
      return;
    }

    setIsLoading(true);

    try {
      const res = await fetch('http://localhost:8083/api/users/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: userEmail,
          oldPassword: oldPassword,
          newPassword: newPassword
        })
      });

      const data = await res.json();

      if (data.success) {
        setMessage({ text: "Password updated successfully!", type: "success" });
        setOldPassword('');
        setNewPassword('');
        setConfirmPassword('');
      } else {
        setMessage({ text: data.message || "Failed to update password.", type: "error" });
      }
    } catch (err) {
      setMessage({ text: "Server error. Please try again later.", type: "error" });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SidebarLayout title="Settings">
      <div className="settings-layout">
        
        <div className="settings-header-section">
          <h1 className="settings-title">Account Settings</h1>
          <p className="settings-subtitle">Manage your security and application preferences.</p>
        </div>

        <div className="settings-grid">
          
          {/* Left Navigation Sidebar */}
          <div className="settings-sidebar">
            <button 
              className={`settings-tab ${activeTab === 'security' ? 'active' : ''}`}
              onClick={() => setActiveTab('security')}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
              Security & Password
            </button>
            <button 
              className={`settings-tab ${activeTab === 'notifications' ? 'active' : ''}`}
              onClick={() => setActiveTab('notifications')}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
              Notifications
            </button>
            <button 
              className={`settings-tab ${activeTab === 'preferences' ? 'active' : ''}`}
              onClick={() => setActiveTab('preferences')}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
              Preferences
            </button>
          </div>

          {/* Right Content Area */}
          <div className="settings-content">
            
            {/* --- SECURITY TAB --- */}
            {activeTab === 'security' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Update Password</h2>
                  <p>Ensure your account is using a long, random password to stay secure.</p>
                </div>

                <form onSubmit={handlePasswordChange} className="settings-form-grid">
                  {message.text && (
                    <div className={`settings-alert ${message.type}`}>
                      {message.text}
                    </div>
                  )}

                  <div className="settings-form-group">
                    <label>Current Password</label>
                    <input 
                      type="password" 
                      placeholder="Enter current password" 
                      value={oldPassword}
                      onChange={(e) => setOldPassword(e.target.value)}
                      required
                    />
                  </div>

                  <div className="settings-form-group">
                    <label>New Password</label>
                    <input 
                      type="password" 
                      placeholder="Create new password (min. 6 characters)" 
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      required
                      minLength={6}
                    />
                  </div>

                  <div className="settings-form-group">
                    <label>Confirm New Password</label>
                    <input 
                      type="password" 
                      placeholder="Confirm new password" 
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                      minLength={6}
                    />
                  </div>

                  <div className="settings-form-group" style={{ marginTop: '8px' }}>
                    <button type="submit" className="settings-save-btn" disabled={isLoading}>
                      {isLoading ? 'Updating...' : 'Update Password'}
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* --- NOTIFICATIONS TAB --- */}
            {activeTab === 'notifications' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Notification Preferences</h2>
                  <p>Choose what alerts you receive and how you get them.</p>
                </div>

                <div className="settings-toggle-row">
                  <div className="settings-toggle-info">
                    <h4>Email Alerts</h4>
                    <p>Receive appointment updates and messages via email.</p>
                  </div>
                  <label className="toggle-switch">
                    <input type="checkbox" checked={emailAlerts} onChange={(e) => setEmailAlerts(e.target.checked)} />
                    <span className="toggle-slider"></span>
                  </label>
                </div>

                <div className="settings-toggle-row">
                  <div className="settings-toggle-info">
                    <h4>SMS Text Alerts</h4>
                    <p>Get a text message 1 hour before an appointment.</p>
                  </div>
                  <label className="toggle-switch">
                    <input type="checkbox" checked={smsAlerts} onChange={(e) => setSmsAlerts(e.target.checked)} />
                    <span className="toggle-slider"></span>
                  </label>
                </div>
                
                <div className="settings-form-group" style={{ marginTop: '24px' }}>
                  <button className="settings-save-btn">Save Preferences</button>
                </div>
              </div>
            )}

            {/* --- PREFERENCES TAB --- */}
            {activeTab === 'preferences' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Application Preferences</h2>
                  <p>Customize your TheraPea dashboard experience.</p>
                </div>

                <div className="settings-form-grid">
                  <div className="settings-form-group">
                    <label>Language</label>
                    <select>
                      <option>English (US)</option>
                      <option>Tagalog</option>
                      <option>Cebuano</option>
                    </select>
                  </div>

                  <div className="settings-form-group">
                    <label>Timezone</label>
                    <select>
                      <option>Asia/Manila (PHT)</option>
                      <option>America/New_York (EST)</option>
                      <option>Europe/London (GMT)</option>
                    </select>
                  </div>

                  <div className="settings-form-group" style={{ marginTop: '8px' }}>
                    <button className="settings-save-btn">Save Preferences</button>
                  </div>
                </div>
              </div>
            )}

          </div>
        </div>
      </div>
    </SidebarLayout>
  );
};

export default Settings;