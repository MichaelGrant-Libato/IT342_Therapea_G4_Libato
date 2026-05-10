import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../../core/components/SidebarLayout';
import './Settings.css';

const Settings: React.FC = () => {
  const navigate = useNavigate();
  const [userEmail, setUserEmail] = useState('');
  
  const [activeTab, setActiveTab] = useState<'notifications' | 'preferences'>('preferences');
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState({ text: '', type: '' });

  const [settings, setSettings] = useState({
    emailAlerts: true,
    smsAlerts: false,
    marketingEmails: false,
    language: 'English (US)',
    timezone: 'Asia/Manila (PHT)',
    theme: 'Light'
  });

  useEffect(() => {
    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (!stored) {
      navigate('/login', { replace: true });
      return;
    }
    const parsed = JSON.parse(stored);
    setUserEmail(parsed.email);

    // Load settings from local storage
    const savedSettings = localStorage.getItem(`settings_${parsed.email}`);
    if (savedSettings) {
      setSettings(JSON.parse(savedSettings));
    }
  }, [navigate]);

  const handleSaveSettings = async () => {
    setIsSaving(true);
    setMessage({ text: '', type: '' });

    try {
      // 1. Save to local storage
      localStorage.setItem(`settings_${userEmail}`, JSON.stringify(settings));
      
      // 2. 🔴 IMMEDIATELY APPLY THEME TO THE ENTIRE APP 🔴
      if (settings.theme === 'Dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
      } else {
        document.documentElement.removeAttribute('data-theme');
      }

      // 3. Fake API Delay
      setTimeout(() => {
        setMessage({ text: "Settings saved successfully!", type: "success" });
        setIsSaving(false);
      }, 800);
    } catch (err) {
      setMessage({ text: "Failed to save settings.", type: "error" });
      setIsSaving(false);
    }
  };

  return (
    <SidebarLayout title="Settings">
      <div className="settings-layout">
        
        <div className="settings-header-section">
          <h1 className="settings-title">Account Settings</h1>
          <p className="settings-subtitle">Manage your notification alerts and application preferences.</p>
        </div>

        <div className="settings-grid">
          <div className="settings-sidebar">
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

          <div className="settings-content">
            {message.text && (
              <div className={`settings-alert ${message.type}`} style={{ marginBottom: '20px' }}>
                {message.text}
              </div>
            )}

            {activeTab === 'notifications' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Notification Preferences</h2>
                  <p>Choose how you want to be alerted about sessions and messages.</p>
                </div>

                <div className="settings-toggle-row">
                  <div className="settings-toggle-info">
                    <h4>Email Alerts</h4>
                    <p>Receive appointment confirmations and clinical updates via email.</p>
                  </div>
                  <label className="toggle-switch">
                    <input type="checkbox" checked={settings.emailAlerts} onChange={(e) => setSettings({...settings, emailAlerts: e.target.checked})} />
                    <span className="toggle-slider"></span>
                  </label>
                </div>

                <div className="settings-toggle-row">
                  <div className="settings-toggle-info">
                    <h4>SMS Text Alerts</h4>
                    <p>Get a direct text message 1 hour before your scheduled session.</p>
                  </div>
                  <label className="toggle-switch">
                    <input type="checkbox" checked={settings.smsAlerts} onChange={(e) => setSettings({...settings, smsAlerts: e.target.checked})} />
                    <span className="toggle-slider"></span>
                  </label>
                </div>
                
                <div className="settings-form-group" style={{ marginTop: '24px' }}>
                  <button className="settings-save-btn" onClick={handleSaveSettings} disabled={isSaving}>
                    {isSaving ? 'Saving...' : 'Save Notification Settings'}
                  </button>
                </div>
              </div>
            )}

            {activeTab === 'preferences' && (
              <div className="settings-card">
                <div className="settings-card-header">
                  <h2>Application Preferences</h2>
                  <p>Customize your regional settings and visual theme.</p>
                </div>

                <div className="settings-form-grid">
                  <div className="settings-form-group">
                    <label>Language</label>
                    <select value={settings.language} onChange={(e) => setSettings({...settings, language: e.target.value})}>
                      <option>English (US)</option>
                      <option>Tagalog</option>
                      <option>Cebuano</option>
                    </select>
                  </div>

                  <div className="settings-form-group">
                    <label>Timezone</label>
                    <select value={settings.timezone} onChange={(e) => setSettings({...settings, timezone: e.target.value})}>
                      <option>Asia/Manila (PHT)</option>
                      <option>America/New_York (EST)</option>
                      <option>Europe/London (GMT)</option>
                    </select>
                  </div>

                  <div className="settings-form-group">
                    <label>Interface Theme</label>
                    <select value={settings.theme} onChange={(e) => setSettings({...settings, theme: e.target.value})}>
                      <option>Light</option>
                      <option>Dark</option> {/* 🔴 Fixed: Removed coming soon */}
                    </select>
                  </div>

                  <div className="settings-form-group" style={{ marginTop: '8px' }}>
                    <button className="settings-save-btn" onClick={handleSaveSettings} disabled={isSaving}>
                      {isSaving ? 'Saving...' : 'Save App Preferences'}
                    </button>
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