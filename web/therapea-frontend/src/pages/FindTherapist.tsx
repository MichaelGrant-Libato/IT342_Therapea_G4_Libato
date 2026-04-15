import React from 'react';
import '../styles/FindTherapist.css';

const FindTherapist: React.FC = () => {
  return (
    <div className="therapist-container">
      <header className="therapist-header">
        <h1>Find Your Therapist</h1>
        <div className="filters-row">
          <div className="search-wrapper">
            <input type="text" placeholder="Search by name or specialty..." />
            <span className="search-icon">⌕</span>
          </div>
          <select className="filter-select">
            <option>All Specialties</option>
          </select>
          <select className="filter-select">
            <option>All Availability</option>
          </select>
        </div>
        <div className="results-count">Showing <strong>8 therapists</strong></div>
      </header>

      <main className="therapist-grid">
        {/* Repeating a static card to match the grid requirement */}
        {[1, 2, 3, 4].map(id => (
          <div className="therapist-card" key={id}>
            <div className="avatar-placeholder"></div>
            <div className="rating-badge">★</div>
            <hr className="card-divider" />
            <div className="rate-label">HOURLY RATE</div>
            <div className="rate-sub">per session</div>
          </div>
        ))}
      </main>

      <footer className="pagination">
        <button className="page-btn">←</button>
        <button className="page-btn active">1</button>
        <button className="page-btn">2</button>
        <button className="page-btn">3</button>
        <span className="page-dots">...</span>
        <button className="page-btn">12</button>
        <button className="page-btn">→</button>
      </footer>
    </div>
  );
};

export default FindTherapist;