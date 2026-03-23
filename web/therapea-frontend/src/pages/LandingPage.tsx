import { useState, useEffect } from "react";
import "../styles/LandingPage.css";

const NAV_LINKS = ["How It Works", "Our Providers", "Pricing", "FAQ"];

const FEATURES = [
  {
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
      </svg>
    ),
    title: "Smart Triage",
    desc: "Our intelligent assessment system matches you with the right care provider based on your unique needs and preferences.",
    color: "#8BA888",
  },
  {
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/>
      </svg>
    ),
    title: "Secure Telehealth",
    desc: "HIPAA-compliant video sessions from the comfort of your home. Your privacy and security are our top priorities.",
    color: "#A78BFA",
  },
  {
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/>
      </svg>
    ),
    title: "Licensed Professionals",
    desc: "Access a network of board-certified therapists, counselors, and psychiatrists dedicated to your mental wellness.",
    color: "#8BA888",
  },
];

const STEPS = [
  { n: "01", title: "Complete Assessment", desc: "Take our comprehensive triage assessment to help us understand your needs and goals." },
  { n: "02", title: "Get Matched", desc: "We connect you with licensed professionals who specialize in your areas of concern." },
  { n: "03", title: "Start Sessions", desc: "Schedule and attend secure video sessions at times that work for your lifestyle." },
];

const PROVIDERS = [
  { name: "Dr. Sarah Johnson", role: "Clinical Psychologist", spec: "Anxiety, depression & trauma therapy", rating: 5, initials: "SJ", color: "#8BA888" },
  { name: "Dr. Michael Chen", role: "Psychiatrist", spec: "Medication management & mood disorders", rating: 5, initials: "MC", color: "#A78BFA" },
  { name: "Lisa Martinez", role: "Licensed Therapist", spec: "Relationship counseling & family therapy", rating: 5, initials: "LM", color: "#8BA888" },
  { name: "Dr. James Wilson", role: "Clinical Counselor", spec: "Stress management & life transitions", rating: 5, initials: "JW", color: "#A78BFA" },
];

