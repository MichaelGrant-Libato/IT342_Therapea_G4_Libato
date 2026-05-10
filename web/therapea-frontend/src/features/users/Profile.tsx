import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../../core/components/SidebarLayout';
import { createClient } from '@supabase/supabase-js';
import './Profile.css';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseKey = import.meta.env.VITE_SUPABASE_ANON_KEY;
let supabase: any;
if (!(window as any).__supabaseClient) {
  (window as any).__supabaseClient = createClient(supabaseUrl, supabaseKey);
}
supabase = (window as any).__supabaseClient;

interface UserData {
  userId?: string; email: string; fullName: string; role: string;
  clinicalBio?: string; hourlyRate?: number; specialties?: string[];
  phone?: string; profilePictureUrl?: string; whatToExpect?: string;
  availableSchedule?: string; profileCompleted?: boolean;
}

type TimeBlock = { start: string; end: string; id: string };
type DaySchedule = { active: boolean; timeBlocks: TimeBlock[] };
type WeeklySchedule = Record<string, DaySchedule>;

const generateId = () => Math.random().toString(36).substring(2, 9);

const defaultSchedule: WeeklySchedule = {
  Monday:    { active: false, timeBlocks: [{ start: "09:00", end: "17:00", id: generateId() }] },
  Tuesday:   { active: false, timeBlocks: [{ start: "09:00", end: "17:00", id: generateId() }] },
  Wednesday: { active: false, timeBlocks: [{ start: "09:00", end: "17:00", id: generateId() }] },
  Thursday:  { active: false, timeBlocks: [{ start: "09:00", end: "17:00", id: generateId() }] },
  Friday:    { active: false, timeBlocks: [{ start: "09:00", end: "17:00", id: generateId() }] },
  Saturday:  { active: false, timeBlocks: [{ start: "10:00", end: "14:00", id: generateId() }] },
  Sunday:    { active: false, timeBlocks: [{ start: "10:00", end: "14:00", id: generateId() }] },
};

const formatTime12h = (time24: string) => {
  if (!time24) return "";
  const [h, m] = time24.split(':');
  const hours = parseInt(h, 10);
  const suffix = hours >= 12 ? 'PM' : 'AM';
  const hours12 = hours % 12 || 12;
  return `${hours12}:${m} ${suffix}`;
};

const parseTime24h = (time12: string) => {
  if (!time12) return "09:00";
  const match = time12.match(/(\d+):(\d+)\s*(AM|PM)/i);
  if (!match) return "09:00";
  let h = parseInt(match[1], 10);
  const m = match[2];
  const modifier = match[3].toUpperCase();
  if (modifier === 'PM' && h !== 12) h += 12;
  if (modifier === 'AM' && h === 12) h = 0;
  return `${h.toString().padStart(2, '0')}:${m}`;
};

const formatScheduleToString = (schedule: WeeklySchedule): string => {
  const activeDays = Object.entries(schedule).filter(([_, data]) => data.active);
  if (activeDays.length === 0) return "Not currently available";
  return activeDays.map(([day, data]) => {
    const timeslots = data.timeBlocks
      .filter(block => block.start && block.end)
      .map(block => `${formatTime12h(block.start)} - ${formatTime12h(block.end)}`)
      .join(' | ');
    if (!timeslots) return `${day}: 09:00 AM - 05:00 PM`;
    return `${day}: ${timeslots}`;
  }).join(', ');
};

