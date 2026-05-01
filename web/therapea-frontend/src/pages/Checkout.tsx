import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import '../styles/Checkout.css';
import '../styles/TherapistProfile.css'; 

const Checkout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { therapist } = location.state || {};
  
  // ─── Wizard Progress State ───
  const [step, setStep] = useState(1);

  // ─── Step 1: Intake State ───
  const [assessmentType, setAssessmentType] = useState('');
  const [notes, setNotes] = useState('');

  // ─── Step 2: Scheduling State ───
  const [selectedDate, setSelectedDate] = useState<Date | null>(null);
  const [selectedTime, setSelectedTime] = useState<string | null>(null);
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [isEditingDate, setIsEditingDate] = useState(false);
  const [tempDate, setTempDate] = useState('');
  const [dateError, setDateError] = useState<string | null>(null);
  
  // FIXED: Renamed to match the variable used in the JSX below
  const allAvailableTimes = ["9:00 AM", "10:30 AM", "1:00 PM", "2:30 PM", "4:00 PM"];

  // ─── Step 3: Review & Payment State ───
  const [sessionFormat, setSessionFormat] = useState<'online' | 'walk-in'>('online');
  const [paymentMethod, setPaymentMethod] = useState<'online-full' | 'online-partial' | 'walk-in'>('online-full');
  const [isProcessing, setIsProcessing] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  // ─── Dynamic Booking States ───
  const [providerBookedSlots, setProviderBookedSlots] = useState<{date: string, time: string}[]>([]);
  const [patientBookings, setPatientBookings] = useState<{date: string, time: string}[]>([]);

  useEffect(() => {
    if (!therapist) navigate('/therapists', { replace: true });
  }, [therapist, navigate]);

  // Fetch Therapist's Existing Appointments to block out times
  useEffect(() => {
    if (!therapist) return;
    const fetchProviderSchedule = async () => {
      try {
        const res = await fetch(`http://localhost:8083/api/appointments/provider/${encodeURIComponent(therapist.name)}`);
        if (res.ok) {
          const data = await res.json();
          if (data.success) {
            const activeApts = data.appointments.filter((a: any) => a.status !== 'Canceled');
            setProviderBookedSlots(activeApts.map((a: any) => ({ date: a.date, time: a.time })));
          }
        }
      } catch (err) {
        console.error("Failed to fetch provider schedule:", err);
      }
    };
    fetchProviderSchedule();
  }, [therapist]);

  // Fetch Patient's Existing Appointments to enforce the 1-per-day rule
  useEffect(() => {
    const storedUser = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (storedUser) {
      const user = JSON.parse(storedUser);
      const fetchPatientSchedule = async () => {
        try {
          const res = await fetch(`http://localhost:8083/api/appointments/user?email=${encodeURIComponent(user.email)}`);
          if (res.ok) {
            const data = await res.json();
            if (data.success) {
              const activeApts = data.appointments.filter((a: any) => a.status === 'Scheduled');
              setPatientBookings(activeApts.map((a: any) => ({ date: a.date, time: a.time })));
            }
          }
        } catch (err) {
          console.error("Failed to fetch patient schedule:", err);
        }
      };
      fetchPatientSchedule();
    }
  }, []);

  if (!therapist) return null;

  const baseRate = therapist.rate || 1500;
  const payToday = paymentMethod === 'online-full' ? baseRate : (paymentMethod === 'online-partial' ? Math.round(baseRate * 0.5) : 0);

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const daysInMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 0).getDate();
  const firstDayIndex = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), 1).getDay();
  const blanks = Array.from({ length: firstDayIndex }, (_, i) => i);
  const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);
  const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
  const dayNames = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

  const validateAndApplyDate = (val: string) => {
    if (!val) { setIsEditingDate(false); return; }
    const [year, month, day] = val.split('-');
    if (year.length > 4 || Number(year) > 9999) { setDateError("Invalid year."); return; }
    
    const newDate = new Date(Number(year), Number(month) - 1, Number(day));
    if (newDate < today) {
      setDateError("Appointments cannot be booked in the past.");
    } else {
      setDateError(null);
      setCurrentMonth(new Date(newDate.getFullYear(), newDate.getMonth(), 1));
      setSelectedDate(newDate);
      setSelectedTime(null);
      setIsEditingDate(false);
    }
  };

 const handleFinalConfirm = async () => {
    setIsProcessing(true);
    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    const user = JSON.parse(stored || '{}');
    const formattedDate = selectedDate?.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });

    try {
      const res = await fetch('http://localhost:8083/api/appointments/book', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: user.email,
          providerName: therapist.name,
          
          // 🔴 ADD THIS EXACT LINE:
          providerEmail: therapist.email, 
          
          date: formattedDate,
          time: selectedTime,
          type: sessionFormat === 'online' ? 'Telehealth' : 'In-Person',
          assessmentType,
          notes,
          amountPaid: payToday
        })
      });

      if (res.ok) {
        setShowSuccess(true);
        setTimeout(() => navigate('/dashboard'), 2500);
      }
    } catch (err) {
      console.error("Booking error:", err);
    } finally {
      setIsProcessing(false);
    }
  };

  // ─── Conflict Checks ───
  const formattedSelectedDate = selectedDate?.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
  
  // 1. Check if patient already has an appointment on this date (Limit 1 per day)
  const patientHasBookingOnDate = patientBookings.some(b => b.date === formattedSelectedDate);
  
  // 2. Check which times the provider is already booked
  const providerBookedTimesForDate = providerBookedSlots
    .filter(slot => slot.date === formattedSelectedDate)
    .map(slot => slot.time);

  return (
    <SidebarLayout title="Book Appointment">
      <div className="co-container">
        <div className={`co-layout ${showSuccess ? 'blurred' : ''}`}>
          
          <div className="co-header-wizard">
            <button className="co-btn-back-minimal" onClick={() => step > 1 ? setStep(step - 1) : navigate(-1)}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6" /></svg>
              <span>{step === 1 ? 'Exit' : 'Back'}</span>
            </button>
            <div className="co-steps-stepper">
              <div className={`step-item ${step >= 1 ? 'active' : ''} ${step > 1 ? 'done' : ''}`}>1</div>
              <div className="step-line" />
              <div className={`step-item ${step >= 2 ? 'active' : ''} ${step > 2 ? 'done' : ''}`}>2</div>
              <div className="step-line" />
              <div className={`step-item ${step >= 3 ? 'active' : ''}`}>3</div>
            </div>
          </div>

          <h1 className="co-step-title">
            {step === 1 ? 'Initial Intake' : step === 2 ? 'Choose Schedule' : 'Review & Confirm'}
          </h1>

          {step === 1 && (
            <div className="co-card animate-in">
              <div className="co-card-header">
                <h2>What brings you here?</h2>
                <p>Select the focus for your session with {therapist.name}.</p>
              </div>
              <div className="co-options-grid">
                {['General Consultation', 'Anxiety & Stress', 'Relationship Issues', 'Depression', 'Personal Growth'].map(type => (
                  <div key={type} className={`co-option-card ${assessmentType === type ? 'selected' : ''}`} onClick={() => setAssessmentType(type)}>
                    <div className="option-check">{assessmentType === type && '✓'}</div>
                    <span>{type}</span>
                  </div>
                ))}
              </div>
              <div className="co-input-group">
                <label>Additional Notes (Optional)</label>
                <textarea placeholder="Tell us a little bit about what you want to discuss..." value={notes} onChange={(e) => setNotes(e.target.value)} />
              </div>
              <button className="co-btn-primary-large" disabled={!assessmentType} onClick={() => setStep(2)}>Continue to Scheduling</button>
            </div>
          )}

          {step === 2 && (
            <div className="co-card animate-in">
              <div className="co-calendar-wizard-container">
                <div className="co-calendar-wrap">
                  <div className="co-calendar-header">
                    <button className="co-cal-nav" onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1))}>
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6"/></svg>
                    </button>
                    <div className="co-cal-month-wrap">
                      {isEditingDate ? (
                        <input type="date" autoFocus className="co-quick-date-input" onChange={(e) => setTempDate(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && validateAndApplyDate(tempDate)} onBlur={() => validateAndApplyDate(tempDate)} />
                      ) : (
                        <span className="co-cal-month" onClick={() => setIsEditingDate(true)}>{monthNames[currentMonth.getMonth()]} {currentMonth.getFullYear()}</span>
                      )}
                    </div>
                    <button className="co-cal-nav" onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1))}>
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
                    </button>
                  </div>
                  <div className="co-calendar-grid">
                    {dayNames.map(d => <div key={d} className="co-cal-day-name">{d}</div>)}
                    {blanks.map(b => <div key={`b-${b}`} className="co-cal-day empty" />)}
                    {days.map(d => {
                      const date = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), d);
                      const isPast = date < today;
                      const isSelected = selectedDate?.toDateString() === date.toDateString();
                      
                      const formattedDateLoop = date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
                      const providerBookedSlotsThisDay = providerBookedSlots.filter(s => s.date === formattedDateLoop).length;
                      const isFullyBooked = providerBookedSlotsThisDay >= allAvailableTimes.length && !isPast;
                      const patientHasApptThisDay = patientBookings.some(b => b.date === formattedDateLoop);

                      return (
                        <button 
                          key={d} 
                          className={`co-cal-day ${isPast ? 'disabled' : ''} ${isSelected ? 'selected' : ''} ${(isFullyBooked || patientHasApptThisDay) && !isPast ? 'fully-booked' : ''}`} 
                          onClick={() => !isPast && !isFullyBooked && !patientHasApptThisDay && (setSelectedDate(date), setSelectedTime(null))}
                          disabled={isPast || isFullyBooked || patientHasApptThisDay}
                          title={patientHasApptThisDay ? "You already have a session this day" : isFullyBooked ? "Fully Booked" : ""}
                        >
                          {d}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Patient Rule Validation Message */}
                {selectedDate && patientHasBookingOnDate && (
                  <div className="co-alert-box warning">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                    You already have a session scheduled on this date. To ensure adequate processing time, patients are limited to one session per day. Please select a different date.
                  </div>
                )}

                <div className={`co-time-picker ${selectedDate && !patientHasBookingOnDate ? 'visible' : ''}`}>
                   <h3>Available Slots for {selectedDate?.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</h3>
                   <div className="co-time-grid">
                     {allAvailableTimes.map(t => {
                       const isBookedByProvider = providerBookedTimesForDate.includes(t);
                       return (
                         <button 
                          key={t} 
                          className={`co-time-btn ${selectedTime === t ? 'selected' : ''} ${isBookedByProvider ? 'booked' : ''}`} 
                          onClick={() => !isBookedByProvider && setSelectedTime(t)}
                          disabled={isBookedByProvider}
                         >
                          {isBookedByProvider ? 'Booked' : t}
                         </button>
                       )
                     })}
                   </div>
                </div>
              </div>
              
              {dateError && <p className="co-error-text">{dateError}</p>}
              <button className="co-btn-primary-large" disabled={!selectedDate || !selectedTime || patientHasBookingOnDate} onClick={() => setStep(3)} style={{ marginTop: '40px' }}>Proceed to Review</button>
            </div>
          )}

          {step === 3 && (
            <div className="co-card animate-in">
              <div className="co-review-summary">
                <div className="summary-item"><label>Provider</label><p>{therapist.name}</p></div>
                <div className="summary-item"><label>Focus Area</label><p>{assessmentType}</p></div>
                <div className="summary-item"><label>Schedule</label><p>{selectedDate?.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })} at {selectedTime}</p></div>
              </div>

              <div className="co-input-group">
                <label>Session Format</label>
                <div className="co-format-toggle">
                  <div className={`format-option ${sessionFormat === 'online' ? 'active' : ''}`} onClick={() => setSessionFormat('online')}>Online</div>
                  <div className={`format-option ${sessionFormat === 'walk-in' ? 'active' : ''}`} onClick={() => {setSessionFormat('walk-in'); setPaymentMethod('walk-in');}}>Walk-In (Clinic)</div>
                </div>
              </div>

              <div className="co-input-group" style={{ marginTop: '32px' }}>
                <label>Payment Method</label>
                <div className="co-payment-selections">
                  {sessionFormat === 'walk-in' && (
                    <label className={`co-payment-radio ${paymentMethod === 'walk-in' ? 'active' : ''}`}>
                      <input type="radio" checked={paymentMethod === 'walk-in'} onChange={() => setPaymentMethod('walk-in')} />
                      <div className="co-payment-label-text"><span className="co-payment-name">Pay at Clinic</span><span className="co-payment-hint">Pay via cash or card when you arrive</span></div>
                    </label>
                  )}
                  <label className={`co-payment-radio ${paymentMethod === 'online-full' ? 'active' : ''}`}>
                    <input type="radio" checked={paymentMethod === 'online-full'} onChange={() => setPaymentMethod('online-full')} />
                    <div className="co-payment-label-text"><span className="co-payment-name">Pay in Full (Online)</span><span className="co-payment-hint">Securely pay the full amount now</span></div>
                  </label>
                  <label className={`co-payment-radio ${paymentMethod === 'online-partial' ? 'active' : ''}`}>
                    <input type="radio" checked={paymentMethod === 'online-partial'} onChange={() => setPaymentMethod('online-partial')} />
                    <div className="co-payment-label-text"><span className="co-payment-name">Pay Downpayment (Online)</span><span className="co-payment-hint">Pay 50% now to reserve, pay the rest later</span></div>
                  </label>
                </div>
              </div>

              <div className="co-price-card">
                <div className="price-row"><span>Counseling Fee</span><span>₱{baseRate.toLocaleString()}</span></div>
                {paymentMethod === 'online-partial' && <div className="price-row" style={{ color: 'var(--primary)', fontWeight: 600 }}><span>To be paid later</span><span>- ₱{(baseRate - payToday).toLocaleString()}</span></div>}
                <div className="price-row total"><span>Due Today</span><span>₱{payToday.toLocaleString()}</span></div>
              </div>

              <button className="co-btn-primary-large" onClick={handleFinalConfirm} disabled={isProcessing}>{isProcessing ? 'Confirming...' : 'Book Appointment'}</button>
            </div>
          )}
        </div>
      </div>

      {showSuccess && (
        <div className="co-modal-overlay">
          <div className="co-modal-card success-card scale-in">
            <div className="success-check-circle">✓</div>
            <h2>Booking Confirmed</h2>
            <p>Your session has been successfully scheduled.</p>
            <p className="redirect-text">Redirecting to your dashboard...</p>
          </div>
        </div>
      )}
    </SidebarLayout>
  );
};

export default Checkout;