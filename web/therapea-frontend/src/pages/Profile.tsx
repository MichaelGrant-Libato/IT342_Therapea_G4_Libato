import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import { createClient } from '@supabase/supabase-js';
import '../styles/Profile.css';

// Initialize Supabase Client using Vite Environment Variables
const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseKey = import.meta.env.VITE_SUPABASE_ANON_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

interface UserData {
  userId?: string;
  email: string;
  fullName: string;
  role: string;
  clinicalBio?: string;
  hourlyRate?: number;
  specialties?: string[];
  phone?: string;
  profilePictureUrl?: string; 
}

const Profile: React.FC = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'personal' | 'professional' | 'security'>('personal');
  const [isEditing, setIsEditing] = useState(false);
  const [isUploadingImage, setIsUploadingImage] = useState(false);

  const [formData, setFormData] = useState<UserData>({
    email: '', fullName: '', role: 'PATIENT'
  });

  useEffect(() => {
    const loadProfile = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) {
        navigate('/login', { replace: true });
        return;
      }

      const parsed = JSON.parse(stored);
      const email = parsed.email;

      try {
        const res = await fetch(`http://localhost:8083/api/dashboard/profile?email=${encodeURIComponent(email)}`);
        if (res.ok) {
          const data = await res.json();
          // 🔴 THE FIX: If data is valid, merge it! Don't blindly overwrite.
          if (data && !data.error) {
            const mergedUser = { 
              ...parsed, 
              ...data, 
              // Keep the stored picture if the backend forgets to send it
              profilePictureUrl: data.profilePictureUrl || parsed.profilePictureUrl 
            };
            setUser(mergedUser);
            setFormData(mergedUser);
            localStorage.setItem('user', JSON.stringify(mergedUser));
          } else {
            setUser(parsed);
            setFormData(parsed);
          }
        } else {
          setUser(parsed);
          setFormData(parsed);
        }
      } catch (err) {
        setUser(parsed);
        setFormData(parsed);
      } finally {
        setIsLoading(false);
      }
    };

    loadProfile();
  }, [navigate]);

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!isEditing) return; 

    const file = e.target.files?.[0];
    if (!file || !user) return;

    setIsUploadingImage(true);

    try {
      const fileExt = file.name.split('.').pop();
      const fileName = `${user.email.split('@')[0]}-${Date.now()}.${fileExt}`;
      const filePath = `public/${fileName}`;

      const { error: uploadError } = await supabase.storage
        .from('profiles')
        .upload(filePath, file);

      if (uploadError) throw uploadError;

      const { data: { publicUrl } } = supabase.storage
        .from('profiles')
        .getPublicUrl(filePath);

      const updateRes = await fetch('http://localhost:8083/api/users/update', { 
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email, profilePictureUrl: publicUrl })
      });

      if (updateRes.ok) {
        const updatedUser = { ...user, profilePictureUrl: publicUrl };
        setUser(updatedUser);
        setFormData(prev => ({ ...prev, profilePictureUrl: publicUrl }));
        localStorage.setItem('user', JSON.stringify(updatedUser));
      } else {
        alert("Image uploaded to Supabase, but failed to save URL to database.");
      }

    } catch (err) {
      console.error("Error uploading image:", err);
      alert("Failed to upload image. Please check your Supabase bucket.");
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handleSave = () => {
    setIsEditing(false);
    setUser(formData);
    
    const stored = localStorage.getItem('user');
    if (stored) {
      const parsed = JSON.parse(stored);
      localStorage.setItem('user', JSON.stringify({ ...parsed, ...formData }));
    }
  };

  const handleCancel = () => {
    setIsEditing(false);
    if (user) setFormData(user); 
  };

  if (isLoading || !user) {
    return (
      <SidebarLayout title="Profile">
        <div className="pf-loading" style={{ height: '50vh', background: 'transparent' }}>
          <p>Loading profile...</p>
        </div>
      </SidebarLayout>
    );
  }

  const isDoctor = user.role === 'DOCTOR';
  const initials = user.fullName ? user.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : '?';

  return (
    <SidebarLayout title="Profile">
      <div className="pf-layout">
        
        <div className="pf-header-section">
          <h1 className="pf-title">Account Profile</h1>
          <p className="pf-subtitle">Manage your personal information and account settings.</p>
        </div>

        <div className="pf-grid">
          <div className="pf-col-left">
            <div className="pf-card summary-card">
              
              <div 
                className="pf-avatar-wrapper" 
                onClick={() => isEditing && document.getElementById('profile-pic-upload')?.click()}
                style={{ 
                  cursor: isEditing ? 'pointer' : 'default', 
                  position: 'relative', 
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: '50%', 
                  margin: '0 auto 16px auto'
                }}
                title={isEditing ? "Click to change profile picture" : ""}
              >
                {user.profilePictureUrl ? (
                  <img 
                    src={user.profilePictureUrl} 
                    alt="Profile" 
                    className={`pf-avatar-large ${isDoctor ? 'doctor' : ''}`}
                    style={{ objectFit: 'cover', margin: 0, padding: 0 }} 
                  />
                ) : (
                  <div className={`pf-avatar-large ${isDoctor ? 'doctor' : ''}`} style={{ margin: 0 }}>
                    {initials}
                  </div>
                )}
                
                {isUploadingImage && (
                  <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(255,255,255,0.7)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <span style={{ fontSize: '11px', fontWeight: 'bold', color: '#333' }}>Uploading...</span>
                  </div>
                )}

                {isEditing && !isUploadingImage && (
                  <div style={{
                    position: 'absolute',
                    bottom: '5px',
                    right: '5px',
                    backgroundColor: 'var(--primary, #0A5C36)', 
                    color: 'white',
                    borderRadius: '50%',
                    padding: '6px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    border: '2px solid white',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                  }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 20h9"></path>
                      <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
                    </svg>
                  </div>
                )}
              </div>

              <input 
                type="file" 
                id="profile-pic-upload" 
                accept="image/*" 
                style={{ display: 'none' }} 
                onChange={handleImageUpload}
              />

              <h2 className="pf-summary-name">{isDoctor ? `Dr. ${user.fullName}` : user.fullName}</h2>
              <p className="pf-summary-role">{isDoctor ? 'Licensed Provider' : 'Patient'}</p>
              
              <div className="pf-badge-row">
                <span className="pf-status-badge verified">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><polyline points="20 6 9 17 4 12"/></svg>
                  Account Verified
                </span>
                {isDoctor && (
                  <span className="pf-status-badge prc">PRC Verified</span>
                )}
              </div>
            </div>

            <div className="pf-card nav-card">
              <button 
                className={`pf-tab-btn ${activeTab === 'personal' ? 'active' : ''}`}
                onClick={() => setActiveTab('personal')}
              >
                Personal Details
              </button>
              {isDoctor && (
                <button 
                  className={`pf-tab-btn ${activeTab === 'professional' ? 'active' : ''}`}
                  onClick={() => setActiveTab('professional')}
                >
                  Professional Profile
                </button>
              )}
              <button 
                className={`pf-tab-btn ${activeTab === 'security' ? 'active' : ''}`}
                onClick={() => setActiveTab('security')}
              >
                Security & Password
              </button>
            </div>
          </div>

          <div className="pf-col-right">
            <div className="pf-card content-card">
              <div className="pf-card-header">
                <h2>
                  {activeTab === 'personal' && 'Personal Details'}
                  {activeTab === 'professional' && 'Professional Profile'}
                  {activeTab === 'security' && 'Security Settings'}
                </h2>
                {activeTab !== 'security' && (
                  !isEditing ? (
                    <button className="pf-edit-btn" onClick={() => setIsEditing(true)}>Edit Profile</button>
                  ) : (
                    <div className="pf-action-group">
                      <button className="pf-cancel-btn" onClick={handleCancel}>Cancel</button>
                      <button className="pf-save-btn" onClick={handleSave}>Save Changes</button>
                    </div>
                  )
                )}
              </div>

              {activeTab === 'personal' && (
                <div className="pf-form-grid">
                  <div className="pf-form-group">
                    <label>Full Name</label>
                    <input 
                      type="text" 
                      value={formData.fullName} 
                      onChange={e => setFormData({...formData, fullName: e.target.value})}
                      disabled={!isEditing} 
                    />
                  </div>
                  <div className="pf-form-group">
                    <label>Email Address</label>
                    <input 
                      type="email" 
                      value={formData.email} 
                      disabled={true} 
                      style={{ background: 'var(--bg)', cursor: 'not-allowed' }}
                    />
                  </div>
                  <div className="pf-form-group">
                    <label>Phone Number</label>
                    <input 
                      type="tel" 
                      value={formData.phone || ''} 
                      placeholder="+63 912 345 6789"
                      onChange={e => setFormData({...formData, phone: e.target.value})}
                      disabled={!isEditing} 
                    />
                  </div>
                  {!isDoctor && (
                    <div className="pf-form-group full-width">
                      <label>Emergency Contact (Optional)</label>
                      <input 
                        type="text" 
                        placeholder="Name and phone number"
                        disabled={!isEditing} 
                      />
                    </div>
                  )}
                </div>
              )}

              {activeTab === 'professional' && isDoctor && (
                <div className="pf-form-grid">
                  <div className="pf-form-group">
                    <label>Hourly Rate (₱)</label>
                    <input 
                      type="number" 
                      value={formData.hourlyRate || 1500} 
                      onChange={e => setFormData({...formData, hourlyRate: Number(e.target.value)})}
                      disabled={!isEditing} 
                    />
                  </div>
                  <div className="pf-form-group full-width">
                    <label>Clinical Biography</label>
                    <textarea 
                      rows={5}
                      value={formData.clinicalBio || ''} 
                      placeholder="Describe your background and specialties..."
                      onChange={e => setFormData({...formData, clinicalBio: e.target.value})}
                      disabled={!isEditing} 
                    />
                  </div>
                  <div className="pf-form-group full-width">
                    <label>PRC License Document</label>
                    <div className="pf-document-box">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                        <polyline points="14 2 14 8 20 8"></polyline>
                      </svg>
                      <span>license_document.pdf</span>
                      <span className="pf-doc-status">Verified</span>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'security' && (
                <div className="pf-form-grid">
                  <div className="pf-form-group full-width">
                    <label>Current Password</label>
                    <input type="password" placeholder="••••••••" />
                  </div>
                  <div className="pf-form-group">
                    <label>New Password</label>
                    <input type="password" placeholder="Create new password" />
                  </div>
                  <div className="pf-form-group">
                    <label>Confirm New Password</label>
                    <input type="password" placeholder="Confirm new password" />
                  </div>
                  <div className="pf-form-group full-width" style={{ marginTop: '16px' }}>
                    <button className="pf-save-btn" style={{ width: 'auto' }}>Update Password</button>
                  </div>
                </div>
              )}

            </div>
          </div>
        </div>

      </div>
    </SidebarLayout>
  );
};

export default Profile;