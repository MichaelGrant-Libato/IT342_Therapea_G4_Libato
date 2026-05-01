import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/FindTherapist.css';

const SPECIALTIES  = ['All Specialties','Anxiety','Depression','Trauma','PTSD','Stress','CBT','EMDR','Grief'];
const AVAILABILITY = ['All Availability','Available Today','Available This Week','Online Only'];

const ITEMS = 6;

const Stars: React.FC<{ rating: number; reviews: number }> = ({ rating, reviews }) => (
  <div className="ft-stars">
    <div className="stars-flex">
      {[1,2,3,4,5].map(s => (
        <svg key={s} width="14" height="14" viewBox="0 0 24 24"
          fill={s <= Math.round(rating) ? '#D97706' : '#ECEEE8'} stroke="none">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
        </svg>
      ))}
    </div>
    <span className="rating-text">{rating} ({reviews} reviews)</span>
  </div>
);

const FindTherapist: React.FC = () => {
  const navigate = useNavigate();
  
  // State for the actual therapists list from DB
  const [therapists, setTherapists] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Search & Filter State
  const [search,    setSearch]    = useState('');
  const [specialty, setSpecialty] = useState('All Specialties');
  const [avail,     setAvail]     = useState('All Availability');
  const [page,      setPage]      = useState(1);

  // Fetch strictly from the backend
  useEffect(() => {
    const fetchDoctors = async () => {
      try {
        const res = await fetch('http://localhost:8083/api/doctors/list');
        if (res.ok) {
          const data = await res.json();
          if (data.success) {
            setTherapists(data.doctors || []);
          } else {
            setTherapists([]);
          }
        } else {
          setTherapists([]);
        }
      } catch (err) {
        console.error("Failed to fetch doctors from database:", err);
        setTherapists([]);
      } finally {
        setIsLoading(false);
      }
    };
    fetchDoctors();
  }, []);

  // Filter logic
  const filtered = therapists.filter(t => {
    const q = search.toLowerCase();
    
    // Safely check arrays/strings in case the DB returned null values
    const safeName = t.name ? t.name.toLowerCase() : '';
    const safeTitle = t.title ? t.title.toLowerCase() : '';
    const safeSpecialties = Array.isArray(t.specialties) ? t.specialties : [];

    const matchSearch = safeName.includes(q) ||
      safeSpecialties.some((s: string) => s.toLowerCase().includes(q)) ||
      safeTitle.includes(q);
      
    const matchSpec  = specialty === 'All Specialties' || safeSpecialties.includes(specialty);
    
    const matchAvail = avail === 'All Availability' ||
      (avail === 'Available Today' && t.available) ||
      (avail === 'Available This Week') ||
      (avail === 'Online Only' && t.online);
      
    return matchSearch && matchSpec && matchAvail;
  });

  const totalPages = Math.ceil(filtered.length / ITEMS);
  const paged      = filtered.slice((page - 1) * ITEMS, page * ITEMS);

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: 'var(--bg)' }}>
        <p style={{ color: 'var(--text-muted)' }}>Loading directory from database...</p>
      </div>
    );
  }

  return (
    <div className="ft-root">
      <div className="ft-topbar">
        <button className="ft-back-btn" onClick={() => navigate('/dashboard')}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          Back to Dashboard
        </button>
      </div>

      <div className="ft-body">
        <h1 className="ft-heading">Directory</h1>
        <p className="ft-subheading">Find a licensed professional to support your mental health journey.</p>

        <div className="ft-filters">
          <div className="ft-search-wrap">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              value={search}
              onChange={e => { setSearch(e.target.value); setPage(1); }}
              placeholder="Search by name or keyword..."
            />
          </div>

          <div className="ft-select-wrap">
            <select value={specialty} onChange={e => { setSpecialty(e.target.value); setPage(1); }}>
              {SPECIALTIES.map(s => <option key={s}>{s}</option>)}
            </select>
          </div>

          <div className="ft-select-wrap">
            <select value={avail} onChange={e => { setAvail(e.target.value); setPage(1); }}>
              {AVAILABILITY.map(a => <option key={a}>{a}</option>)}
            </select>
          </div>
        </div>

        <div className="ft-count">
          Showing <strong>{filtered.length} providers</strong> based on your filters.
        </div>

        {paged.length === 0 ? (
          <div className="ft-empty">
            <div className="ft-empty-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
            </div>
            <p>{therapists.length === 0 ? "No doctors are currently registered in the system." : "We couldn't find any therapists matching those criteria."}</p>
            {therapists.length > 0 && (
              <button className="ft-clear-btn" onClick={() => {
                setSearch(''); setSpecialty('All Specialties'); setAvail('All Availability');
              }}>Clear Filters</button>
            )}
          </div>
        ) : (
          <div className="ft-grid">
            {paged.map(t => {
              // Extract initials safely
              const cleanName = (t.name || "Provider").replace('Dr. ', '').trim();
              const nameParts = cleanName.split(' ');
              const initials = nameParts.length > 1 
                ? (nameParts[0][0] + nameParts[nameParts.length - 1][0]).toUpperCase()
                : cleanName.substring(0, 2).toUpperCase();
                
              const specialties = Array.isArray(t.specialties) ? t.specialties : [];

              return (
                <div key={t.id} className="ft-card" onClick={() => navigate(`/therapists/${t.id}`)}>
                  <div className="ft-card-top">
                    <div className="ft-avatar">{initials}</div>
                    <div className="ft-badges">
                      {t.available && <span className="ft-badge green">Accepting Patients</span>}
                      {t.online && <span className="ft-badge purple">Telehealth</span>}
                    </div>
                  </div>
                  
                  <div className="ft-card-body">
                    <h2 className="ft-card-name">{t.name}</h2>
                    <p className="ft-card-title">{t.title || "Licensed Professional"} • {t.experience || "Verified"}</p>
                    <Stars rating={t.rating || 5.0} reviews={t.reviews || 0} />
                    
                    <div className="ft-tags">
                      {specialties.slice(0,3).map((s: string) => <span key={s} className="ft-tag">{s}</span>)}
                      {specialties.length > 3 && <span className="ft-tag-more">+{specialties.length - 3}</span>}
                    </div>
                  </div>

                  <div className="ft-card-footer">
                    <div className="ft-rate">
                      <span className="rate-val">₱{(t.rate || 1500).toLocaleString()}</span>
                      <span className="rate-label">/ session</span>
                    </div>
                    <button className="ft-view-btn">View Profile</button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {totalPages > 1 && (
          <div className="ft-pagination">
            <button className="ft-pag-btn" onClick={() => setPage(p => Math.max(1,p-1))} disabled={page===1}>Prev</button>
            <span className="ft-pag-info">Page {page} of {totalPages}</span>
            <button className="ft-pag-btn" onClick={() => setPage(p => Math.min(totalPages,p+1))} disabled={page===totalPages}>Next</button>
          </div>
        )}
      </div>
    </div>
  );
};

export default FindTherapist;