import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/AdminApprovals.css';

/* ─── Types ─────────────────────────────────────────────── */
interface DoctorRequest {
  id: string;
  fullName: string;
  email: string;
  clinicalBio: string;
  hourlyRate: number;
  prcLicenseUrl: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt?: string;
}

type FilterStatus = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED';
type NavSection   = 'approvals' | 'doctors' | 'reports' | 'settings';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8083';

/* ─── Icon ───────────────────────────────────────────────── */
const Icon = ({ type, size = 16 }: { type: string; size?: number }) => {
  const paths: Record<string, React.ReactNode> = {
    approvals: <><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></>,
    doctors:   <><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></>,
    reports:   <><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></>,
    settings:  <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></>,
    bell:      <><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/></>,
    logout:    <><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></>,
    close:     <><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></>,
    file:      <><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></>,
    search:    <><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></>,
    check:     <polyline points="20 6 9 17 4 12"/>,
    alert:     <><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></>,
    download:  <><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></>,
    shield:    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>,
    lock:      <><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></>,
    mail:      <><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22 6 12 13 2 6"/></>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {paths[type] ?? null}
    </svg>
  );
};

/* ─── Helpers ────────────────────────────────────────────── */
const mkInitials = (name: string) =>
  name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);

const fmtDate = (d?: string) =>
  d ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—';

