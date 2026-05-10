import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { SidebarLayout } from '../../core/components/SidebarLayout';
import './Checkout.css';

/* ─── TYPES & INTERFACES ─── */
interface Therapist {
    id: string | number;
    name: string;
    email: string;
    rate: number;
    specialization?: string;
    availableSchedule?: string; 
    whatToExpect?: string;      
}

interface BookingReceipt {
    therapistName: string;
    date: string;
    time: string;
    type: string;
    amountPaid: number;
    balance: number;
}

// Extract active days for calendar blocking
const parseAvailableDays = (scheduleStr?: string): Set<number> => {
    if (!scheduleStr || scheduleStr === "Not currently available") return new Set([1, 2, 3, 4, 5]); 
    const mapping: Record<string, number> = { "Sunday": 0, "Monday": 1, "Tuesday": 2, "Wednesday": 3, "Thursday": 4, "Friday": 5, "Saturday": 6 };
    const activeDays = new Set<number>();
    scheduleStr.split(',').forEach(part => {
        const day = part.split(':')[0].trim();
        if (mapping[day] !== undefined) activeDays.add(mapping[day]);
    });
    return activeDays.size > 0 ? activeDays : new Set([1, 2, 3, 4, 5]);
};

// 🟢 NEW: Directly pulls the exact starting times the doctor defined as the available slots
const generateDynamicSlots = (date: Date | null, scheduleStr?: string): string[] => {
    if (!date || !scheduleStr || scheduleStr === "Not currently available") return [];
    
    const days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    const dayName = days[date.getDay()];

    const dayEntries = scheduleStr.split(', ');
    const targetDayEntry = dayEntries.find(entry => entry.startsWith(`${dayName}:`));
    
    if (!targetDayEntry) return [];

    const timeBlocksPart = targetDayEntry.split(': ')[1];
    if (!timeBlocksPart) return [];

    const slots: string[] = [];
    const blocks = timeBlocksPart.includes('|') ? timeBlocksPart.split(' | ') : timeBlocksPart.split(' and ');

    blocks.forEach(block => {
        const [startStr, endStr] = block.split(' - ');
        if (startStr && endStr) {
            // Simply map the exact starting time of the block as the bookable slot
            slots.push(startStr.trim());
        }
    });
    
    return [...new Set(slots)]; // Remove any accidental duplicates
};

