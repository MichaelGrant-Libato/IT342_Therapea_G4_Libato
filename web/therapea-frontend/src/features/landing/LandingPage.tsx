import { useEffect, useState } from "react";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  Heart,
  Lock,
  Menu,
  Plus,
  ShieldCheck,
  Sparkles,
  Users,
  X,
} from "lucide-react";
import "./LandingPage.css";

/* ---------- Data ---------- */

const NAV_LINKS = [
  { label: "How It Works", href: "#how-it-works" },
  { label: "Our Providers", href: "#our-providers" },
  { label: "FAQ", href: "#faq" },
];

const FEATURES = [
  {
    Icon: CheckCircle2,
    title: "Smart Triage",
    desc: "Our intelligent assessment matches you with the right care provider based on your unique needs.",
    tone: "sage" as const,
  },
  {
    Icon: Lock,
    title: "Secure Telehealth",
    desc: "HIPAA-compliant video sessions from the comfort of your home. Your privacy is our top priority.",
    tone: "lav" as const,
  },
  {
    Icon: Users,
    title: "Licensed Professionals",
    desc: "Access a network of board-certified therapists, counselors, and psychiatrists dedicated to you.",
    tone: "sage" as const,
  },
];

const STEPS = [
  { n: "01", title: "Complete Assessment", desc: "Take our comprehensive triage assessment so we understand your needs and goals." },
  { n: "02", title: "Get Matched", desc: "We connect you with licensed professionals who specialize in your areas of concern." },
  { n: "03", title: "Start Sessions", desc: "Schedule and attend secure video sessions at times that work for your lifestyle." },
];

const PROVIDERS = [
  { name: "Dr. Sarah Johnson", role: "Clinical Psychologist", spec: "Anxiety, depression & trauma therapy", image: "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&q=80&w=240&h=240" },
  { name: "Dr. Michael Chen", role: "Psychiatrist", spec: "Medication management & mood disorders", image: "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?auto=format&fit=crop&q=80&w=240&h=240" },
  { name: "Lisa Martinez", role: "Licensed Therapist", spec: "Relationship counseling & family therapy", image: "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&q=80&w=240&h=240" },
  { name: "Dr. James Wilson", role: "Clinical Counselor", spec: "Stress management & life transitions", image: "https://images.unsplash.com/photo-1622253692010-333f2da6031d?auto=format&fit=crop&q=80&w=240&h=240" },
];

const FAQS = [
  { q: "Do you accept insurance?", a: "We operate on a cash-pay basis but provide superbills you can submit to your insurance for out-of-network reimbursement." },
  { q: "How does the matching process work?", a: "Our Smart Triage system analyzes your initial assessment to pair you with providers who specialize in your areas of need." },
  { q: "Is my data and video session secure?", a: "Yes. TheraPea uses end-to-end encryption and is fully HIPAA-compliant. We never record your video sessions." },
  { q: "Can I switch therapists if it's not a good fit?", a: "Absolutely. Finding the right connection is crucial. You can request a new match anytime through your dashboard." },
];

const FOOTER_COLUMNS = [
  {
    title: "Quick Links",
    links: [
      { label: "How It Works", href: "#how-it-works" },
      { label: "Our Providers", href: "#our-providers" },
      { label: "FAQ", href: "#faq" },
    ],
  },
  {
    title: "Support",
    links: [
      { label: "Contact Us", href: "/contact" },
      { label: "Privacy Policy", href: "/privacy" },
      { label: "Terms of Service", href: "/terms" },
      { label: "HIPAA Compliance", href: "/hipaa" },
      { label: "Crisis Resources", href: "/crisis-resources", danger: true },
    ],
  },
  {
    title: "Platform",
    links: [
      { label: "Patient Portal", href: "/login" },
      { label: "Track Application", href: "/reference" },
    ],
  },
];

const DAILY_REFLECTIONS = [
  "Healing isn't linear — every small step you take is progress worth celebrating.",
  "Be gentle with yourself today. You are doing the best you can with what you have.",
  "Your mental health is a priority. Taking a pause to breathe is never time wasted.",
  "You don't have to have it all figured out right now. Just focus on the next right step.",
  "Every storm runs out of rain. Allow yourself to rest while you wait for the sun.",
  "You are allowed to take up space, speak your truth, and prioritize your peace.",
  "Progress is still progress, no matter how small it may seem to you today."
];

/* ---------- Tiny pieces ---------- */

function LogoMark() {
  return (
    <span className="lp-logo">
      <span className="lp-logo__mark">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="white" aria-hidden>
          <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z" />
        </svg>
      </span>
      <span className="lp-logo__text">
        Thera<em>Pea</em>
      </span>
    </span>
  );
}

function Stars({ n = 5 }: { n?: number }) {
  return (
    <span className="lp-stars" aria-label={`${n} out of 5 stars`}>
      {Array.from({ length: n }).map((_, i) => <span key={i}>★</span>)}
    </span>
  );
}

/* ---------- Page ---------- */