export default function LandingPage() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  return (
    <div className="landing-page">
      {/* ── Navbar ── */}
      <nav className={`navbar ${scrolled ? 'scrolled' : ''}`}>
        {/* Logo */}
        <div className="nav-logo">
          <div className="nav-logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="white">
              <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
            </svg>
          </div>
          <span className="nav-logo-text">
            Thera<span>Pea</span>
          </span>
        </div>

        {/* Nav links */}
        <div className="nav-links">
          {NAV_LINKS.map(l => (
            <a key={l} href={`#${l.toLowerCase().replace(/ /g, "-")}`} className="nav-link">{l}</a>
          ))}
        </div>

        {/* CTA buttons */}
        <div className="nav-actions">
          <a href="/login" className="btn btn-secondary">Login</a>
          <a href="/register" className="btn btn-primary">Sign Up</a>
        </div>
      </nav>

      {/* ── Hero ── */}
      <section className="hero">
        {/* Background blobs */}
        <div className="blob-1"/>
        <div className="blob-2"/>

        <div className="hero-container">
          <div className="hero-grid">
            {/* Left */}
            <div className="hero-left">
              <div className="hero-badge">
                <span className="hero-badge-dot"/>
                Trusted by 10,000+ patients
              </div>

              <h1 className="hero-title">
                Find Your<br/>
                <span className="hero-title-gradient">Inner Peace</span>
              </h1>

              <p className="hero-subtitle">
                Connect with licensed mental health professionals through our secure telehealth platform. Start your journey to wellness today.
              </p>

              <div className="hero-actions">
                <a href="/register" className="btn btn-primary btn-large">
                  Take Triage Assessment
                  <svg style={{ marginLeft: 8 }} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <path d="M5 12h14M12 5l7 7-7 7"/>
                  </svg>
                </a>
                <a href="#how-it-works" className="btn btn-secondary btn-large">
                  Learn How It Works
                </a>
              </div>

              <div className="hero-trust">
                {/* Avatar stack */}
                <div className="avatar-stack">
                  <div className="avatar avatar-1">SJ</div>
                  <div className="avatar avatar-2">MC</div>
                  <div className="avatar avatar-3">LM</div>
                  <div className="avatar avatar-4">JW</div>
                </div>
                <div className="trust-info">
                  <div className="trust-stars">
                    <span className="star">★</span>
                    <span className="star">★</span>
                    <span className="star">★</span>
                    <span className="star">★</span>
                    <span className="star">★</span>
                  </div>
                  <p className="trust-text">4.9/5 from 2,400+ reviews</p>
                </div>
              </div>
            </div>

            {/* Right — floating UI cards */}
            <div className="hero-visual">
              {/* Main card */}
              <div className="floating-card floating-card-1">
                <div className="session-card-header">
                  <div className="session-icon">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                      <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
                    </svg>
                  </div>
                  <div className="session-info">
                    <h4>Your Next Session</h4>
                    <p>Today, 3:00 PM</p>
                  </div>
                </div>
                <div className="session-provider">
                  <div className="provider-avatar">SJ</div>
                  <div className="provider-info">
                    <h4>Dr. Sarah Johnson</h4>
                    <p>Clinical Psychologist</p>
                  </div>
                  <div className="status-dot"/>
                </div>
              </div>

              {/* Progress card */}
              <div className="floating-card floating-card-2">
                <h4>Wellness Progress</h4>
                {[
                  { label: "Mood", val: 72, color: "progress-mood" },
                  { label: "Sleep", val: 58, color: "progress-sleep" },
                  { label: "Anxiety", val: 45, color: "progress-anxiety" },
                ].map(item => (
                  <div key={item.label} className="progress-item">
                    <div className="progress-header">
                      <span className="progress-label">{item.label}</span>
                      <span className={`progress-value ${item.color}`}>{item.val}%</span>
                    </div>
                    <div className="progress-bar">
                      <div className={`progress-fill ${item.color}`} style={{ width: `${item.val}%` }}/>
                    </div>
                  </div>
                ))}
              </div>

              {/* Notification card */}
              <div className="floating-card floating-card-3">
                <div className="notification-content">
                  <div className="notification-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#A78BFA" strokeWidth="2">
                      <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 9.81a19.79 19.79 0 01-3.07-8.68A2 2 0 012 .98h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L6.91 8.84a16 16 0 006.29 6.29l1.22-1.24a2 2 0 012.11-.45 12.84 12.84 0 002.81.7A2 2 0 0122 16.16v.76z"/>
                    </svg>
                  </div>
                  <div className="notification-text">
                    <h4>Session Confirmed!</h4>
                    <p>Link sent to your email</p>
                  </div>
                </div>
              </div>

              {/* Center graphic */}
              <div className="center-graphic">
                <div className="center-icon">
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="white">
                    <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
                  </svg>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── Crisis Banner ── */}
      <div className="crisis-banner">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="#DC2626">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
        </svg>
        <p>
          If you are in crisis, call or text <strong>988</strong> (Suicide & Crisis Lifeline) or visit your nearest emergency room.
        </p>
      </div>

      {/* ── Features ── */}
      <section id="how-it-works" className="section">
        <div className="section-container">
          <div className="section-header">
            <span className="section-badge badge-primary">Why TheraPea</span>
            <h2 className="section-title">
              Care designed around <em>you</em>
            </h2>
            <p className="section-subtitle">
              Everything you need for your mental health journey, all in one secure place.
            </p>
          </div>

          <div className="features-grid">
            {FEATURES.map((f, i) => (
              <div key={f.title} className="card">
                <div className={`feature-icon ${f.color === '#8BA888' ? 'feature-icon-green' : 'feature-icon-purple'}`}>
                  {f.icon}
                </div>
                <h3 className="card-title">{f.title}</h3>
                <p className="card-description">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── How It Works ── */}
      <section className="section section-light">
        <div className="section-container">
          <div className="section-header">
            <span className="section-badge badge-secondary">Simple Process</span>
            <h2 className="section-title">How It Works</h2>
            <p className="section-subtitle">
              Getting started with your mental health journey is simple and straightforward.
            </p>
          </div>

          <div className="steps-grid">
            {STEPS.map((s, i) => (
              <div key={s.n} className="card step-card">
                <div className="step-number-bg">{s.n}</div>
                <div className="step-number">{s.n}</div>
                <h3 className="card-title">{s.title}</h3>
                <p className="card-description">{s.desc}</p>
                {i < STEPS.length - 1 && (
                  <div className="step-connector">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <path d="M5 12h14M12 5l7 7-7 7"/>
                    </svg>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Providers ── */}
      <section id="our-providers" className="section">
        <div className="section-container">
          <div className="section-header">
            <span className="section-badge badge-primary">Our Team</span>
            <h2 className="section-title">Meet Our Providers</h2>
            <p className="section-subtitle">
              Experienced mental health professionals committed to your well-being.
            </p>
          </div>

          <div className="providers-grid">
            {PROVIDERS.map(p => (
              <div key={p.name} className="provider-card">
                <div className={`provider-avatar-large ${p.color === '#8BA888' ? 'provider-avatar-green' : 'provider-avatar-purple'}`}>
                  {p.initials}
                </div>
                <h4 className="provider-name">{p.name}</h4>
                <p className={`provider-role ${p.color === '#8BA888' ? '' : 'provider-role-purple'}`}>{p.role}</p>
                <div style={{ display: "flex", justifyContent: "center", gap: 2, marginBottom: 12 }}>
                  {Array.from({ length: p.rating }, (_, i) => <span key={i} className="star">★</span>)}
                </div>
                <p className="provider-specialty">{p.spec}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section className="cta-section">
        <div className="cta-blob-1"/>
        <div className="cta-blob-2"/>
        <div className="cta-content">
          <h2 className="cta-title">Ready to Begin Your Journey?</h2>
          <p className="cta-description">
            Take the first step towards better mental health. Our triage assessment takes just 10 minutes.
          </p>
          <a href="/register" className="btn btn-white btn-xlarge">
            Get Started Now
            <svg style={{ marginLeft: 10 }} width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </a>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="footer">
        <div className="section-container">
          <div className="footer-grid">
            {/* Brand */}
            <div>
              <div className="footer-brand">
                <div className="footer-logo">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="white">
                    <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
                  </svg>
                </div>
                <span className="footer-brand-name">TheraPea</span>
              </div>
              <p className="footer-description">
                Providing accessible, high-quality mental health care through secure telehealth services.
              </p>
            </div>

            {/* Links */}
            {[
              { heading: "Quick Links", links: ["About Us", "Our Providers", "How It Works", "Pricing", "FAQ"] },
              { heading: "Support", links: ["Contact Us", "Privacy Policy", "Terms of Service", "HIPAA Compliance", "Crisis Resources"] },
              { heading: "Platform", links: ["Patient Portal", "Doctor Portal", "Admin Dashboard", "API Docs"] },
            ].map(col => (
              <div key={col.heading} className="footer-column">
                <h5>{col.heading}</h5>
                <ul className="footer-links">
                  {col.links.map(l => (
                    <li key={l}>
                      <a href="#">{l}</a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="footer-bottom">
            <p className="footer-copyright">
              © 2026 TheraPea. All rights reserved. If you are in crisis, please call <strong>988</strong> or visit your nearest emergency room.
            </p>
            <div className="footer-legal">
              {["Privacy", "Terms", "HIPAA"].map(l => (
                <a key={l} href="#">{l}</a>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