const Checkout: React.FC = () => {
    const navigate = useNavigate();
    const location = useLocation();
    
    const { therapist } = (location.state as { therapist: Therapist }) || {};
    const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8083';

    /* ─── STATE MANAGEMENT ─── */
    const [step, setStep] = useState<1 | 2 | 3 | 4>(1);
    const [globalError, setGlobalError] = useState<string | null>(null);

    const [assessmentType, setAssessmentType] = useState<string>('');
    const [notes, setNotes] = useState<string>('');

    const [selectedDate, setSelectedDate] = useState<Date | null>(null);
    const [selectedTime, setSelectedTime] = useState<string | null>(null);
    const [currentMonth, setCurrentMonth] = useState(new Date());

    const [sessionFormat, setSessionFormat] = useState<'online' | 'face-to-face'>('online');
    const [paymentMethod, setPaymentMethod] = useState<'online-full' | 'online-partial'>('online-full');
    const [showConfirmModal, setShowConfirmModal] = useState(false);

    const [confirmationNumber, setConfirmationNumber] = useState('');
    const [receiptData, setReceiptData] = useState<BookingReceipt | null>(null);
    
    const [isVerifyingReturn, setIsVerifyingReturn] = useState(() => {
        return new URLSearchParams(window.location.search).get('status') === 'success';
    });

    const [providerBookedSlots, setProviderBookedSlots] = useState<{ date: string, time: string }[]>([]);

    /* ─── MEMOIZED DATA ─── */
    const today = useMemo(() => { const d = new Date(); d.setHours(0,0,0,0); return d; }, []);
    const activeDaysSet = useMemo(() => parseAvailableDays(therapist?.availableSchedule), [therapist]);
    const dynamicTimeSlots = useMemo(() => generateDynamicSlots(selectedDate, therapist?.availableSchedule), [selectedDate, therapist?.availableSchedule]);

    const pricing = useMemo(() => {
        const base = therapist?.rate || 1500;
        const deposit = paymentMethod === 'online-full' ? base : Math.round(base * 0.5);
        return { base, deposit, pending: base - deposit };
    }, [therapist, paymentMethod]);


    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const status = urlParams.get('status');

        if (status === 'success') {
            const cachedBooking = sessionStorage.getItem('pendingTherapyBooking');

            if (cachedBooking) {
                sessionStorage.removeItem('pendingTherapyBooking');
                const parsedData = JSON.parse(cachedBooking);

                const finalizeBookingOnServer = async () => {
                    try {
                        const response = await fetch(`${API_BASE_URL}/api/appointments/book`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(parsedData.dbPayload)
                        });

                        if (response.ok) {
                            const txId = `THP-ACK-${Date.now()}-${Math.floor(Math.random() * 999)}`;
                            setConfirmationNumber(txId);
                            setReceiptData(parsedData.receiptData);
                            window.history.replaceState({}, document.title, window.location.pathname);
                            setStep(4);
                        } else {
                            throw new Error("Failed to save booking to database.");
                        }
                    } catch (err) {
                        console.error("Critical Post-Payment Error:", err);
                        setGlobalError("Payment confirmed but we had trouble updating your dashboard. Please contact support.");
                    } finally {
                        setIsVerifyingReturn(false);
                    }
                };
                finalizeBookingOnServer();
            } else {
                setIsVerifyingReturn(false);
                if (!therapist) navigate('/dashboard');
            }
            return;
        }

        if (!therapist && !status) navigate('/therapists', { replace: true });
    }, [therapist, navigate, API_BASE_URL]);

    /* ─── DATA FETCHING ─── */
    const loadProviderAvailability = useCallback(async () => {
        if (!therapist || step === 4) return;
        try {
            const res = await fetch(`${API_BASE_URL}/api/appointments/provider/${encodeURIComponent(therapist.name)}`);
            if (res.ok) {
                const json = await res.json();
                if (json.success) {
                    const active = json.appointments.filter((a: any) => a.status !== 'Canceled');
                    setProviderBookedSlots(active.map((a: any) => ({ date: a.date, time: a.time })));
                }
            }
        } catch (e) { console.error("Availability sync failed", e); }
    }, [therapist, step, API_BASE_URL]);

    useEffect(() => { loadProviderAvailability(); }, [loadProviderAvailability]);

    /* ─── CALENDAR LOGIC ─── */
    const calendar = useMemo(() => {
        const year = currentMonth.getFullYear();
        const month = currentMonth.getMonth();
        const daysCount = new Date(year, month + 1, 0).getDate();
        const firstDayIdx = new Date(year, month, 1).getDay();
        const monthName = new Intl.DateTimeFormat('en-US', { month: 'long' }).format(currentMonth);

        return {
            days: Array.from({ length: daysCount }, (_, i) => i + 1),
            blanks: Array.from({ length: firstDayIdx }, (_, i) => i),
            header: `${monthName} ${year}`
        };
    }, [currentMonth]);

    /* ─── CHECKOUT EXECUTION ─── */
    const handleCheckout = async () => {
        setIsVerifyingReturn(true);
        const storedUser = localStorage.getItem('user') || sessionStorage.getItem('user');
        const user = JSON.parse(storedUser || '{}');
        const fmtDate = selectedDate?.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });

        const sessionStoreData = {
            dbPayload: {
                email: user.email, providerName: therapist.name, providerEmail: therapist.email,
                date: fmtDate, time: selectedTime, type: sessionFormat === 'online' ? 'Telehealth' : 'In-Person',
                assessmentType, notes, amountPaid: pricing.deposit, status: 'Scheduled'
            },
            receiptData: {
                therapistName: therapist.name, date: fmtDate, time: selectedTime,
                type: sessionFormat === 'online' ? 'Video Consultation' : 'Face-to-Face Clinic',
                amountPaid: pricing.deposit, balance: pricing.pending
            }
        };

        sessionStorage.setItem('pendingTherapyBooking', JSON.stringify(sessionStoreData));

        try {
            const response = await fetch(`${API_BASE_URL}/api/payments/create-link`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: pricing.deposit, description: `Therapy Booking: ${therapist.name}`, email: user.email })
            });

            const linkData = await response.json();
            if (linkData.success && linkData.checkoutUrl) window.location.href = linkData.checkoutUrl;
            else { setGlobalError("Payment gateway is temporarily unavailable. Please try again later."); setIsVerifyingReturn(false); }
        } catch (err) {
            setGlobalError("Connection failed. Check your internet.");
            setIsVerifyingReturn(false);
        }
    };

    const styles = {
        card: { backgroundColor: '#fff', padding: '40px', borderRadius: '24px', boxShadow: '0 10px 25px -5px rgba(0,0,0,0.05)', border: '1px solid #f1f5f9' },
        headerLabel: { display: 'block', marginBottom: '10px', fontWeight: 700, fontSize: '12px', color: '#94a3b8', textTransform: 'uppercase' as const, letterSpacing: '1px' },
        primaryBtn: { width: '100%', padding: '20px', backgroundColor: '#0A5C36', color: '#fff', border: 'none', borderRadius: '16px', fontWeight: 800, fontSize: '16px', cursor: 'pointer' },
    };

    const renderHeader = () => (
        <div style={{ textAlign: 'center', marginBottom: '40px' }}>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', marginBottom: '20px' }}>
                {[1, 2, 3].map(i => (
                    <React.Fragment key={i}>
                        <div style={{
                            width: '32px', height: '32px', borderRadius: '50%', backgroundColor: step >= i ? '#0A5C36' : '#f1f5f9',
                            color: step >= i ? '#fff' : '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center',
                            fontWeight: 'bold', fontSize: '14px', border: step === i ? '2px solid #052e16' : 'none'
                        }}>{i}</div>
                        {i < 3 && <div style={{ width: '40px', height: '2px', alignSelf: 'center', backgroundColor: step > i ? '#0A5C36' : '#f1f5f9' }} />}
                    </React.Fragment>
                ))}
            </div>
            <h1 style={{ fontSize: '28px', color: '#1e293b', margin: 0 }}>
                {step === 1 ? 'Booking Intake' : step === 2 ? 'Schedule Session' : 'Review & Pay'}
            </h1>
        </div>
    );

    if (isVerifyingReturn) {
        return (
            <SidebarLayout title="Verifying Transaction">
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '70vh' }}>
                    <div className="custom-loader" />
                    <h2 style={{ marginTop: '25px', color: '#1e293b' }}>Confirming with PayMongo...</h2>
                </div>
                <style>{`.custom-loader { width: 50px; height: 50px; border: 5px solid #f3f3f3; border-top: 5px solid #0A5C36; border-radius: 50%; animation: spin 1s linear infinite; } @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
            </SidebarLayout>
        );
    }

    if (!therapist && step !== 4) return null;

    return (
        <SidebarLayout title={step === 4 ? "Booking Complete" : "Checkout"}>
            <div style={{ maxWidth: '900px', margin: '0 auto', paddingBottom: '100px' }}>
                
                {step < 4 && renderHeader()}
                {globalError && <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fee2e2', color: '#b91c1c', padding: '20px', borderRadius: '15px', marginBottom: '25px', fontWeight: 500 }}>{globalError}</div>}

                {/* STEP 1: INTAKE */}
                {step === 1 && therapist && (
                    <div className="animate-in" style={styles.card}>
                        <h2 style={{ fontSize: '24px', color: '#1e293b', marginBottom: '10px' }}>What brings you here?</h2>
                        <p style={{ color: '#64748b', marginBottom: '30px' }}>Help {therapist.name} understand your needs before the session.</p>
                        
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '15px', marginBottom: '30px' }}>
                            {['Anxiety & Stress', 'Relationship Issues', 'Depression', 'Personal Growth', 'Career Counseling'].map(t => (
                                <button key={t} onClick={() => setAssessmentType(t)} style={{
                                    padding: '20px', borderRadius: '16px', border: assessmentType === t ? '2px solid #0A5C36' : '1px solid #e2e8f0',
                                    backgroundColor: assessmentType === t ? '#f0fdf4' : '#fff', cursor: 'pointer', textAlign: 'left', transition: 'all 0.2s', display: 'flex', alignItems: 'center', gap: '12px'
                                }}>
                                    <div style={{ width: '14px', height: '14px', borderRadius: '50%', border: '2px solid #0A5C36', backgroundColor: assessmentType === t ? '#0A5C36' : 'transparent' }} />
                                    <span style={{ fontWeight: 600, color: assessmentType === t ? '#064e3b' : '#475569' }}>{t}</span>
                                </button>
                            ))}
                        </div>

                        <label style={styles.headerLabel}>Message for the therapist (Optional)</label>
                        <textarea value={notes} onChange={e => setNotes(e.target.value)} placeholder="Briefly describe your goals for this session..." style={{ width: '100%', minHeight: '120px', padding: '20px', borderRadius: '16px', border: '1px solid #e2e8f0', marginBottom: '30px', fontSize: '15px', outline: 'none' }} />

                        <button disabled={!assessmentType} onClick={() => setStep(2)} style={{ ...styles.primaryBtn, opacity: !assessmentType ? 0.5 : 1 }}>Continue to Scheduling</button>
                    </div>
                )}

                {/* STEP 2: CALENDAR & DYNAMIC SCHEDULE */}
                {step === 2 && (
                    <div className="animate-in" style={styles.card}>
                        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '50px' }}>
                            <div>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px' }}>
                                    <h3 style={{ margin: 0, fontSize: '20px' }}>{calendar.header}</h3>
                                    <div style={{ display: 'flex', gap: '10px' }}>
                                        <button onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1))} className="cal-nav-btn">‹</button>
                                        <button onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1))} className="cal-nav-btn">›</button>
                                    </div>
                                </div>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', textAlign: 'center', gap: '8px' }}>
                                    {['Su','Mo','Tu','We','Th','Fr','Sa'].map(d => <div key={d} style={{ color: '#94a3b8', fontSize: '12px', fontWeight: 800 }}>{d}</div>)}
                                    {calendar.blanks.map(b => <div key={`b-${b}`} />)}
                                    {calendar.days.map(d => {
                                        const dObj = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), d);
                                        const dayOfWeekIndex = dObj.getDay();
                                        const isPast = dObj < today;
                                        
                                        const isDoctorUnavailable = !activeDaysSet.has(dayOfWeekIndex);
                                        const isDisabled = isPast || isDoctorUnavailable;
                                        const isSelected = selectedDate?.toDateString() === dObj.toDateString();
                                        
                                        return (
                                            <button key={d} disabled={isDisabled} onClick={() => { setSelectedDate(dObj); setSelectedTime(null); }} style={{
                                                padding: '12px 0', border: 'none', borderRadius: '12px', cursor: isDisabled ? 'not-allowed' : 'pointer',
                                                backgroundColor: isSelected ? '#0A5C36' : 'transparent',
                                                color: isSelected ? '#fff' : (isDisabled ? '#e2e8f0' : '#1e293b'),
                                                fontWeight: isSelected ? 800 : 500,
                                                textDecoration: isDoctorUnavailable && !isPast ? 'line-through' : 'none'
                                            }}>{d}</button>
                                        );
                                    })}
                                </div>
                            </div>

                            <div style={{ borderLeft: '1px solid #f1f5f9', paddingLeft: '50px' }}>
                                
                                {/* UI UPDATE: Card is removed, Select Time Slot takes prominence */}
                                <h3 style={{ margin: '0 0 20px 0', fontSize: '20px', color: '#1e293b', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                        <circle cx="12" cy="12" r="10"></circle>
                                        <polyline points="12 6 12 12 16 14"></polyline>
                                    </svg>
                                    Select Time Slot
                                </h3>

                                {!selectedDate ? (
                                    <div style={{ padding: '30px', textAlign: 'center', background: '#f8fafc', borderRadius: '16px', border: '1px dashed #cbd5e1' }}>
                                        <p style={{ color: '#64748b', fontStyle: 'italic', margin: 0 }}>Please select a date from the calendar first.</p>
                                    </div>
                                ) : dynamicTimeSlots.length === 0 ? (
                                    <div style={{ padding: '30px', textAlign: 'center', background: '#FEF2F2', borderRadius: '16px', border: '1px dashed #FECACA' }}>
                                        <p style={{ color: '#991B1B', fontWeight: 500, margin: 0 }}>No slots available on this date.</p>
                                    </div>
                                ) : (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '12px', maxHeight: '350px', overflowY: 'auto', paddingRight: '5px' }}>
                                        {dynamicTimeSlots.map(t => {
                                            const fmt = selectedDate.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
                                            const isTaken = providerBookedSlots.some(s => s.date === fmt && s.time === t);
                                            return (
                                                <button key={t} disabled={isTaken} onClick={() => setSelectedTime(t)} style={{
                                                    padding: '16px 12px', borderRadius: '12px', border: selectedTime === t ? '2px solid #0A5C36' : '1px solid #e2e8f0',
                                                    backgroundColor: selectedTime === t ? '#f0fdf4' : (isTaken ? '#f8fafc' : '#fff'),
                                                    color: isTaken ? '#94a3b8' : (selectedTime === t ? '#064e3b' : '#1e293b'), fontWeight: 700, cursor: isTaken ? 'not-allowed' : 'pointer',
                                                    transition: 'all 0.2s', boxShadow: selectedTime === t ? '0 4px 12px rgba(10, 92, 54, 0.1)' : 'none',
                                                    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px'
                                                }}>
                                                    <span>{t}</span>
                                                    {isTaken && <span style={{fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.5px', fontWeight: 700, color: '#94a3b8'}}>Booked</span>}
                                                </button>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        </div>
                        <div style={{ marginTop: '50px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <button onClick={() => setStep(1)} style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontWeight: 700, fontSize: '15px' }}>← Back</button>
                            <button disabled={!selectedDate || !selectedTime} onClick={() => setStep(3)} style={{ ...styles.primaryBtn, width: '250px', opacity: (!selectedDate || !selectedTime) ? 0.5 : 1 }}>Review Summary</button>
                        </div>
                    </div>
                )}

                {/* STEP 3: REVIEW & PAY */}
                {step === 3 && (
                    <div className="animate-in" style={styles.card}>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '40px' }}>
                            <div style={{ padding: '20px', backgroundColor: '#f8fafc', borderRadius: '16px' }}>
                                <label style={styles.headerLabel}>Therapist</label>
                                <p style={{ fontSize: '18px', fontWeight: 800, margin: 0 }}>{therapist?.name}</p>
                            </div>
                            <div style={{ padding: '20px', backgroundColor: '#f8fafc', borderRadius: '16px' }}>
                                <label style={styles.headerLabel}>Time</label>
                                <p style={{ fontSize: '18px', fontWeight: 800, margin: 0 }}>{selectedDate?.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} @ {selectedTime}</p>
                            </div>
                        </div>

                        <div style={{ marginBottom: '40px' }}>
                            <label style={{ fontWeight: 800, fontSize: '15px', marginBottom: '15px', display: 'block' }}>Select Payment Plan</label>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                                <div onClick={() => setPaymentMethod('online-full')} style={{ 
                                    padding: '25px', borderRadius: '20px', cursor: 'pointer', border: paymentMethod === 'online-full' ? '2.5px solid #0A5C36' : '1px solid #e2e8f0',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: paymentMethod === 'online-full' ? '#f0fdf4' : '#fff'
                                }}>
                                    <div><p style={{ fontWeight: 800, margin: 0 }}>Full Session Fee</p><p style={{ fontSize: '13px', color: '#64748b', margin: 0 }}>Pay total amount now for a smooth experience</p></div>
                                    <span style={{ fontWeight: 900, fontSize: '20px' }}>₱{pricing.base.toLocaleString()}</span>
                                </div>
                                <div onClick={() => setPaymentMethod('online-partial')} style={{ 
                                    padding: '25px', borderRadius: '20px', cursor: 'pointer', border: paymentMethod === 'online-partial' ? '2.5px solid #0A5C36' : '1px solid #e2e8f0',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: paymentMethod === 'online-partial' ? '#f0fdf4' : '#fff'
                                }}>
                                    <div><p style={{ fontWeight: 800, margin: 0 }}>50% Downpayment</p><p style={{ fontSize: '13px', color: '#64748b', margin: 0 }}>Pay the other half at the clinic location</p></div>
                                    <span style={{ fontWeight: 900, fontSize: '20px' }}>₱{(pricing.base / 2).toLocaleString()}</span>
                                </div>
                            </div>
                        </div>

                        <div style={{ borderTop: '2px dashed #f1f5f9', paddingTop: '30px', marginBottom: '30px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                             <span style={{ fontWeight: 600, color: '#64748b' }}>To pay</span>
                             <span style={{ fontWeight: 900, fontSize: '32px', color: '#0A5C36' }}>₱{pricing.deposit.toLocaleString()}</span>
                        </div>

                        <button onClick={() => setShowConfirmModal(true)} style={styles.primaryBtn}>
                            Confirm & Go to Payment
                        </button>
                    </div>
                )}

                {/* STEP 4: SUCCESS RECEIPT */}
                {step === 4 && receiptData && (
                    <div className="animate-in printable-area" style={{ 
                        backgroundColor: '#fff', padding: '60px', borderRadius: '40px', 
                        boxShadow: '0 25px 50px -12px rgba(0,0,0,0.15)', maxWidth: '650px', margin: '0 auto', textAlign: 'center'
                    }}>
                        <div style={{ marginBottom: '40px' }}>
                            <div style={{ width: '80px', height: '80px', backgroundColor: '#f0fdf4', borderRadius: '50%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: '20px' }}>
                                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="3"><polyline points="20 6 9 17 4 12" /></svg>
                            </div>
                            <h1 style={{ fontSize: '32px', color: '#1e293b', marginBottom: '8px' }}>Booking Confirmed!</h1>
                            <p style={{ color: '#64748b' }}>We've sent a detailed confirmation to your email.</p>
                        </div>
                        <div className="receipt-border">
                            <div style={{ padding: '20px', background: '#f8fafc', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between' }}>
                                <span style={{ fontWeight: 800, color: '#475569', fontSize: '12px', textTransform: 'uppercase' }}>Official E-Receipt</span>
                                <span style={{ color: '#94a3b8', fontSize: '12px' }}>{new Date().toLocaleDateString()}</span>
                            </div>
                            <div style={{ padding: '30px', textAlign: 'left' }}>
                                <div className="receipt-row"><span>Reference ID</span><span style={{ fontWeight: 700 }}>{confirmationNumber}</span></div>
                                <div className="receipt-row"><span>Therapist</span><span style={{ fontWeight: 700 }}>{receiptData.therapistName}</span></div>
                                <div className="receipt-row"><span>Date/Time</span><span>{receiptData.date} @ {receiptData.time}</span></div>
                                <div className="receipt-row"><span>Service</span><span>{receiptData.type}</span></div>
                                <hr style={{ border: 'none', borderTop: '1px dashed #e2e8f0', margin: '20px 0' }} />
                                <div className="receipt-row" style={{ fontSize: '20px' }}>
                                    <span style={{ fontWeight: 800 }}>Paid Amount</span>
                                    <span style={{ fontWeight: 800, color: '#0A5C36' }}>₱{receiptData.amountPaid.toLocaleString()}</span>
                                </div>
                                {receiptData.balance > 0 && <p style={{ color: '#b91c1c', fontSize: '13px', marginTop: '10px', textAlign: 'right' }}>Remaining Balance: ₱{receiptData.balance.toLocaleString()}</p>}
                            </div>
                        </div>

                        <div className="no-print" style={{ marginTop: '40px', display: 'flex', flexDirection: 'column', gap: '15px' }}>
                            <button onClick={() => window.print()} className="receipt-download-btn">Download Soft Copy (PDF)</button>
                            <button onClick={() => navigate('/dashboard')} className="receipt-home-btn">Return to My Dashboard</button>
                        </div>
                    </div>
                )}
            </div>

            {showConfirmModal && (
                <div className="modal-overlay">
                    <div className="modal-content scale-in">
                        <h2 style={{ fontSize: '24px', marginBottom: '15px' }}>Final Confirmation</h2>
                        <p style={{ color: '#64748b', lineHeight: 1.6, marginBottom: '30px' }}>
                            You are about to be redirected to our secure payment processor.
                            Once the transaction is complete, <b>PLEASE WAIT to be redirected back here to receive your receipt.</b>
                        </p>
                        <div style={{ display: 'flex', gap: '15px' }}>
                            <button onClick={() => setShowConfirmModal(false)} className="modal-cancel">Cancel</button>
                            <button onClick={handleCheckout} className="modal-proceed">Proceed to Pay ₱{pricing.deposit.toLocaleString()}</button>
                        </div>
                    </div>
                </div>
            )}
            <style>{`
                .cal-nav-btn { padding: 10px 15px; border: 1px solid #e2e8f0; background: #fff; borderRadius: 10px; cursor: pointer; font-size: 18px; }
                .cal-nav-btn:hover { background: #f8fafc; }
                .receipt-border { border: 2px solid #f1f5f9; borderRadius: 20px; overflow: hidden; margin-top: 30px; }
                .receipt-row { display: flex; justify-content: space-between; margin-bottom: 12px; color: #475569; }
                .receipt-download-btn { padding: 18px; background: #1e293b; color: #fff; border: none; borderRadius: 16px; font-weight: 700; cursor: pointer; }
                .receipt-home-btn { background: none; border: none; color: #64748b; font-weight: 600; cursor: pointer; text-decoration: underline; }
                
                .modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); display: flex; align-items: center; justifyContent: center; z-index: 9999; }
                .modal-content { background: #fff; width: 90%; maxWidth: 450px; padding: 40px; borderRadius: 28px; text-align: center; }
                .modal-cancel { flex: 1; padding: 15px; border: 1px solid #e2e8f0; background: #fff; borderRadius: 12px; font-weight: 700; cursor: pointer; }
                .modal-proceed { flex: 1; padding: 15px; border: none; background: #0A5C36; color: #fff; borderRadius: 12px; font-weight: 700; cursor: pointer; }

                @media print {
                    .no-print, aside, header { display: none !important; }
                    .printable-area { box-shadow: none !important; margin: 0 !important; width: 100% !important; padding: 0 !important; }
                    body { background: #fff !important; }
                }
                .animate-in { animation: slideUp 0.4s ease-out; }
                @keyframes slideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
                .scale-in { animation: scaleIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); }
                @keyframes scaleIn { from { transform: scale(0.9); opacity: 0; } to { transform: scale(1); opacity: 1; } }
            `}</style>
        </SidebarLayout>
    );
};

export default Checkout;