const parseScheduleFromString = (str: string): WeeklySchedule => {
  const parsed: WeeklySchedule = JSON.parse(JSON.stringify(defaultSchedule)); 
  Object.keys(parsed).forEach(day => parsed[day].timeBlocks.forEach(block => block.id = generateId()));

  if (!str || str === "Not currently available") {
    Object.keys(parsed).forEach(k => parsed[k].active = false);
    return parsed;
  }
  Object.keys(parsed).forEach(k => parsed[k].active = false);
  const daysString = str.split(', ');
  
  daysString.forEach(dayPart => {
    const splitIndex = dayPart.indexOf(': ');
    if (splitIndex > -1) {
      const day = dayPart.substring(0, splitIndex).trim();
      const timesString = dayPart.substring(splitIndex + 2).trim(); 
      if (parsed[day] && timesString) {
        parsed[day].active = true;
        const blocks = timesString.split(' | ');
        const parsedBlocks: TimeBlock[] = [];
        blocks.forEach(blockStr => {
           const [startStr, endStr] = blockStr.split(' - ');
           if (startStr && endStr) {
               parsedBlocks.push({ id: generateId(), start: parseTime24h(startStr.trim()), end: parseTime24h(endStr.trim()) });
           }
        });
        if (parsedBlocks.length > 0) parsed[day].timeBlocks = parsedBlocks;
      }
    }
  });
  return parsed;
};

