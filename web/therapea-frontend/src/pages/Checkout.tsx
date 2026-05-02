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
  
  const allAvailableTimes = ["9:00 AM", "10:30 AM", "1:00 PM", "2:30 PM", "4:00 PM"];

  // ─── Step 3: Review & Payment State ───
  const [sessionFormat, setSessionFormat] = useState<'online' | 'face-to-face'>('online');
  const [paymentMethod, setPaymentMethod] = useState<'online-full' | 'online-partial'>('online-full');
  
  // ─── Modal & Confirmation State ───
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [confirmationNumber, setConfirmationNumber] = useState('');
  const [receiptData, setReceiptData] = useState<any>(null); // Holds data when returning from PayMongo

  // ─── Dynamic Booking States ───
  const [providerBookedSlots, setProviderBookedSlots] = useState<{date: string, time: string}[]>([]);
  const [patientBookings, setPatientBookings] = useState<{date: string, time: string}[]>([]);

  // 🔴 MAGIC RETURN HOOK: Detects when the user returns from PayMongo
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('status') === 'success') {
      const pendingStr = sessionStorage.getItem('pendingTherapyBooking');
      if (pendingStr) {
        setIsProcessing(true);
        const pendingBooking = JSON.parse(pendingStr);
        
        // 1. NOW we save it to the database because payment is guaranteed!
        fetch('http://localhost:8083/api/appointments/book', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(pendingBooking.dbPayload)
        }).then(res => {
          if (res.ok) {
            const newConfNumber = `APT - ${new Date().getFullYear()} - ${Math.floor(100000 + Math.random() * 900000)}`;
            setConfirmationNumber(newConfNumber);
            setReceiptData(pendingBooking.receiptData);
            sessionStorage.removeItem('pendingTherapyBooking'); // Clear it out
            setStep(4); // Jump straight to the receipt screen
          }
        }).catch(err => console.error("Error saving booking:", err))
          .finally(() => setIsProcessing(false));
      }
      return; 
    }

    // Normal page load check
    if (!therapist && !urlParams.get('status')) navigate('/therapists', { replace: true });
  }, [therapist, navigate]);

  // Fetch Schedules (Skipped if we are just showing the receipt)
  useEffect(() => {
    if (!therapist || step === 4) return;
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
      } catch (err) { console.error(err); }
    };
    fetchProviderSchedule();
  }, [therapist, step]);

  useEffect(() => {
    if (step === 4) return;
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
        } catch (err) { console.error(err); }
      };
      fetchPatientSchedule();
    }
  }, [step]);

  // If therapist is missing and we aren't loading a receipt, render nothing while redirecting
  if (!therapist && !receiptData) return null;

  const baseRate = therapist?.rate || 1500;
  const payToday = paymentMethod === 'online-full' ? baseRate : (paymentMethod === 'online-partial' ? Math.round(baseRate * 0.5) : 0);
  const remainingBalance = baseRate - payToday;

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

  const handleProceedFromReview = () => {
    setShowConfirmModal(true); 
  };

  const executeCheckout = async () => {
    setIsProcessing(true);
    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    const user = JSON.parse(stored || '{}');
    const formattedDate = selectedDate?.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });

    try {
      // 1. Pack the data to be saved to the database LATER
      const pendingData = {
        dbPayload: {
          email: user.email,
          providerName: therapist.name,
          providerEmail: therapist.email, 
          date: formattedDate,
          time: selectedTime,
          type: sessionFormat === 'online' ? 'Telehealth' : 'In-Person',
          assessmentType,
          notes,
          amountPaid: payToday,
          status: 'Scheduled' // Automatically scheduled because it will only save if payment succeeds
        },
        receiptData: {
          therapistName: therapist.name,
          date: formattedDate,
          time: selectedTime,
          type: sessionFormat === 'online' ? 'Video Consultation' : 'Face-to-Face Clinic',
          amountPaid: payToday,
          balance: remainingBalance
        }
      };

      // 2. Hide it in SessionStorage
      sessionStorage.setItem('pendingTherapyBooking', JSON.stringify(pendingData));

      // 3. Ask Spring Boot for the PayMongo Link
      const payRes = await fetch('http://localhost:8083/api/payments/create-link', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: payToday,
          description: `Therapy Session: ${therapist.name}`,
          email: user.email
        })
      });

      const payData = await payRes.json();
      
      if (payData.success && payData.checkoutUrl) {
        // REDIRECT TO PAYMONGO
        window.location.href = payData.checkoutUrl;
      } else {
        console.error("PayMongo Error:", payData.message);
        setIsProcessing(false);
      }
    } catch (err) {
      console.error("Checkout error:", err);
      setIsProcessing(false);
    } 
  };

  const formattedSelectedDate = selectedDate?.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
  const patientHasBookingOnDate = patientBookings.some(b => b.date === formattedSelectedDate);
  const providerBookedTimesForDate = providerBookedSlots
    .filter(slot => slot.date === formattedSelectedDate)
    .map(slot => slot.time);

  // If processing the return from PayMongo, show a loading screen
  if (isProcessing && step !== 4 && !showConfirmModal) {
    return (
      <SidebarLayout title="Confirming Payment...">
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
          <h2>Verifying your secure payment...</h2>
        </div>
      </SidebarLayout>
    );
  }

  return (
    <SidebarLayout title="Book Appointment">
      <div className="co-container">
        <div className={`co-layout ${showConfirmModal ? 'blurred' : ''}`}>
          
          {step < 4 && (
            <div className="co-header-wizard">
              <button className="co-btn-back-minimal" onClick={() => step > 1 ? setStep(step - 1) : navigate(-1)}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6" /></svg>
                <span>{step === 1 ? 'Exit' : 'Back'}</span>
              </button>
              <div className="co-steps-stepper" style={{ justifyContent: 'center', width: '100%', gap: '10px' }}>
                <div className={`step-item ${step >= 1 ? 'active' : ''} ${step > 1 ? 'done' : ''}`}>1</div>
                <div className="step-line" />
                <div className={`step-item ${step >= 2 ? 'active' : ''} ${step > 2 ? 'done' : ''}`}>2</div>
                <div className="step-line" />
                <div className={`step-item ${step >= 3 ? 'active' : ''}`}>3</div>
              </div>
            </div>
          )}

          {step < 4 && (
            <h1 className="co-step-title" style={{ textAlign: 'center' }}>
              {step === 1 ? 'Initial Intake' : step === 2 ? 'Choose Schedule' : 'Review & Confirm'}
            </h1>
          )}

          {/* STEP 1: INTAKE */}
          {step === 1 && therapist && (
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

          {/* STEP 2: SCHEDULING */}
          {step === 2 && therapist && (
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
                        >
                          {d}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {selectedDate && patientHasBookingOnDate && (
                  <div className="co-alert-box warning">
                    You already have a session scheduled on this date.
                  </div>
                )}

                <div className={`co-time-picker ${selectedDate && !patientHasBookingOnDate ? 'visible' : ''}`}>
                   <h3>Available Slots</h3>
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

          {/* STEP 3: REVIEW DETAILS */}
          {step === 3 && therapist && (
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
                  <div className={`format-option ${sessionFormat === 'face-to-face' ? 'active' : ''}`} onClick={() => setSessionFormat('face-to-face')}>Face-to-Face (Clinic)</div>
                </div>
              </div>

              <div className="co-input-group" style={{ marginTop: '32px' }}>
                <label>Secure Payment Method</label>
                <div className="co-payment-selections">
                  <label className={`co-payment-radio ${paymentMethod === 'online-full' ? 'active' : ''}`}>
                    <input type="radio" checked={paymentMethod === 'online-full'} onChange={() => setPaymentMethod('online-full')} />
                    <div className="co-payment-label-text">
                      <span className="co-payment-name">Pay in Full (PayMongo)</span>
                      <span className="co-payment-hint">Securely pay the full amount now via GCash, Maya, or Card</span>
                    </div>
                  </label>
                  <label className={`co-payment-radio ${paymentMethod === 'online-partial' ? 'active' : ''}`}>
                    <input type="radio" checked={paymentMethod === 'online-partial'} onChange={() => setPaymentMethod('online-partial')} />
                    <div className="co-payment-label-text">
                      <span className="co-payment-name">50% Downpayment (PayMongo)</span>
                      <span className="co-payment-hint">Secure your slot now, pay the remaining balance later</span>
                    </div>
                  </label>
                </div>
              </div>

              {/* 🔴 Anti-Scam Policy Display */}
              {paymentMethod === 'online-partial' && (
                <div className="co-alert-box info" style={{ marginTop: '16px', backgroundColor: '#eff6ff', border: '1px solid #bfdbfe', color: '#1e40af' }}>
                  <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ flexShrink: 0, marginTop: '2px' }}><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                    <div>
                      <strong>Payment Policy:</strong><br/>
                      {sessionFormat === 'online' 
                        ? 'To protect our providers, the meeting link for your online session will remain locked. The remaining balance must be settled through your dashboard prior to the session.' 
                        : 'The remaining balance must be settled at the clinic reception before your face-to-face session begins.'}
                    </div>
                  </div>
                </div>
              )}

              <div className="co-price-card" style={{ marginTop: '24px' }}>
                <div className="price-row"><span>Counseling Fee</span><span>₱{baseRate.toLocaleString()}</span></div>
                {paymentMethod === 'online-partial' && <div className="price-row" style={{ color: 'var(--primary)', fontWeight: 600 }}><span>To be paid later</span><span>- ₱{(baseRate - payToday).toLocaleString()}</span></div>}
                <div className="price-row total"><span>Due Today</span><span>₱{payToday.toLocaleString()}</span></div>
              </div>

              <button className="co-btn-primary-large" onClick={handleProceedFromReview}>
                Proceed to Checkout
              </button>
            </div>
          )}

          {/* STEP 4: SUCCESS RECEIPT */}
          {step === 4 && receiptData && (
            <div className="co-card animate-in printable-receipt" style={{ padding: '40px' }}>
              <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                <h2 style={{ fontSize: '24px', marginBottom: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px' }}>
                  Appointment Confirmed <span style={{ color: '#0A5C36' }}>✓</span>
                </h2>
                <p style={{ color: '#666', fontSize: '14px', margin: 0 }}>Your therapy session has been officially booked</p>
              </div>

              <div style={{ backgroundColor: '#f5f5f5', borderRadius: '12px', padding: '24px', marginBottom: '24px' }}>
                <div style={{ borderBottom: '1px solid #e0e0e0', paddingBottom: '16px', marginBottom: '16px' }}>
                  <p style={{ fontSize: '12px', color: '#666', margin: '0 0 4px 0' }}>Therapist</p>
                  <p style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>{receiptData.therapistName}</p>
                </div>
                <div style={{ borderBottom: '1px solid #e0e0e0', paddingBottom: '16px', marginBottom: '16px' }}>
                  <p style={{ fontSize: '12px', color: '#666', margin: '0 0 4px 0' }}>Date & Time</p>
                  <p style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>
                    {receiptData.date} at {receiptData.time}
                  </p>
                </div>
                <div style={{ borderBottom: '1px solid #e0e0e0', paddingBottom: '16px', marginBottom: '16px' }}>
                  <p style={{ fontSize: '12px', color: '#666', margin: '0 0 4px 0' }}>Session Type</p>
                  <p style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>{receiptData.type}</p>
                </div>
                <div>
                  <p style={{ fontSize: '12px', color: '#666', margin: '0 0 4px 0' }}>Amount Paid (via PayMongo)</p>
                  <p style={{ fontSize: '16px', fontWeight: 600, margin: 0, color: '#0A5C36' }}>₱{receiptData.amountPaid.toLocaleString()}</p>
                </div>
                {receiptData.balance > 0 && (
                  <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid #e0e0e0' }}>
                    <p style={{ fontSize: '12px', color: '#b91c1c', margin: '0 0 4px 0', fontWeight: 600 }}>Remaining Balance Due</p>
                    <p style={{ fontSize: '16px', fontWeight: 600, margin: 0, color: '#b91c1c' }}>₱{receiptData.balance.toLocaleString()}</p>
                  </div>
                )}
              </div>

              <div style={{ backgroundColor: '#f5f5f5', borderRadius: '12px', padding: '24px', textAlign: 'center', marginBottom: '32px' }}>
                <p style={{ fontSize: '14px', color: '#666', margin: '0 0 8px 0' }}>Confirmation Number</p>
                <h3 style={{ fontSize: '24px', fontWeight: 700, margin: 0 }}>{confirmationNumber}</h3>
              </div>

              <div className="no-print" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <button 
                  style={{ width: '100%', padding: '16px', backgroundColor: '#333', color: 'white', border: 'none', borderRadius: '8px', fontSize: '16px', fontWeight: 600, cursor: 'pointer' }}
                  onClick={() => window.print()}
                >
                  Download Confirmation
                </button>
                <button 
                  style={{ width: '100%', padding: '16px', backgroundColor: '#f1f1f1', color: '#333', border: 'none', borderRadius: '8px', fontSize: '16px', fontWeight: 600, cursor: 'pointer' }}
                  onClick={() => navigate('/dashboard')}
                >
                  Return to Dashboard
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* CONFIRMATION MODAL POPUP */}
      {showConfirmModal && therapist && (
        <div className="co-modal-overlay" style={{ zIndex: 1000 }} onClick={() => !isProcessing && setShowConfirmModal(false)}>
          <div className="co-modal-card scale-in" onClick={e => e.stopPropagation()} style={{ padding: '32px', maxWidth: '400px', textAlign: 'center' }}>
            <h2 style={{ fontSize: '22px', marginBottom: '16px', color: '#1e293b' }}>Confirm & Pay</h2>
            <p style={{ color: '#64748b', fontSize: '15px', marginBottom: '24px', lineHeight: '1.6' }}>
              You are securing a session with <strong>{therapist.name}</strong> on <strong>{selectedDate?.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })} at {selectedTime}</strong>.
              <br/><br/>
              You will be safely redirected to PayMongo to complete your payment of ₱{payToday.toLocaleString()}.
            </p>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button 
                style={{ flex: 1, padding: '14px', borderRadius: '8px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc', color: '#64748b', cursor: 'pointer', fontWeight: 600, fontSize: '14px' }} 
                onClick={() => setShowConfirmModal(false)}
                disabled={isProcessing}
              >
                Cancel
              </button>
              <button 
                style={{ flex: 1, padding: '14px', borderRadius: '8px', border: 'none', backgroundColor: '#0A5C36', color: 'white', cursor: 'pointer', fontWeight: 600, fontSize: '14px' }}
                onClick={executeCheckout}
                disabled={isProcessing}
              >
                {isProcessing ? 'Redirecting...' : 'Yes, Proceed'}
              </button>
            </div>
          </div>
        </div>
      )}
    </SidebarLayout>
  );
};

export default Checkout;