import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth, useToast } from '../context/AppContext';
import { useCountUp } from '../hooks/useCountUp';
import { fmtDateTime, inr } from '../lib/format';
import { pickNextReservation, summarizeUserActivity } from '../lib/stats';
import StatusChip from '../components/StatusChip';

function CountTile({ label, target, format, tone }) {
  const v = useCountUp(target ?? 0);
  return (
    <div className={`stat-tile ${tone ? `tile-${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{format ? format(Math.round(v)) : Math.round(v)}</span>
    </div>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const toast = useToast();
  const [vehicles, setVehicles] = useState(null);
  const [reservations, setReservations] = useState(null);
  const [error, setError] = useState('');
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    try {
      const [vs, rs] = await Promise.all([api.myVehicles(), api.myReservations()]);
      setVehicles(vs);
      setReservations(rs);
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const loading = vehicles === null || reservations === null;
  const stats = loading ? null : summarizeUserActivity(vehicles, reservations);
  const next = loading ? null : pickNextReservation(reservations);
  const recent = loading ? [] : reservations.slice(0, 5);

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const firstName = (user?.fullName || '').trim().split(/\s+/)[0];
  const today = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  const act = async (action) => {
    if (!next) return;
    setActing(true);
    try {
      const updated = action === 'entry' ? await api.entryReservation(next.id) : await api.exitReservation(next.id);
      toast(`Reservation #${next.id} is now ${updated.status}`);
      await load();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setActing(false);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>{greeting}{firstName ? `, ${firstName}` : ''}</h2>
          <p className="muted">{today} · Here is your parking at a glance.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <>
          <div className="stat-grid" aria-hidden="true">
            {[0, 1, 2, 3].map((i) => <div key={i} className="skel skel-tile" />)}
          </div>
          <div className="skel skel-panel" aria-hidden="true" />
        </>
      ) : (
        <>
          <div className="stat-grid stagger">
            <CountTile label="My vehicles" target={stats.vehicleCount} />
            <CountTile label="Total bookings" target={stats.totalBookings} />
            <CountTile label="Active bookings" target={stats.activeBookings} tone="reserved" />
            <CountTile label="Total spent" target={stats.totalSpent} format={(n) => inr(n)} />
          </div>

          {next ? (
            <section className="panel next-card">
              <div className="next-card-main">
                <p className="stat-label">{next.status === 'OCCUPIED' ? 'You are parked now' : 'Your next booking'}</p>
                <div className="reservation-title">
                  <strong>{next.locationName}</strong>
                  <span className="slot-badge">Slot {next.slotCode}</span>
                  <StatusChip status={next.status} />
                </div>
                <p className="muted small">
                  {next.licensePlate} · {fmtDateTime(next.startTime)} → {fmtDateTime(next.endTime)} · Fee <strong className="ink">{inr(next.feeAmount)}</strong>
                </p>
              </div>
              <div className="reservation-actions">
                {next.status === 'RESERVED' && (
                  <button className="btn btn-dark" disabled={acting} onClick={() => act('entry')}>
                    {acting ? '…' : 'Entry — I have arrived'}
                  </button>
                )}
                {next.status === 'OCCUPIED' && (
                  <button className="btn btn-primary" disabled={acting} onClick={() => act('exit')}>
                    {acting ? '…' : 'Exit — Check out'}
                  </button>
                )}
                <Link className="btn btn-ghost" to="/reservations">All reservations</Link>
              </div>
            </section>
          ) : (
            <section className="panel empty-state">
              No active booking right now. <Link to="/locations">Find a parking location</Link> and reserve a slot.
            </section>
          )}

          <div className="quick-actions stagger">
            <Link className="quick-action" to="/locations">
              <strong>Book a slot</strong>
              <span>Open the live slot map</span>
            </Link>
            <Link className="quick-action" to="/vehicles">
              <strong>Add a vehicle</strong>
              <span>Register a plate for booking</span>
            </Link>
            <Link className="quick-action" to="/reservations">
              <strong>My reservations</strong>
              <span>Entry, exit, passes &amp; history</span>
            </Link>
            <Link className="quick-action" to="/help">
              <strong>How it works</strong>
              <span>Fees, rules &amp; FAQs</span>
            </Link>
          </div>

          <section className="panel">
            <div className="panel-head">
              <h3>Recent bookings</h3>
              <Link to="/reservations" className="small">View all</Link>
            </div>
            {recent.length === 0 ? (
              <p className="muted">Nothing booked yet — your reservations will appear here.</p>
            ) : (
              <div className="recent-list">
                {recent.map((r) => (
                  <div key={r.id} className="recent-row">
                    <div className="recent-main">
                      <strong>{r.locationName}</strong>
                      <span className="slot-badge">Slot {r.slotCode}</span>
                      <StatusChip status={r.status} />
                    </div>
                    <span className="muted small">{fmtDateTime(r.startTime)} · {inr(r.feeAmount)}</span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