export default function LandingPage() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [openFaq, setOpenFaq] = useState<number | null>(0);
  const [reflectionQuote, setReflectionQuote] = useState(DAILY_REFLECTIONS[0]);

  useEffect(() => {
    // Handle Nav Scrolling
    const onScroll = () => setScrolled(window.scrollY > 16);
    window.addEventListener("scroll", onScroll);
    
    // Set dynamic daily reflection based on the day of the month
    const currentDay = new Date().getDate();
    setReflectionQuote(DAILY_REFLECTIONS[currentDay % DAILY_REFLECTIONS.length]);

    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <div className="landing-page">
      {/* Navbar */}
      <header className={`lp-nav${scrolled ? " is-scrolled" : ""}`}>
        <div className="lp-container lp-nav__inner">
          <LogoMark />
          <nav className="lp-nav__links">
            {NAV_LINKS.map((l) => (
              <a key={l.label} href={l.href}>{l.label}</a>
            ))}
          </nav>
          <div className="lp-nav__actions">
            <a href="/reference" className="lp-nav__track">Track Application</a>
            <a href="/login" className="lp-btn lp-btn--ghost">Login</a>
            <a href="/register" className="lp-btn lp-btn--primary">Sign Up</a>
          </div>
          <button
            className="lp-nav__menu-btn"
            aria-label="Toggle menu"
            onClick={() => setMenuOpen((v) => !v)}
          >
            {menuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        {menuOpen && (
          <div className="lp-nav__mobile">
            {NAV_LINKS.map((l) => (
              <a key={l.label} href={l.href} onClick={() => setMenuOpen(false)}>{l.label}</a>
            ))}
            <span className="lp-nav__mobile-divider" />
            <a href="/reference" style={{ color: "var(--sage-deep)", fontWeight: 600 }}>Track Application</a>
            <a href="/login">Login</a>
            <a href="/register" className="lp-btn lp-btn--primary" style={{ width: "100%" }}>Sign Up</a>
          </div>
        )}
      </header>

      <main>
        {/* Hero */}
        <section className="lp-hero">
          <div className="lp-container lp-hero__grid">
            <div className="lp-hero__content">
              <span className="lp-hero__badge">
                <span className="lp-hero__pulse" />
                Trusted by 10,000+ patients
              </span>
              <h1 className="lp-hero__title">
                Find Your<br />
                <em>Inner Peace</em>
              </h1>
              <p className="lp-hero__sub">
                Connect with licensed mental health professionals through our secure telehealth platform. Start your journey to wellness today.
              </p>
              <div className="lp-hero__cta">
                <a href="/register" className="lp-btn lp-btn--primary lp-btn--lg">
                  Take Triage Assessment
                  <ArrowRight size={18} className="lp-arrow" />
                </a>
                <a href="#how-it-works" className="lp-btn lp-btn--ghost lp-btn--lg">
                  How it works
                </a>
              </div>
              <div className="lp-hero__trust">
                <div className="lp-avatars">
                  {PROVIDERS.slice(0, 3).map((p) => (
                    <img key={p.name} src={p.image} alt={p.name} />
                  ))}
                </div>
                <div>
                  <Stars />
                  <p className="lp-trust__text">4.9/5 from 2,400+ reviews</p>
                </div>
              </div>
            </div>

            {/* Bento */}
            <div className="lp-bento">
              <div className="lp-bento__card lp-bento__card--wide">
                <div className="lp-bento__head">
                  <h3 className="lp-bento__title">Daily Reflection</h3>
                  <span className="lp-chip"><Sparkles size={12} /> Today</span>
                </div>
                <p className="lp-quote">
                  "{reflectionQuote}"
                </p>
                <div className="lp-suggest">
                  <div className="lp-suggest__icon">
                    <Heart size={16} fill="currentColor" />
                  </div>
                  <div>
                    <p className="lp-suggest__t">5-minute guided breathing</p>
                    <p className="lp-suggest__s">Recommended for you</p>
                  </div>
                </div>
              </div>

              <div className="lp-bento__card">
                <h4 className="lp-bento__title">Wellness Progress</h4>
                <div className="lp-progress">
                  {[
                    { label: "Mood", val: 72, color: "var(--sage)" },
                    { label: "Sleep", val: 58, color: "var(--lavender)" },
                  ].map((it) => (
                    <div key={it.label}>
                      <div className="lp-progress__head">
                        <span>{it.label}</span>
                        <b>{it.val}%</b>
                      </div>
                      <div className="lp-progress__bar">
                        <div className="lp-progress__fill" style={{ width: `${it.val}%`, background: it.color }} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="lp-bento__card lp-bento__card--center">
                <span className="lp-shield"><ShieldCheck size={22} /></span>
                <h4 className="lp-bento__title">HIPAA Secure</h4>
                <p className="lp-bento__sub">End-to-end encrypted</p>
              </div>
            </div>
          </div>
        </section>

        {/* Crisis */}
        <div className="lp-crisis">
          <div className="lp-container lp-crisis__inner">
            <div className="lp-crisis__msg">
              <AlertCircle size={18} />
              <p>
                If you are in crisis, call or text <strong>988</strong> (Suicide &amp; Crisis Lifeline) or visit your nearest emergency room.
              </p>
            </div>
            <a href="/crisis-resources" className="lp-crisis__link">View Crisis Resources →</a>
          </div>
        </div>

        {/* How it works */}
        <section id="how-it-works" className="lp-section lp-section--surface">
          <div className="lp-container">
            <div className="lp-section__head">
              <span className="lp-badge lp-badge--lav">Simple Process</span>
              <h2 className="lp-section__title">How it works</h2>
              <p className="lp-section__sub">Getting started with your mental health journey is simple and straightforward.</p>
            </div>
            <div className="lp-grid-3">
              {STEPS.map((s) => (
                <div key={s.n} className="lp-card lp-step">
                  <span className="lp-step__ghost">{s.n}</span>
                  <div className="lp-step__num">{s.n}</div>
                  <h3 className="lp-card__title">{s.title}</h3>
                  <p className="lp-card__desc">{s.desc}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Features */}
        <section className="lp-section">
          <div className="lp-container">
            <div className="lp-section__head">
              <span className="lp-badge lp-badge--sage">Why TheraPea</span>
              <h2 className="lp-section__title">Care designed around <em>you</em></h2>
              <p className="lp-section__sub">Everything you need for your mental health journey, all in one secure place.</p>
            </div>
            <div className="lp-grid-3">
              {FEATURES.map(({ Icon, title, desc, tone }) => (
                <div key={title} className="lp-card">
                  <div className={`lp-card__icon lp-card__icon--${tone}`}>
                    <Icon size={26} />
                  </div>
                  <h3 className="lp-card__title">{title}</h3>
                  <p className="lp-card__desc">{desc}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Providers */}
        <section id="our-providers" className="lp-section lp-section--surface">
          <div className="lp-container">
            <div className="lp-section__head">
              <span className="lp-badge lp-badge--sage">Our Team</span>
              <h2 className="lp-section__title">Meet our providers</h2>
              <p className="lp-section__sub">Experienced mental health professionals committed to your well-being.</p>
            </div>
            <div className="lp-grid-4">
              {PROVIDERS.map((p) => (
                <div key={p.name} className="lp-provider">
                  <div className="lp-provider__img-wrap">
                    <img src={p.image} alt={p.name} className="lp-provider__img" />
                  </div>
                  <h4 className="lp-provider__name">{p.name}</h4>
                  <p className="lp-provider__role">{p.role}</p>
                  <div className="lp-provider__stars"><Stars /></div>
                  <p className="lp-provider__spec">{p.spec}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* FAQ */}
        <section id="faq" className="lp-section">
          <div className="lp-container">
            <div className="lp-section__head">
              <span className="lp-badge lp-badge--sage">Questions?</span>
              <h2 className="lp-section__title">Frequently asked questions</h2>
              <p className="lp-section__sub">Everything you need to know about the platform and how we help.</p>
            </div>
            <div className="lp-faq">
              {FAQS.map((f, i) => {
                const isOpen = openFaq === i;
                return (
                  <div key={i} className={`lp-faq__item${isOpen ? " is-open" : ""}`}>
                    <button
                      className="lp-faq__btn"
                      aria-expanded={isOpen}
                      onClick={() => setOpenFaq(isOpen ? null : i)}
                    >
                      <span>{f.q}</span>
                      <span className="lp-faq__icon"><Plus size={18} /></span>
                    </button>
                    <div className="lp-faq__panel">
                      <div><p>{f.a}</p></div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </section>

        {/* CTA */}
        <section className="lp-cta">
          <div className="lp-container">
            <div className="lp-cta__inner">
              <h2 className="lp-cta__title">Ready to begin your journey?</h2>
              <p className="lp-cta__sub">
                Take the first step toward better mental health. Our triage assessment takes just 10 minutes.
              </p>
              <a href="/register" className="lp-btn lp-btn--white lp-btn--xl lp-cta__btn">
                Get started now
                <ArrowRight size={18} className="lp-arrow" />
              </a>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="lp-footer">
        <div className="lp-container">
          <div className="lp-footer__grid">
            <div className="lp-footer__brand">
              <LogoMark />
              <p className="lp-footer__desc">
                Providing accessible, high-quality mental health care through secure telehealth services.
              </p>
            </div>
            {FOOTER_COLUMNS.map((col) => (
              <div key={col.title}>
                <h5>{col.title}</h5>
                <ul className="lp-footer__links">
                  {col.links.map((l) => (
                    <li key={l.label}>
                      <a href={l.href} className={"danger" in l && l.danger ? "is-danger" : ""}>
                        {l.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="lp-footer__bottom">
            © 2026 TheraPea. All rights reserved. If you are in crisis, please call <strong>988</strong>.
          </div>
        </div>
      </footer>
    </div>
  );
}