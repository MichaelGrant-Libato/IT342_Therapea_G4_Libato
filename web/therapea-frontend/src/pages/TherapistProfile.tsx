import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { SidebarLayout } from '../components/SidebarLayout';
import '../styles/TherapistProfile.css';

const TherapistProfile: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>(); 
  const [therapist, setTherapist] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchTherapist = async () => {
      if (!id) { 
        setIsLoading(false); 
        return; 
      }
      try {
        const res = await fetch('http://localhost:8083/api/doctors/list');
        if (res.ok) {
          const data = await res.json();
          if (data.success && data.doctors) {
            const found = data.doctors.find((t: any) => String(t.id) === String(id));
            setTherapist(found || null);
          }
        }
      } catch (err) {
        console.error("Failed to fetch therapist:", err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchTherapist();
  }, [id]);

  if (isLoading) {
    return (
      <SidebarLayout title="Provider Profile">
        <div className="tp-loader-wrap">
          <div className="db-spinner" />
          <p>Loading profile...</p>
        </div>
      </SidebarLayout>
    );
  }

  if (!therapist) {
    return (
      <SidebarLayout title="Error">
        <div className="tp-error-state">
          <h2>Provider Not Found</h2>
          <p>We couldn't find the provider you were looking for.</p>
          <button className="tp-btn-outline" onClick={() => navigate('/therapists')}>
            Return to Directory
          </button>
        </div>
      </SidebarLayout>
    );
  }

  const cleanName = (therapist.name || "Provider").replace('Dr. ', '').trim();
  const initials = cleanName.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase();
  const specialties = Array.isArray(therapist.specialties) ? therapist.specialties : ['Mental Wellness', 'Behavioral Health'];

  return (
    <SidebarLayout title={`Profile: ${therapist.name}`}>
      <div className="tp-container">
        
        <button className="tp-back-link" onClick={() => navigate('/therapists')}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          Back to Directory
        </button>

        <div className="tp-profile-grid">
          
          {/* ── Main Content Column ── */}
          <div className="tp-main-col">
            
            <header className="tp-hero-section">
              <div className="tp-avatar-lg">{initials}</div>
              <div className="tp-hero-text">
                <div className="tp-verified-badge">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                  </svg>
                  Verified Provider
                </div>
                <h1>{therapist.name}</h1>
                <p className="tp-title-tag">{therapist.title || 'Licensed Clinical Psychologist'}</p>
                <div className="tp-meta-pills">
                  <span>{therapist.experience || '8+ Years Experience'}</span>
                  <span className="dot">·</span>
                  <span>{therapist.online ? 'Telehealth Available' : 'Clinic Only'}</span>
                </div>
              </div>
            </header>

            <section className="tp-bio-section">
              <h2 className="tp-section-h">About the Provider</h2>
              <p className="tp-description">
                {therapist.bio || "I am a dedicated mental health professional committed to providing a compassionate and evidence-based approach to wellness. My goal is to create a safe, non-judgmental space where you feel heard and supported throughout your journey. I specialize in helping individuals navigate challenges related to general mental health, stress management, and personal growth."}
              </p>
              
              <h3 className="tp-sub-h">Areas of Focus</h3>
              <div className="tp-focus-tags">
                {specialties.map((s: string) => (
                  <span key={s} className="tp-focus-pill">{s}</span>
                ))}
              </div>
            </section>

            <section className="tp-expectation-section">
              <h2 className="tp-section-h">What to Expect in Sessions</h2>
              <div className="tp-expect-grid">
                <div className="tp-expect-item">
                  <div className="tp-expect-icon">🤝</div>
                  <div className="tp-expect-text">
                    <strong>A Safe, Non-Judgmental Space</strong>
                    <p>A welcoming environment to explore your inner thoughts and feelings openly.</p>
                  </div>
                </div>
                <div className="tp-expect-item">
                  <div className="tp-expect-icon">🧠</div>
                  <div className="tp-expect-text">
                    <strong>Evidence-Based Techniques</strong>
                    <p>Utilizing proven methodologies tailored specifically to your unique needs.</p>
                  </div>
                </div>
                <div className="tp-expect-item">
                  <div className="tp-expect-icon">🎯</div>
                  <div className="tp-expect-text">
                    <strong>Collaborative Goal Setting</strong>
                    <p>Working together to set realistic milestones and tracking your progress over time.</p>
                  </div>
                </div>
                <div className="tp-expect-item">
                  <div className="tp-expect-icon">🔒</div>
                  <div className="tp-expect-text">
                    <strong>Strict Confidentiality</strong>
                    <p>Your privacy and the security of your sessions are our utmost priority.</p>
                  </div>
                </div>
              </div>
            </section>

          </div>

          {/* ── Sidebar Action Column ── */}
          <aside className="tp-side-col">
            <div className="tp-booking-card">
              
              <div className="tp-card-price">
                <span className="label">Session Rate</span>
                <div className="amount">
                  ₱{(therapist.rate || 1500).toLocaleString()}
                  <small>/ session</small>
                </div>
              </div>
              
              <div className="tp-perks">
                <div className="perk-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                    <polyline points="22 4 12 14.01 9 11.01"/>
                  </svg>
                  Instant Booking Confirmation
                </div>
                <div className="perk-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <circle cx="12" cy="12" r="10"/>
                    <polyline points="12 6 12 12 16 14"/>
                  </svg>
                  60 Minute Duration
                </div>
                <div className="perk-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                  Online or Walk-In Available
                </div>
              </div>

              <button 
                className="tp-btn-book-now"
                onClick={() => navigate('/checkout', { state: { therapist } })}
              >
                Schedule Consultation
              </button>
              
              <p className="tp-disclaimer">
                By booking, you agree to our 24-hour cancellation policy. A deposit may be required to secure your slot.
              </p>

            </div>
          </aside>

        </div>
      </div>
    </SidebarLayout>
  );
};

export default TherapistProfile;