/* ─── Reject Modal ───────────────────────────────────────── */
const RejectModal: React.FC<{
  doctor: DoctorRequest;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}> = ({ doctor, onConfirm, onCancel }) => {
  const [reason, setReason] = useState('');
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal-content small-modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Reject Application</h2>
          <button className="close-btn" onClick={onCancel}><Icon type="close" /></button>
        </div>
        <div className="modal-body">
          <p className="reject-intro">
            You are rejecting <strong>{doctor.fullName}</strong>'s application.
            Give a brief reason — the doctor will see this.
          </p>
          <div className="form-group">
            <label className="form-label">Reason for rejection</label>
            <textarea className="reject-textarea" rows={4}
              placeholder="e.g. The PRC license image is not legible. Please reapply with a clearer copy."
              value={reason} onChange={e => setReason(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn-outline" onClick={onCancel}>Cancel</button>
          <button className="btn-danger"
            onClick={() => onConfirm(reason || 'Your application was declined by administration.')}>
            Confirm Rejection
          </button>
        </div>
      </div>
    </div>
  );
};

/* ─── Logout Confirm Modal ───────────────────────────────── */
const LogoutModal: React.FC<{ onConfirm: () => void; onCancel: () => void }> = ({ onConfirm, onCancel }) => (
  <div className="modal-overlay" onClick={onCancel}>
    <div className="modal-content small-modal" onClick={e => e.stopPropagation()}>
      <div className="modal-header">
        <h2>Sign Out</h2>
        <button className="close-btn" onClick={onCancel}><Icon type="close" /></button>
      </div>
      <div className="modal-body">
        <p className="reject-intro">Are you sure you want to sign out of the admin panel?</p>
      </div>
      <div className="modal-footer">
        <button className="btn-outline" onClick={onCancel}>Cancel</button>
        <button className="btn-danger" onClick={onConfirm}>Yes, Sign Out</button>
      </div>
    </div>
  </div>
);

/* ══════════════════════════════════════════════════════════
   SECTION VIEWS
══════════════════════════════════════════════════════════ */

/* ── Approvals ───────────────────────────────────────────── */
const ApprovalsView: React.FC<{
  requests: DoctorRequest[];
  isLoading: boolean;
  onApprove: (id: string) => void;
  onReject: (doc: DoctorRequest) => void;
  actionLoading: string | null;
}> = ({ requests, isLoading, onApprove, onReject, actionLoading }) => {
  const [selectedDoctor, setSelectedDoctor] = useState<DoctorRequest | null>(null);
  const [filterStatus, setFilterStatus]     = useState<FilterStatus>('ALL');
  const [search, setSearch]                 = useState('');

  const counts = {
    total:    requests.length,
    pending:  requests.filter(r => r.status === 'PENDING').length,
    approved: requests.filter(r => r.status === 'APPROVED').length,
    rejected: requests.filter(r => r.status === 'REJECTED').length,
  };

  const filtered = requests.filter(r => {
    const okStatus = filterStatus === 'ALL' || r.status === filterStatus;
    const q = search.toLowerCase();
    const okSearch = !q || r.fullName.toLowerCase().includes(q) || r.email.toLowerCase().includes(q);
    return okStatus && okSearch;
  });

  // Live-sync the modal if status changes while it is open
  const liveDoc = selectedDoctor ? (requests.find(r => r.id === selectedDoctor.id) ?? selectedDoctor) : null;

  return (
    <>
      <div className="section-header">
        <div>
          <h1 className="page-title">License Approvals</h1>
          <p className="page-subtitle">Review doctor applications and approve or reject their credentials.</p>
        </div>
      </div>

      {/* Stats */}
      <div className="stats-grid">
        {([
          { label: 'Total',    value: counts.total,    f: 'ALL'      },
          { label: 'Pending',  value: counts.pending,  f: 'PENDING'  },
          { label: 'Approved', value: counts.approved, f: 'APPROVED' },
          { label: 'Rejected', value: counts.rejected, f: 'REJECTED' },
        ] as { label: string; value: number; f: FilterStatus }[]).map(s => (
          <button key={s.label}
            className={`stat-card ${filterStatus === s.f ? 'stat-active' : ''}`}
            onClick={() => setFilterStatus(s.f)}>
            <span className="stat-label">{s.label}</span>
            <span className="stat-value">{s.value}</span>
            {s.f === 'PENDING' && s.value > 0 && <span className="stat-dot" />}
          </button>
        ))}
      </div>

      {/* Toolbar */}
      <div className="toolbar">
        <div className="search-wrap">
          <Icon type="search" />
          <input className="search-input" placeholder="Search by name or email…"
            value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <div className="filter-pills">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as FilterStatus[]).map(f => (
            <button key={f} className={`filter-pill ${filterStatus === f ? 'active' : ''}`}
              onClick={() => setFilterStatus(f)}>
              {f === 'ALL' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="table-container">
        {isLoading ? (
          <div className="table-empty"><div className="spinner" /><p>Loading applications…</p></div>
        ) : filtered.length === 0 ? (
          <div className="table-empty"><Icon type="file" size={36} /><p>No applications match your filter.</p></div>
        ) : (
          <table className="approvals-table">
            <thead><tr>
              <th>Doctor</th><th>Rate</th><th>Submitted</th>
              <th>Status</th><th>Details</th><th>Actions</th>
            </tr></thead>
            <tbody>
              {filtered.map(doc => (
                <tr key={doc.id}>
                  <td>
                    <div className="doctor-cell">
                      <div className="doctor-initials">{mkInitials(doc.fullName)}</div>
                      <div>
                        <div className="doctor-name">{doc.fullName}</div>
                        <div className="doctor-email">{doc.email}</div>
                      </div>
                    </div>
                  </td>
                  <td className="rate-cell">₱{doc.hourlyRate?.toFixed(2) ?? '—'}</td>
                  <td className="date-cell">{fmtDate(doc.createdAt)}</td>
                  <td>
                    <span className={`status-badge status-${doc.status.toLowerCase()}`}>
                      {doc.status.charAt(0) + doc.status.slice(1).toLowerCase()}
                    </span>
                  </td>
                  <td>
                    <button className="view-btn" onClick={() => setSelectedDoctor(doc)}>
                      View application
                    </button>
                  </td>
                  <td>
                    <div className="action-group">
                      <button className="btn-approve"
                        disabled={doc.status !== 'PENDING' || actionLoading === doc.id + '_approve'}
                        onClick={() => onApprove(doc.id)}>
                        {actionLoading === doc.id + '_approve' ? '…' : 'Approve'}
                      </button>
                      <button className="btn-reject"
                        disabled={doc.status !== 'PENDING' || actionLoading === doc.id + '_reject'}
                        onClick={() => onReject(doc)}>
                        {actionLoading === doc.id + '_reject' ? '…' : 'Reject'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <p className="table-footer">
        Showing {filtered.length} of {requests.length} application{requests.length !== 1 ? 's' : ''}
      </p>

      {/* Doctor detail modal */}
      {liveDoc && (
        <div className="modal-overlay" onClick={() => setSelectedDoctor(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <div className="modal-doctor-info">
                <div className="modal-avatar">{mkInitials(liveDoc.fullName)}</div>
                <div>
                  <h2>{liveDoc.fullName}</h2>
                  <p className="modal-email">{liveDoc.email}</p>
                </div>
              </div>
              <button className="close-btn" onClick={() => setSelectedDoctor(null)}><Icon type="close" /></button>
            </div>
            <div className="modal-body">
              <div className="detail-row-inline">
                <div className="detail-group">
                  <label>Hourly Rate</label>
                  <p>₱{liveDoc.hourlyRate?.toFixed(2) ?? '—'}</p>
                </div>
                <div className="detail-group">
                  <label>Status</label>
                  <span className={`status-badge status-${liveDoc.status.toLowerCase()}`}>
                    {liveDoc.status.charAt(0) + liveDoc.status.slice(1).toLowerCase()}
                  </span>
                </div>
              </div>
              <div className="detail-group">
                <label>Clinical Biography</label>
                <div className="bio-box">{liveDoc.clinicalBio || 'No biography provided.'}</div>
              </div>
              
              {/* ✅ THE FIX: Dynamically generate the document link so the Admin can view the file */}
              <div className="detail-group">
                <label>PRC License Document</label>
                <div className="document-box">
                  <Icon type="file" />
                  <span>PRC License Upload</span>
                  <a 
                    href={liveDoc.prcLicenseUrl && liveDoc.prcLicenseUrl !== '#' 
                      ? liveDoc.prcLicenseUrl 
                      : `${API_BASE}/api/admin/doctors/${liveDoc.id}/prc-license`} 
                    target="_blank" 
                    rel="noreferrer" 
                    className="open-file-link"
                  >
                    View Document ↗
                  </a>
                </div>
              </div>

            </div>
            <div className="modal-footer">
              <button className="btn-outline" onClick={() => setSelectedDoctor(null)}>Close</button>
              <button className="btn-reject"
                disabled={liveDoc.status !== 'PENDING' || !!actionLoading}
                onClick={() => { onReject(liveDoc); setSelectedDoctor(null); }}>
                Reject
              </button>
              <button className="btn-approve"
                disabled={liveDoc.status !== 'PENDING' || !!actionLoading}
                onClick={() => onApprove(liveDoc.id)}>
                {actionLoading ? 'Processing…' : 'Approve Doctor'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

/* ── Doctors ─────────────────────────────────────────────── */
const DoctorsView: React.FC<{ doctors: DoctorRequest[] }> = ({ doctors }) => {
  const [search, setSearch] = useState('');
  const approved = doctors.filter(d => d.status === 'APPROVED');
  const filtered = approved.filter(d =>
    !search ||
    d.fullName.toLowerCase().includes(search.toLowerCase()) ||
    d.email.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <>
      <div className="section-header">
        <div>
          <h1 className="page-title">Doctors</h1>
          <p className="page-subtitle">All verified and active doctors on the platform.</p>
        </div>
        <div className="header-stat-pill">
          <Icon type="check" /> {approved.length} active doctor{approved.length !== 1 ? 's' : ''}
        </div>
      </div>

      <div className="toolbar" style={{ marginBottom: 20 }}>
        <div className="search-wrap">
          <Icon type="search" />
          <input className="search-input" placeholder="Search doctors…"
            value={search} onChange={e => setSearch(e.target.value)} />
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="empty-section">
          <Icon type="doctors" size={40} />
          <p>{approved.length === 0 ? 'No approved doctors yet.' : 'No doctors match your search.'}</p>
        </div>
      ) : (
        <div className="doctors-grid">
          {filtered.map(d => (
            <div key={d.id} className="doctor-card">
              <div className="doctor-card-top">
                <div className="doctor-card-avatar">{mkInitials(d.fullName)}</div>
                <span className="status-badge status-approved">Active</span>
              </div>
              <div className="doctor-card-body">
                <h3 className="doctor-card-name">{d.fullName}</h3>
                <p className="doctor-card-email">{d.email}</p>
                <p className="doctor-card-bio">
                  {d.clinicalBio
                    ? d.clinicalBio.slice(0, 110) + (d.clinicalBio.length > 110 ? '…' : '')
                    : 'No biography provided.'}
                </p>
              </div>
              <div className="doctor-card-footer">
                <span className="doctor-card-rate">₱{d.hourlyRate?.toFixed(2)}/hr</span>
                <span className="doctor-card-since">Since {fmtDate(d.createdAt)}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
};

/* ── Reports ─────────────────────────────────────────────── */
const ReportsView: React.FC<{ requests: DoctorRequest[] }> = ({ requests }) => {
  const pending  = requests.filter(r => r.status === 'PENDING').length;
  const approved = requests.filter(r => r.status === 'APPROVED').length;
  const rejected = requests.filter(r => r.status === 'REJECTED').length;
  const total    = requests.length;
  const approvalRate = total > 0 ? Math.round((approved / total) * 100) : 0;

  const monthly: Record<string, number> = {};
  requests.forEach(r => {
    if (!r.createdAt) return;
    const key = new Date(r.createdAt).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
    monthly[key] = (monthly[key] || 0) + 1;
  });
  const monthlyEntries = Object.entries(monthly).slice(-6);

  return (
    <>
      <div className="section-header">
        <div>
          <h1 className="page-title">Reports</h1>
          <p className="page-subtitle">An overview of platform activity and doctor applications.</p>
        </div>
        <button className="btn-outline-sm"><Icon type="download" /> Export CSV</button>
      </div>

      {/* KPIs */}
      <div className="reports-kpi-grid">
        {[
          { icon: 'file',    label: 'Total Applications', value: total,              cls: 'kpi-blue'   },
          { icon: 'check',   label: 'Approval Rate',      value: `${approvalRate}%`, cls: 'kpi-green'  },
          { icon: 'doctors', label: 'Active Doctors',     value: approved,           cls: 'kpi-purple' },
          { icon: 'alert',   label: 'Pending Review',     value: pending,            cls: 'kpi-yellow' },
        ].map(k => (
          <div key={k.label} className={`kpi-card ${k.cls}`}>
            <div className="kpi-icon"><Icon type={k.icon} size={20} /></div>
            <div className="kpi-value">{k.value}</div>
            <div className="kpi-label">{k.label}</div>
          </div>
        ))}
      </div>

      <div className="reports-row">
        {/* Breakdown */}
        <div className="report-card" style={{ flex: 2 }}>
          <h3 className="report-card-title">Application Breakdown</h3>
          <div className="breakdown-list">
            {[
              { label: 'Approved', count: approved, pct: total ? Math.round(approved/total*100) : 0, cls: 'bar-approved' },
              { label: 'Pending',  count: pending,  pct: total ? Math.round(pending/total*100)  : 0, cls: 'bar-pending'  },
              { label: 'Rejected', count: rejected, pct: total ? Math.round(rejected/total*100) : 0, cls: 'bar-rejected' },
            ].map(b => (
              <div key={b.label} className="breakdown-item">
                <div className="breakdown-meta">
                  <span className="breakdown-label">{b.label}</span>
                  <span className="breakdown-count">{b.count} ({b.pct}%)</span>
                </div>
                <div className="breakdown-track">
                  <div className={`breakdown-bar ${b.cls}`} style={{ width: `${b.pct || 0}%` }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Monthly */}
        <div className="report-card" style={{ flex: 1 }}>
          <h3 className="report-card-title">Monthly Submissions</h3>
          {monthlyEntries.length === 0 ? (
            <p className="report-empty">No data yet.</p>
          ) : (
            <div className="monthly-list">
              {monthlyEntries.map(([month, count]) => (
                <div key={month} className="monthly-row">
                  <span className="monthly-month">{month}</span>
                  <span className="monthly-count">{count}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Recent */}
      <div className="report-card" style={{ marginTop: 20 }}>
        <h3 className="report-card-title">Recent Applications</h3>
        <table className="approvals-table">
          <thead><tr><th>Doctor</th><th>Rate</th><th>Submitted</th><th>Status</th></tr></thead>
          <tbody>
            {requests.slice(0, 8).map(r => (
              <tr key={r.id}>
                <td>
                  <div className="doctor-cell">
                    <div className="doctor-initials">{mkInitials(r.fullName)}</div>
                    <div>
                      <div className="doctor-name">{r.fullName}</div>
                      <div className="doctor-email">{r.email}</div>
                    </div>
                  </div>
                </td>
                <td className="rate-cell">₱{r.hourlyRate?.toFixed(2) ?? '—'}</td>
                <td className="date-cell">{fmtDate(r.createdAt)}</td>
                <td>
                  <span className={`status-badge status-${r.status.toLowerCase()}`}>
                    {r.status.charAt(0) + r.status.slice(1).toLowerCase()}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
};

/* ── Settings ────────────────────────────────────────────── */
const SettingsView: React.FC = () => {
  const [emailNotifs, setEmailNotifs]   = useState(true);
  const [autoLogout, setAutoLogout]     = useState(false);
  const [siteName, setSiteName]         = useState('TheraPea');
  const [supportEmail, setSupportEmail] = useState('support@therapea.com');
  const [newPassword, setNewPassword]   = useState('');
  const [confirmPwd, setConfirmPwd]     = useState('');
  const [maxDays, setMaxDays]           = useState('7');
  const [rejTemplate, setRejTemplate]   = useState(
    'Thank you for applying to TheraPea. After reviewing your application, we are unable to approve it at this time.'
  );
  const [saved, setSaved] = useState(false);
  const [pwdError, setPwdError] = useState('');

  const handleSave = () => {
    if (newPassword && newPassword !== confirmPwd) {
      setPwdError('Passwords do not match.');
      return;
    }
    setPwdError('');
    setSaved(true);
    setTimeout(() => setSaved(false), 2500);
  };

  return (
    <>
      <div className="section-header">
        <div>
          <h1 className="page-title">Settings</h1>
          <p className="page-subtitle">Manage platform-wide configuration and preferences.</p>
        </div>
        {saved && <div className="save-toast">Changes saved successfully.</div>}
      </div>

      <div className="settings-grid">
        {/* General */}
        <div className="settings-card">
          <div className="settings-card-header">
            <div className="settings-icon-wrap blue"><Icon type="shield" size={18} /></div>
            <div>
              <h3>General</h3>
              <p>Basic platform information.</p>
            </div>
          </div>
          <div className="settings-fields">
            <div className="form-group">
              <label className="form-label">Platform Name</label>
              <input className="settings-input" value={siteName} onChange={e => setSiteName(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Support Email</label>
              <input className="settings-input" type="email" value={supportEmail} onChange={e => setSupportEmail(e.target.value)} />
            </div>
          </div>
        </div>

        {/* Notifications */}
        <div className="settings-card">
          <div className="settings-card-header">
            <div className="settings-icon-wrap green"><Icon type="bell" size={18} /></div>
            <div>
              <h3>Notifications</h3>
              <p>Control what emails get sent.</p>
            </div>
          </div>
          <div className="settings-fields">
            <div className="toggle-row">
              <div>
                <div className="toggle-label">Email notifications</div>
                <div className="toggle-desc">Send emails when new doctor applications arrive.</div>
              </div>
              <button className={`toggle-btn ${emailNotifs ? 'toggle-on' : ''}`}
                onClick={() => setEmailNotifs(v => !v)} aria-pressed={emailNotifs}>
                <span className="toggle-knob" />
              </button>
            </div>
            <div className="toggle-row">
              <div>
                <div className="toggle-label">Auto-logout after inactivity</div>
                <div className="toggle-desc">Sign out admin after 30 minutes of inactivity.</div>
              </div>
              <button className={`toggle-btn ${autoLogout ? 'toggle-on' : ''}`}
                onClick={() => setAutoLogout(v => !v)} aria-pressed={autoLogout}>
                <span className="toggle-knob" />
              </button>
            </div>
          </div>
        </div>

        {/* Security */}
        <div className="settings-card">
          <div className="settings-card-header">
            <div className="settings-icon-wrap red"><Icon type="lock" size={18} /></div>
            <div>
              <h3>Security</h3>
              <p>Admin account and access controls.</p>
            </div>
          </div>
          <div className="settings-fields">
            <div className="form-group">
              <label className="form-label">New Password</label>
              <input className="settings-input" type="password" placeholder="Leave blank to keep current"
                value={newPassword} onChange={e => setNewPassword(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Confirm Password</label>
              <input className="settings-input" type="password" placeholder="Repeat new password"
                value={confirmPwd} onChange={e => setConfirmPwd(e.target.value)} />
              {pwdError && <p style={{ color: '#B91C1C', fontSize: 13, marginTop: 4 }}>{pwdError}</p>}
            </div>
          </div>
        </div>

        {/* Approval Policy */}
        <div className="settings-card">
          <div className="settings-card-header">
            <div className="settings-icon-wrap purple"><Icon type="approvals" size={18} /></div>
            <div>
              <h3>Approval Policy</h3>
              <p>Rules for doctor onboarding.</p>
            </div>
          </div>
          <div className="settings-fields">
            <div className="form-group">
              <label className="form-label">Max review time (days)</label>
              <input className="settings-input" type="number" min={1}
                value={maxDays} onChange={e => setMaxDays(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Default rejection message</label>
              <textarea className="reject-textarea" rows={3}
                value={rejTemplate} onChange={e => setRejTemplate(e.target.value)} />
            </div>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 24, display: 'flex', justifyContent: 'flex-end' }}>
        <button className="btn-approve" style={{ padding: '12px 32px', fontSize: 15 }} onClick={handleSave}>
          Save Changes
        </button>
      </div>
    </>
  );
};

/* ══════════════════════════════════════════════════════════
   ROOT
══════════════════════════════════════════════════════════ */
const AdminApprovals: React.FC = () => {
  const navigate = useNavigate();

  const [requests,      setRequests]      = useState<DoctorRequest[]>([]);
  const [isLoading,     setIsLoading]     = useState(true);
  const [activeNav,     setActiveNav]     = useState<NavSection>('approvals');
  const [rejectTarget,  setRejectTarget]  = useState<DoctorRequest | null>(null);
  const [showLogout,    setShowLogout]    = useState(false);
  const [toast,         setToast]         = useState<{ msg: string; type: 'success' | 'error' } | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const navItems: { id: NavSection; label: string; icon: string }[] = [
    { id: 'approvals', label: 'Approvals', icon: 'approvals' },
    { id: 'doctors',   label: 'Doctors',   icon: 'doctors'   },
    { id: 'reports',   label: 'Reports',   icon: 'reports'   },
    { id: 'settings',  label: 'Settings',  icon: 'settings'  },
  ];

  const pendingCount = requests.filter(r => r.status === 'PENDING').length;

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  useEffect(() => {
    const load = async () => {
      try {
        const res = await fetch(`${API_BASE}/api/admin/doctors`);
        if (res.ok) setRequests(await res.json());
        else setToast({ msg: 'Failed to load applications.', type: 'error' });
      } catch {
        setToast({ msg: 'Could not connect to server.', type: 'error' });
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, []);

  const handleApprove = async (id: string) => {
    setActionLoading(id + '_approve');
    try {
      const res = await fetch(`${API_BASE}/api/admin/doctors/${id}/approve`, { method: 'POST' });
      if (res.ok) {
        setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r));
        setToast({ msg: 'Doctor approved successfully.', type: 'success' });
      } else {
        setToast({ msg: 'Failed to approve. Please try again.', type: 'error' });
      }
    } catch {
      setToast({ msg: 'Could not connect to server.', type: 'error' });
    } finally {
      setActionLoading(null);
    }
  };

 const handleRejectConfirm = async (reason: string) => {
    if (!rejectTarget) return;
    const id = rejectTarget.id;
    setRejectTarget(null);
    setActionLoading(id + '_reject');
    
    try {
      const res = await fetch(`${API_BASE}/api/admin/doctors/${id}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      });
      
      if (res.ok) {
        setRequests(prev => prev.filter(r => r.id !== id));
        
        setToast({ msg: 'Application declined. Account has been removed.', type: 'success' });
      } else {
        setToast({ msg: 'Failed to reject. Please try again.', type: 'error' });
      }
    } catch {
      setToast({ msg: 'Could not connect to server.', type: 'error' });
    } finally {
      setActionLoading(null);
    }
  };

  const handleLogout = () => {
    ['user', 'sessionStart', 'oauth_state'].forEach(k => {
      localStorage.removeItem(k);
      sessionStorage.removeItem(k);
    });
    navigate('/login', { replace: true });
  };

  return (
    <div className="admin-layout">

      {/* Toast */}
      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}

      {/* Sidebar */}
      <aside className="admin-sidebar">
        <div className="sidebar-brand">
          <div className="brand-logo">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="white">
              <path d="M12 21.7C17.3 17 22 13 22 8.5 22 5.4 19.6 3 16.5 3c-1.8 0-3.6.9-4.5 2.3C11.1 3.9 9.3 3 7.5 3 4.4 3 2 5.4 2 8.5c0 4.5 4.7 8.5 10 13.2z"/>
            </svg>
          </div>
          <span className="brand-text">TheraPea</span>
          <span className="brand-badge">Admin</span>
        </div>

        <nav className="sidebar-nav">
          {navItems.map(item => (
            <button key={item.id}
              className={`nav-item ${activeNav === item.id ? 'active' : ''}`}
              onClick={() => setActiveNav(item.id)}>
              <Icon type={item.icon} />
              {item.label}
              {item.id === 'approvals' && pendingCount > 0 && (
                <span className="nav-badge">{pendingCount}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="admin-profile-row">
            <div className="admin-avatar-sm">A</div>
            <div className="admin-profile-info">
              <span className="admin-profile-name">Admin</span>
              <span className="admin-profile-role">Super Admin</span>
            </div>
          </div>
          <button className="nav-item logout-item" onClick={() => setShowLogout(true)}>
            <Icon type="logout" /> Sign Out
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="admin-main">
        <header className="admin-topbar">
          <span className="topbar-section">
            {navItems.find(n => n.id === activeNav)?.label}
          </span>
          <div className="topbar-right">
            <div className="bell-wrap">
              <Icon type="bell" />
              {pendingCount > 0 && <span className="bell-dot" />}
            </div>
          </div>
        </header>

        <div className="admin-content">
          {activeNav === 'approvals' && (
            <ApprovalsView
              requests={requests} isLoading={isLoading}
              onApprove={handleApprove} onReject={setRejectTarget}
              actionLoading={actionLoading}
            />
          )}
          {activeNav === 'doctors'  && <DoctorsView  doctors={requests} />}
          {activeNav === 'reports'  && <ReportsView  requests={requests} />}
          {activeNav === 'settings' && <SettingsView />}
        </div>
      </main>

      {rejectTarget && (
        <RejectModal doctor={rejectTarget}
          onConfirm={handleRejectConfirm} onCancel={() => setRejectTarget(null)} />
      )}
      {showLogout && (
        <LogoutModal onConfirm={handleLogout} onCancel={() => setShowLogout(false)} />
      )}
    </div>
  );
};

export default AdminApprovals;