const Profile: React.FC = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'personal' | 'professional' | 'security'>('personal');
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  
  const [selectedImageFile, setSelectedImageFile] = useState<File | null>(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null);
  
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({ schedule: false, expectations: false });

  const [formData, setFormData] = useState<UserData>({ email: '', fullName: '', role: 'PATIENT' });
  const [weeklySchedule, setWeeklySchedule] = useState<WeeklySchedule>(defaultSchedule);

  const isDoctor = user?.role === 'DOCTOR';
  const isMandatorySetup = isDoctor && user?.profileCompleted === false;

  const timeBlockErrors = useMemo(() => {
    const errors: Record<string, string> = {};
    Object.values(weeklySchedule).forEach(dayData => {
        if (!dayData.active) return;
        const blocksWithMins = dayData.timeBlocks.map(b => {
            const startMins = parseInt(b.start.split(':')[0]) * 60 + parseInt(b.start.split(':')[1]);
            const endMins = parseInt(b.end.split(':')[0]) * 60 + parseInt(b.end.split(':')[1]);
            return { ...b, startMins, endMins };
        });

        for (let i = 0; i < blocksWithMins.length; i++) {
            const current = blocksWithMins[i];
            if (current.startMins >= current.endMins) {
                errors[current.id] = "End time must be after start time.";
                continue;
            }
            for (let j = 0; j < blocksWithMins.length; j++) {
                if (i === j) continue;
                const other = blocksWithMins[j];
                if (current.startMins < other.endMins && current.endMins > other.startMins) {
                    errors[current.id] = "Time slot overlaps with another block.";
                }
            }
        }
    });
    return errors;
  }, [weeklySchedule]);


  useEffect(() => {
    const loadProfile = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) { navigate('/login', { replace: true }); return; }
      const parsed = JSON.parse(stored);
      try {
        const res = await fetch(`http://localhost:8083/api/dashboard/profile?email=${encodeURIComponent(parsed.email)}`);
        if (res.ok) {
          const data = await res.json();
          if (data && !data.error) {
            const mergedUser = { ...parsed, ...data, profilePictureUrl: data.profilePictureUrl || parsed.profilePictureUrl };
            setUser(mergedUser); setFormData(mergedUser);
            setWeeklySchedule(parseScheduleFromString(mergedUser.availableSchedule || ""));
            localStorage.setItem('user', JSON.stringify(mergedUser));
          } else { setUser(parsed); setFormData(parsed); }
        } else { setUser(parsed); setFormData(parsed); }
      } catch (err) {
        setUser(parsed); setFormData(parsed);
      } finally { setIsLoading(false); }
    };
    loadProfile();
  }, [navigate]);

  useEffect(() => {
    if (isMandatorySetup) {
      setActiveTab('professional');
      setIsEditing(true);
      window.history.pushState(null, '', window.location.href);
      const handlePopState = () => window.history.pushState(null, '', window.location.href);
      window.addEventListener('popstate', handlePopState);
      return () => window.removeEventListener('popstate', handlePopState);
    }
  }, [isMandatorySetup]);

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!isEditing) return; 
    const file = e.target.files?.[0];
    if (!file) return;

    setSelectedImageFile(file);
    setImagePreviewUrl(URL.createObjectURL(file)); 
  };

  const toggleDayActive = (day: string, isActive: boolean) => {
      setWeeklySchedule(prev => ({ ...prev, [day]: { ...prev[day], active: isActive } }));
      if (fieldErrors.schedule) setFieldErrors(prev => ({ ...prev, schedule: false }));
  };

  const addTimeBlock = (day: string) => {
      setWeeklySchedule(prev => ({
          ...prev, [day]: { ...prev[day], timeBlocks: [...prev[day].timeBlocks, { start: "09:00", end: "10:00", id: generateId() }] }
      }));
  };

  const removeTimeBlock = (day: string, blockId: string) => {
      setWeeklySchedule(prev => {
          if (prev[day].timeBlocks.length <= 1) return prev;
          return { ...prev, [day]: { ...prev[day], timeBlocks: prev[day].timeBlocks.filter(b => b.id !== blockId) } };
      });
  };

  const updateTimeBlock = (day: string, blockId: string, field: 'start' | 'end', value: string) => {
      setWeeklySchedule(prev => ({
          ...prev, [day]: { ...prev[day], timeBlocks: prev[day].timeBlocks.map(b => b.id === blockId ? { ...b, [field]: value } : b) }
      }));
  };

  // 🔴 NEW: Logic to determine if Doctor made actual changes to their Professional Profile
  const hasProfessionalChanges = () => {
    if (!isDoctor || !user) return false;
    
    const currentScheduleStr = formatScheduleToString(weeklySchedule);
    const originalScheduleStr = user.availableSchedule || "Not currently available";

    if (formData.hourlyRate !== user.hourlyRate) return true;
    if (formData.clinicalBio !== user.clinicalBio) return true;
    if (formData.whatToExpect !== user.whatToExpect) return true;
    if (currentScheduleStr !== originalScheduleStr) return true;

    return false;
  };

  const initiateSave = () => {
    setSaveError('');
    setFieldErrors({ schedule: false, expectations: false });
    const finalScheduleString = formatScheduleToString(weeklySchedule);

    if (isDoctor) {
      if (Object.keys(timeBlockErrors).length > 0) {
          setSaveError("Please resolve the scheduling conflicts highlighted in red.");
          return;
      }
      
      const isScheduleValid = finalScheduleString !== "Not currently available";
      const isExpectationsValid = !!formData.whatToExpect?.trim();
      
      if (isMandatorySetup && (!isScheduleValid || !isExpectationsValid)) {
        setFieldErrors({ schedule: !isScheduleValid, expectations: !isExpectationsValid });
        setSaveError("Please complete the highlighted fields to continue.");
        return;
      }
    }

    // 🔴 NEW: Decide whether to show the modal or save directly
    if (isDoctor && (isMandatorySetup || hasProfessionalChanges())) {
      setShowReviewModal(true); 
    } else {
      executeSave(); // Bypass modal if only personal details or image changed
    }
  };

  const executeSave = async () => {
    setIsSaving(true);
    let finalProfilePictureUrl = formData.profilePictureUrl;

    if (selectedImageFile && user) {
      try {
        const fileExt = selectedImageFile.name.split('.').pop();
        const fileName = `${user.email.split('@')[0]}-${Date.now()}.${fileExt}`;
        const filePath = `public/${fileName}`;
        
        const { error: uploadError } = await supabase.storage.from('profiles').upload(filePath, selectedImageFile);
        if (uploadError) throw uploadError;
        
        const { data: { publicUrl } } = supabase.storage.from('profiles').getPublicUrl(filePath);
        finalProfilePictureUrl = publicUrl; 
      } catch (err) {
        setShowReviewModal(false);
        setSaveError("Failed to upload profile picture. Please try again.");
        setIsSaving(false);
        return; 
      }
    }

    const finalScheduleString = formatScheduleToString(weeklySchedule);
    const isNowCompleted = isDoctor ? !!(formData.whatToExpect?.trim() && finalScheduleString !== "Not currently available") : true;
    
    const payload = { 
      ...formData, 
      availableSchedule: finalScheduleString, 
      profileCompleted: isNowCompleted,
      profilePictureUrl: finalProfilePictureUrl 
    };

    try {
      const res = await fetch('http://localhost:8083/api/users/update', { 
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
      });
      if (res.ok) {
        setIsEditing(false); 
        setUser(payload);
        
        const stored = localStorage.getItem('user');
        if (stored) {
          const parsed = JSON.parse(stored);
          localStorage.setItem('user', JSON.stringify({ ...parsed, ...payload }));
        }

        setSelectedImageFile(null);
        setImagePreviewUrl(null);

        setShowReviewModal(false);
        setShowSuccessModal(true); 
      } else {
        setShowReviewModal(false); setSaveError("Failed to save changes. Please try again.");
      }
    } catch (err) {
      setShowReviewModal(false); setSaveError("Network error. Please check your connection.");
    } finally { 
      setIsSaving(false); 
    }
  };

  const handleCancel = () => {
    setIsEditing(false); 
    setSaveError(''); 
    setFieldErrors({ schedule: false, expectations: false });
    
    setSelectedImageFile(null);
    setImagePreviewUrl(null);

    if (user) { 
      setFormData(user); 
      setWeeklySchedule(parseScheduleFromString(user.availableSchedule || "")); 
    }
  };

  if (isLoading || !user) return <SidebarLayout title="Profile"><div className="pf-loading"><p>Loading profile...</p></div></SidebarLayout>;
  
  const initials = user.fullName ? user.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : '?';
  const displayAvatarUrl = imagePreviewUrl || user.profilePictureUrl;

  return (
    <SidebarLayout title={isMandatorySetup ? "Complete Profile Setup" : "Profile"}>
      
      {isMandatorySetup && (
        <style>{`
          .db-sidebar { display: none !important; }
          .db-topbar { display: none !important; }
          .db-main { margin-left: 0 !important; width: 100vw !important; height: 100vh !important; display: flex !important; align-items: center !important; justify-content: center !important; background: var(--bg) !important; }
          .db-content { width: 100% !important; max-width: 1100px !important; padding: 0 !important; margin: 0 !important; }
        `}</style>
      )}

      <style>{`
        .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 9999; }
        .modal-card { background: #fff; width: 90%; max-width: 420px; padding: 32px; border-radius: 24px; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.1); animation: scaleIn 0.3s ease; }
        .modal-icon { width: 64px; height: 64px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px auto; }
        .modal-icon.warning { background: #FFFBEB; color: #D97706; }
        .modal-icon.success { background: #F0FDF4; color: #059669; }
        .modal-btn-row { display: flex; gap: 12px; margin-top: 24px; }
        .modal-btn { flex: 1; padding: 14px; border-radius: 12px; font-weight: 700; font-size: 15px; cursor: pointer; border: none; transition: 0.2s; }
        .modal-btn.secondary { background: #f1f5f9; color: #475569; }
        .modal-btn.secondary:hover { background: #e2e8f0; }
        .modal-btn.primary { background: #0A5C36; color: #fff; }
        .modal-btn.primary:hover { background: #064e3b; }
        .time-blocks-container { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
        .time-block-row { display: flex; align-items: center; gap: 8px; background: #f8fafc; padding: 6px 10px; border-radius: 8px; border: 1px solid #e2e8f0; }
        .block-action-btn { background: none; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 50%; transition: 0.2s; }
        .block-action-btn.add { color: #0A5C36; background: #ecfdf5; }
        .block-action-btn.add:hover { background: #d1fae5; }
        .block-action-btn.remove { color: #ef4444; background: #fef2f2; }
        .block-action-btn.remove:hover { background: #fee2e2; }
        .block-action-btn:disabled { opacity: 0.3; cursor: not-allowed; }
        .time-block-input-error { border-color: #ef4444 !important; background-color: #FEF2F2 !important; box-shadow: 0 0 0 1px #ef4444; }
        @keyframes scaleIn { from { transform: scale(0.95); opacity: 0; } to { transform: scale(1); opacity: 1; } }
      `}</style>

      {showReviewModal && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-icon warning"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
            <h2 style={{ margin: '0 0 12px 0', color: '#1e293b' }}>Review Profile Updates</h2>
            <p style={{ margin: 0, color: '#64748b', lineHeight: 1.5 }}>Are you sure you want to save these changes to your professional profile? Your schedule and session expectations will be updated immediately.</p>
            <div className="modal-btn-row">
              <button className="modal-btn secondary" onClick={() => setShowReviewModal(false)} disabled={isSaving}>Cancel</button>
              <button className="modal-btn primary" onClick={executeSave} disabled={isSaving}>{isSaving ? 'Saving...' : 'Confirm & Save'}</button>
            </div>
          </div>
        </div>
      )}

      {showSuccessModal && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-icon success"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg></div>
            <h2 style={{ margin: '0 0 12px 0', color: '#1e293b' }}>Changes Saved</h2>
            <p style={{ margin: 0, color: '#64748b', lineHeight: 1.5 }}>Your profile has been successfully updated.</p>
            <div className="modal-btn-row">
              {/* 🔴 FIXED: Simplified to just "Okay" which closes the popup */}
              <button className="modal-btn primary" onClick={() => setShowSuccessModal(false)}>Okay</button>
            </div>
          </div>
        </div>
      )}

      <div className="pf-container" style={isMandatorySetup ? { padding: '40px 20px', margin: '0 auto', width: '100%', maxHeight: '100vh', overflowY: 'auto' } : {}}>
        <div className="pf-layout">
          {isMandatorySetup && (
            <div className="pf-alert-banner">
              <h3 style={{ margin: '0 0 8px 0', fontSize: '18px', color: '#7F1D1D' }}>Action Required: Complete Your Practice Profile</h3>
              You must set up your <strong>Available Schedule</strong> and <strong>Session Expectations</strong> before you can access the platform and receive patient bookings. You cannot navigate away until this is saved.
            </div>
          )}

          <div className="pf-header-section">
            <h1 className="pf-title">Account Profile</h1>
            <p className="pf-subtitle">Manage your personal information and account settings.</p>
          </div>

          {saveError && <div className="pf-error-banner">{saveError}</div>}

          <div className="pf-grid">
            <div className="pf-col-left">
              <div className="pf-card summary-card">
                <div className="pf-avatar-wrapper" onClick={() => isEditing && document.getElementById('profile-pic-upload')?.click()} style={{ cursor: isEditing ? 'pointer' : 'default' }}>
                  {displayAvatarUrl ? (
                    <img src={displayAvatarUrl} alt="Profile" className={`pf-avatar-large ${isDoctor ? 'doctor' : ''}`} style={{ objectFit: 'cover' }} />
                  ) : (
                    <div className={`pf-avatar-large ${isDoctor ? 'doctor' : ''}`}>{initials}</div>
                  )}
                  {isEditing && (
                    <div className="pf-avatar-edit-icon"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg></div>
                  )}
                </div>
                <input type="file" id="profile-pic-upload" accept="image/*" style={{ display: 'none' }} onChange={handleImageSelect} />
                <h2 className="pf-summary-name">{isDoctor ? `Dr. ${user.fullName}` : user.fullName}</h2>
                <p className="pf-summary-role">{isDoctor ? 'Licensed Provider' : 'Patient'}</p>
                <div className="pf-badge-row">
                  <span className="pf-status-badge verified"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><polyline points="20 6 9 17 4 12"/></svg> Account Verified</span>
                  {isDoctor && <span className="pf-status-badge prc">PRC Verified</span>}
                </div>
              </div>

              <div className="pf-card nav-card">
                <button className={`pf-tab-btn ${activeTab === 'personal' ? 'active' : ''}`} onClick={() => !isMandatorySetup && setActiveTab('personal')} disabled={isMandatorySetup} style={{ opacity: isMandatorySetup ? 0.5 : 1 }}>Personal Details</button>
                {isDoctor && (
                  <button className={`pf-tab-btn ${activeTab === 'professional' ? 'active' : ''}`} onClick={() => !isMandatorySetup && setActiveTab('professional')}>Professional Profile {isMandatorySetup && <span className="pf-nav-alert-dot" />}</button>
                )}
                <button className={`pf-tab-btn ${activeTab === 'security' ? 'active' : ''}`} onClick={() => !isMandatorySetup && setActiveTab('security')} disabled={isMandatorySetup} style={{ opacity: isMandatorySetup ? 0.5 : 1 }}>Security & Password</button>
              </div>
            </div>

            <div className="pf-col-right">
              <div className="pf-card content-card">
                <div className="pf-card-header">
                  <h2>{activeTab === 'personal' ? 'Personal Details' : activeTab === 'professional' ? 'Professional Profile' : 'Security Settings'}</h2>
                  {!isEditing ? (
                      <button className="pf-edit-btn" onClick={() => setIsEditing(true)}>Edit Profile</button>
                  ) : (
                      <div className="pf-action-group">
                        {!isMandatorySetup && <button className="pf-cancel-btn" onClick={handleCancel} disabled={isSaving}>Cancel</button>}
                        <button className="pf-save-btn" onClick={initiateSave} disabled={isSaving}>{isMandatorySetup ? 'Complete Setup' : 'Save Changes'}</button>
                      </div>
                  )}
                </div>

                {activeTab === 'professional' && isDoctor && (
                  <div className="pf-form-grid">
                    <div className="pf-form-group"><label>Hourly Rate (₱)</label><input type="number" value={formData.hourlyRate || 1500} onChange={e => setFormData({...formData, hourlyRate: Number(e.target.value)})} disabled={!isEditing} /></div>
                    <div className="pf-form-group full-width"><label>Clinical Biography</label><textarea rows={4} value={formData.clinicalBio || ''} placeholder="Describe your background and specialties..." onChange={e => setFormData({...formData, clinicalBio: e.target.value})} disabled={!isEditing} /></div>
                    
                    <div className="pf-form-group full-width">
                      <label>Available Schedule <span style={{color: '#ef4444'}}>*</span></label>
                      {!isEditing ? (
                        <div className="pf-readonly-schedule">
                          {formatScheduleToString(weeklySchedule).split(', ').map((str, idx) => (
                             <div key={idx} className="pf-schedule-badge">{str}</div>
                          ))}
                        </div>
                      ) : (
                        <div className={`schedule-builder ${fieldErrors.schedule ? 'error-border' : ''}`}>
                          {Object.keys(defaultSchedule).map(day => {
                            const data = weeklySchedule[day];
                            return (
                              <div key={day} className="schedule-row" style={{ background: data.active ? '#fff' : '#f8fafc' }}>
                                <div className="schedule-day-toggle">
                                  <label className="toggle-switch">
                                    <input type="checkbox" checked={data.active} onChange={(e) => toggleDayActive(day, e.target.checked)} />
                                    <span className="slider"></span>
                                  </label>
                                  <span style={{ fontWeight: 600, color: '#1e293b' }}>{day}</span>
                                </div>
                                <div className="time-blocks-container" style={{ opacity: data.active ? 1 : 0.4, pointerEvents: data.active ? 'auto' : 'none' }}>
                                    {data.timeBlocks.map((block, idx) => {
                                        const errorMsg = timeBlockErrors[block.id];
                                        return (
                                          <div key={block.id} style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                              <div className="time-block-row">
                                                  <input type="time" className={`schedule-time-input ${errorMsg ? 'time-block-input-error' : ''}`} value={block.start} onChange={(e) => updateTimeBlock(day, block.id, 'start', e.target.value)} disabled={!data.active} />
                                                  <span style={{ color: '#64748b', fontSize: 13, fontWeight: 600 }}>to</span>
                                                  <input type="time" className={`schedule-time-input ${errorMsg ? 'time-block-input-error' : ''}`} value={block.end} onChange={(e) => updateTimeBlock(day, block.id, 'end', e.target.value)} disabled={!data.active} />
                                                  
                                                  {idx === data.timeBlocks.length - 1 ? (
                                                      <button type="button" className="block-action-btn add" onClick={() => addTimeBlock(day)} disabled={!data.active} title="Add another slot"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg></button>
                                                  ) : (
                                                      <button type="button" className="block-action-btn remove" onClick={() => removeTimeBlock(day, block.id)} disabled={!data.active} title="Remove this slot"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
                                                  )}
                                              </div>
                                              {errorMsg && data.active && <span className="pf-input-error-text" style={{ marginTop: '0', fontSize: '11px' }}>{errorMsg}</span>}
                                          </div>
                                        );
                                    })}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                      {fieldErrors.schedule && <span className="pf-input-error-text">Please activate and specify valid time slots for at least one day.</span>}
                    </div>
                    
                    <div className="pf-form-group full-width">
                      <label>What to Expect in Sessions <span style={{color: '#ef4444'}}>*</span></label>
                      <textarea rows={5} value={formData.whatToExpect || ''} placeholder="Describe your therapeutic approach, environment, and what a patient should expect during a typical session..." onChange={e => { setFormData({...formData, whatToExpect: e.target.value}); if (fieldErrors.expectations) setFieldErrors(prev => ({...prev, expectations: false})); }} disabled={!isEditing} style={{ borderColor: fieldErrors.expectations ? '#ef4444' : 'var(--border)', backgroundColor: fieldErrors.expectations ? '#FEF2F2' : 'white' }} />
                      {fieldErrors.expectations && <span className="pf-input-error-text">This field is required.</span>}
                    </div>

                    <div className="pf-form-group full-width" style={{ marginTop: 12 }}>
                      <label>PRC License Document</label>
                      <div className="pf-document-box"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg><span>license_document.pdf</span><span className="pf-doc-status">Verified</span></div>
                    </div>
                  </div>
                )}

                {activeTab === 'personal' && (
                  <div className="pf-form-grid">
                    <div className="pf-form-group"><label>Full Name</label><input type="text" value={formData.fullName} onChange={e => setFormData({...formData, fullName: e.target.value})} disabled={!isEditing} /></div>
                    <div className="pf-form-group"><label>Email Address</label><input type="email" value={formData.email} disabled={true} style={{ background: 'var(--bg-alt)', cursor: 'not-allowed' }} /></div>
                    <div className="pf-form-group"><label>Phone Number</label><input type="tel" value={formData.phone || ''} placeholder="+63 912 345 6789" onChange={e => setFormData({...formData, phone: e.target.value})} disabled={!isEditing} /></div>
                  </div>
                )}

                {activeTab === 'security' && (
                  <div className="pf-form-grid">
                    <div className="pf-form-group full-width"><label>Current Password</label><input type="password" placeholder="••••••••" disabled={!isEditing} /></div>
                    <div className="pf-form-group"><label>New Password</label><input type="password" placeholder="Create new password" disabled={!isEditing} /></div>
                    <div className="pf-form-group"><label>Confirm New Password</label><input type="password" placeholder="Confirm new password" disabled={!isEditing} /></div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </SidebarLayout>
  );
};

export default Profile;