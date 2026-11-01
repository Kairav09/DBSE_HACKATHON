import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, getToken } from '../lib/api';
import { useAuth } from '../context/AppContext';
import { fmtDateTime, inr } from '../lib/format';
import { summarizeUserActivity } from '../lib/stats';

export default function Profile() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [me, setMe] = useState(null);
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');

  const isAdmin = (me?.role || user?.role) === 'ADMIN';

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const m = await api.me();
        if (!cancelled) setMe(m);
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
      if (user?.role === 'USER') {
        try {
          const [vs, rs] = await Promise.all([api.myVehicles(), api.myReservations()]);
          if (!cancelled) setStats(summarizeUserActivity(vs, rs));
        } catch { /* stats are a bonus on this page */ }
      }
    })();
    return () => { cancelled = true; };
  }, [user?.role]);

  const display = me || user || {};
  const initial = (display.fullName || '?').trim().charAt(0).toUpperCase();

  const signOut = () => {
    logout();
    navigate('/');
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Profile</h2>
          <p className="muted">Your account, exactly as stored on the server.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="two-col">
        <section className="panel profile-card">
          <span className="profile-avatar">{initial}</span>
          <h3 className="profile-name">{display.fullName || '—'}</h3>
          <p className="muted small">{display.email || '—'}</p>
          <span className={`chip ${isAdmin ? 'chip-occupied' : 'chip-reserved'}`}>{display.role || '—'}</span>
          <dl className="profile-facts">
            {display.phone && <div><dt>Phone</dt><dd>{display.phone}</dd></div>}
            {display.createdAt && <div><dt>Member since</dt><dd>{fmtDateTime(display.createdAt)}</dd></div>}
            <div><dt>Account ID</dt><dd>#{display.id ?? '—'}</dd></div>
          </dl>
          <p className="muted small">
            Profile details are read-only here — they are set at registration and there is no
            profile-edit endpoint in this MVP.
          </p>
          <button className="btn btn-danger-ghost btn-block" onClick={signOut}>Sign out</button>
        </section>

        <section className="panel">
          <h3>{isAdmin ? 'Operator account' : 'Your activity'}</h3>
          {isAdmin ? (
            <p className="muted">
              Admin accounts manage locations and slots, see the dashboard and analytics, and can view
              every reservation. Booking and vehicle management are driver (USER) features, so they
              don&apos;t appear for this account.
            </p>
          ) : stats ? (
            <div className="stat-grid profile-stats">
              <div className="stat-tile"><span className="stat-label">Vehicles</span><span className="stat-value">{stats.vehicleCount}</span></div>
              <div className="stat-tile"><span className="stat-label">Total bookings</span><span className="stat-value">{stats.totalBookings}</span></div>
              <div className="stat-tile"><span className="stat-label">Active bookings</span><span className="stat-value">{stats.activeBookings}</span></div>
              <div className="stat-tile"><span className="stat-label">Total spent</span><span className="stat-value">{inr(stats.totalSpent)}</span></div>
            </div>
          ) : (
            <p className="muted">Loading your activity…</p>
          )}

          <h3 className="session-title">Session</h3>
          <dl className="profile-facts">
            <div><dt>Status</dt><dd>{getToken() ? 'Signed in (JWT session active)' : 'Not signed in'}</dd></div>
            <div><dt>Signed in as</dt><dd>{display.email || '—'}</dd></div>
          </dl>
          <p className="muted small">Sessions end when you sign out, or automatically when the API returns 401 — the app then returns you to the sign-in page.</p>
        </section>
      </div>
    </div>
  );
}
