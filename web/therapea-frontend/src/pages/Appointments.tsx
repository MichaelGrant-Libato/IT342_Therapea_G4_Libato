import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import '../styles/Appointments.css';

interface AppointmentData {
  id: string | number;
  providerId?: string;
  providerName?: string;
  patientId?: string;
  patientName?: string;  
  date: string;
  time: string;
  type: string;
  status: string;
  assessmentType?: string;
  notes?: string;
}

const Appointments: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<'upcoming' | 'past' | 'calendar'>('upcoming');
  const [appointments, setAppointments] = useState<AppointmentData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [userRole, setUserRole] = useState<'PATIENT' | 'DOCTOR'>('PATIENT');

  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [selectedDayApts, setSelectedDayApts] = useState<AppointmentData[] | null>(null);

  const [selectedAppointment, setSelectedAppointment] = useState<AppointmentData | null>(null);
  const [cancelTargetId, setCancelTargetId] = useState<string | number | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [isCanceling, setIsCanceling] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<string | number | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const fetchAppointments = async () => {
    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (!stored) { navigate('/login', { replace: true }); return; }

    const parsedUser = JSON.parse(stored);
    setUserRole(parsedUser.role || 'PATIENT');

    try {
      const res = await fetch(`http://localhost:8083/api/appointments/user?email=${encodeURIComponent(parsedUser.email)}`);
      if (res.ok) {
        const data = await res.json();
        if (data.success) { setAppointments(data.appointments); } 
        else { setAppointments([]); }
      } else { setAppointments([]); }
    } catch (err) {
      console.error("API Fetch failed:", err);
      setAppointments([]); 
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchAppointments(); }, [navigate]);

  const submitCancellation = async () => {
    if (!cancelTargetId) return;
    if (cancelReason.trim() === '') { alert("Provide a reason."); return; }
    setIsCanceling(true);
    try {
      const res = await fetch(`http://localhost:8083/api/appointments/${cancelTargetId}/cancel`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: cancelReason })
      });
      if (res.ok) setAppointments(prev => prev.map(apt => apt.id === cancelTargetId ? { ...apt, status: 'Canceled' } : apt));
    } catch (err) { console.error("Cancel fail:", err); } finally { setCancelTargetId(null); setCancelReason(''); setIsCanceling(false); }
  };

  const submitDeletion = async () => {
    if (!deleteTargetId) return;
    setIsDeleting(true);
    try {
      const res = await fetch(`http://localhost:8083/api/appointments/${deleteTargetId}`, { method: 'DELETE' });
      if (res.ok) setAppointments(prev => prev.filter(apt => apt.id !== deleteTargetId));
    } catch (err) { console.error("Delete fail:", err); } finally { setDeleteTargetId(null); setIsDeleting(false); }
  };

  const upcomingAppointments = appointments.filter(a => a.status === 'Scheduled');
  const pastAppointments = appointments.filter(a => a.status === 'Completed' || a.status === 'Canceled');
  const displayedAppointments = activeTab === 'upcoming' ? upcomingAppointments : pastAppointments;

  const daysInMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 0).getDate();
  const firstDayIndex = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), 1).getDay();
  const blanks = Array.from({ length: firstDayIndex }, (_, i) => i);
  const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);
  const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
  const dayNames = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

  return (
    <SidebarLayout title="Appointments">
      <div className="appt-content-wrapper">
        
        <div className="appt-header-row">
          <div>
            <h1 className="appt-title">{userRole === 'DOCTOR' ? 'Your Schedule' : 'Your Appointments'}</h1>
            <p className="appt-subtitle">{userRole === 'DOCTOR' ? 'Manage your upcoming sessions.' : 'View and manage your therapy sessions.'}</p>
          </div>
          {userRole === 'PATIENT' && <button className="appt-btn-book" onClick={() => navigate('/therapists')}>Book a Session</button>}
        </div>

        <div className="appt-tabs">
          <button className={`tab-btn ${activeTab === 'upcoming' ? 'active' : ''}`} onClick={() => {setActiveTab('upcoming'); setSelectedDayApts(null)}}>Upcoming</button>
          <button className={`tab-btn ${activeTab === 'past' ? 'active' : ''}`} onClick={() => {setActiveTab('past'); setSelectedDayApts(null)}}>Past Sessions</button>
          {userRole === 'DOCTOR' && (
            <button className={`tab-btn ${activeTab === 'calendar' ? 'active' : ''}`} onClick={() => setActiveTab('calendar')}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{marginRight: '6px'}}><rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>
              Calendar View
            </button>
          )}
        </div>

        {activeTab === 'calendar' && userRole === 'DOCTOR' ? (
          <div className="doc-calendar-container">
            <div className="co-calendar-wrap" style={{ maxWidth: '600px', margin: '0 auto 32px auto' }}>
              <div className="co-calendar-header">
                <button className="co-cal-nav" onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1))}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6"/></svg>
                </button>
                <div className="co-cal-month">{monthNames[currentMonth.getMonth()]} {currentMonth.getFullYear()}</div>
                <button className="co-cal-nav" onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1))}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
                </button>
              </div>
              
              <div className="co-calendar-grid">
                {dayNames.map(d => <div key={d} className="co-cal-day-name">{d}</div>)}
                {blanks.map(b => <div key={`b-${b}`} className="co-cal-day empty" />)}
                {days.map(d => {
                  const dateStr = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), d).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
                  const dayApts = appointments.filter(a => a.date === dateStr && a.status === 'Scheduled');
                  const hasApts = dayApts.length > 0;

                  return (
                    <button 
                      key={d} 
                      className={`co-cal-day doc-day ${hasApts ? 'has-events' : ''}`} 
                      onClick={() => setSelectedDayApts(hasApts ? dayApts : null)}
                    >
                      {d}
                      {hasApts && <span className="doc-event-dot"></span>}
                    </button>
                  );
                })}
              </div>
            </div>

            {selectedDayApts && (
              <div className="doc-selected-day-list animate-in">
                <h3 className="doc-selected-title">Appointments for {selectedDayApts[0].date}</h3>
                {selectedDayApts.map(apt => (
                  <div key={apt.id} className="appt-card-item" onClick={() => setSelectedAppointment(apt)}>
                    <div className="appt-info">
                      <div className="appt-name-row">
                        <h2>{apt.patientName}</h2>
                        <span className={`status-badge ${apt.status.toLowerCase()}`}>{apt.status}</span>
                      </div>
                      <p className="appt-meta">{apt.time} • {apt.type}</p>
                    </div>
                    <div className="appt-actions">
                      <button className="appt-btn-primary" onClick={(e) => { e.stopPropagation(); }}>Join Video</button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="appt-list">
            {isLoading ? (
               <div className="appt-loading">Loading appointments...</div>
            ) : displayedAppointments.length === 0 ? (
              <div className="appt-empty-state">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>
                <p>{activeTab === 'upcoming' ? "No upcoming appointments." : "No past sessions."}</p>
              </div>
            ) : (
              displayedAppointments.map((apt) => (
                <div key={apt.id} className="appt-card-item" onClick={() => setSelectedAppointment(apt)}>
                  <div className="appt-info">
                    <div className="appt-name-row">
                      <h2>{userRole === 'DOCTOR' ? apt.patientName : apt.providerName}</h2>
                      <span className={`status-badge ${apt.status.toLowerCase()}`}>{apt.status}</span>
                    </div>
                    <p className="appt-meta">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>
                      {apt.date} at {apt.time} • {apt.type}
                    </p>
                  </div>

                  <div className="appt-actions">
                    {activeTab === 'upcoming' && (
                      <>
                        {userRole === 'PATIENT' && <button className="appt-btn-outline" onClick={(e) => { e.stopPropagation(); setCancelTargetId(apt.id); }}>Cancel</button>}
                        <button className="appt-btn-primary" onClick={(e) => { e.stopPropagation(); }}>Join Video</button>
                      </>
                    )}
                    {activeTab === 'past' && (
                      <>
                        <button className="appt-btn-outline" onClick={(e) => { e.stopPropagation(); }}>{userRole === 'DOCTOR' ? 'Write Notes' : 'View Notes'}</button>
                        <button className="appt-btn-icon-delete" onClick={(e) => { e.stopPropagation(); setDeleteTargetId(apt.id); }} title="Delete from history">
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
                        </button>
                      </>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* ── Details Modal (Includes Notes & Assessment) ── */}
      {selectedAppointment && (
        <div className="appt-modal-overlay" onClick={() => setSelectedAppointment(null)}>
          <div className="appt-modal-card details-card" onClick={e => e.stopPropagation()}>
            <div className="appt-modal-header">
              <h2 className="appt-modal-title">Appointment Details</h2>
              <button className="appt-modal-close" onClick={() => setSelectedAppointment(null)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            
            <div className="appt-details-grid">
              <div className="detail-group">
                <label>{userRole === 'DOCTOR' ? 'Patient Name' : 'Provider Name'}</label>
                <p>{userRole === 'DOCTOR' ? selectedAppointment.patientName : selectedAppointment.providerName}</p>
              </div>
              <div className="detail-group">
                <label>Status</label>
                <p><span className={`status-badge ${selectedAppointment.status.toLowerCase()}`}>{selectedAppointment.status}</span></p>
              </div>
              <div className="detail-group">
                <label>Date</label>
                <p>{selectedAppointment.date}</p>
              </div>
              <div className="detail-group">
                <label>Time</label>
                <p>{selectedAppointment.time}</p>
              </div>
              <div className="detail-group">
                <label>Session Format</label>
                <p>{selectedAppointment.type}</p>
              </div>
              <div className="detail-group">
                <label>Focus Area / Intake</label>
                <p>{selectedAppointment.assessmentType || 'General Consultation'}</p>
              </div>
              
              {/* ── NOTES RENDERED HERE ── */}
              {selectedAppointment.notes && (
                <div className="detail-group full-width">
                  <label>Additional Notes</label>
                  <p className="notes-box">{selectedAppointment.notes}</p>
                </div>
              )}
            </div>
            
            <div className="appt-modal-actions mt-24">
              <button className="appt-btn-outline full" onClick={() => setSelectedAppointment(null)}>Close Window</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Cancellation Modal ── */}
      {cancelTargetId && (
        <div className="appt-modal-overlay" onClick={() => !isCanceling && setCancelTargetId(null)}>
          <div className="appt-modal-card" onClick={e => e.stopPropagation()}>
            <h2 className="appt-modal-title">Cancel Appointment?</h2>
            <p className="appt-modal-text">Note our 24-hour policy. Please select a reason:</p>
            <select value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} className="appt-select">
              <option value="" disabled>Select a reason...</option>
              <option value="Scheduling conflict">Scheduling conflict</option>
              <option value="Other">Other</option>
            </select>
            <div className="appt-modal-actions">
              <button className="appt-btn-outline" onClick={() => setCancelTargetId(null)} disabled={isCanceling}>Keep</button>
              <button className="appt-btn-danger" onClick={submitCancellation} disabled={!cancelReason || isCanceling}>
                {isCanceling ? 'Processing...' : 'Confirm Cancellation'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Deletion Modal ── */}
      {deleteTargetId && (
        <div className="appt-modal-overlay" onClick={() => !isDeleting && setDeleteTargetId(null)}>
          <div className="appt-modal-card" onClick={e => e.stopPropagation()}>
            <h2 className="appt-modal-title">Delete Record?</h2>
            <p className="appt-modal-text">Permanently remove this session from your history?</p>
            <div className="appt-modal-actions">
              <button className="appt-btn-outline" onClick={() => setDeleteTargetId(null)} disabled={isDeleting}>Cancel</button>
              <button className="appt-btn-danger" onClick={submitDeletion} disabled={isDeleting}>
                {isDeleting ? 'Deleting...' : 'Delete Permanently'}
              </button>
            </div>
          </div>
        </div>
      )}
    </SidebarLayout>
  );
};

export default Appointments;