import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useToast } from '../context/AppContext';
import { fmtDateTime } from '../lib/format';
import StatusChip from '../components/StatusChip';

export default function Waitlist() {
  const toast = useToast();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [acting, setActing] = useState(null);

  const load = async () => {
    try {
      setItems(await api.myWaitlist());
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const act = async (entry, action) => {
    setActing(entry.id + action);
    try {
      if (action === 'claim') {
        const res = await api.claimWaitlist(entry.id);
        toast(`Claimed — slot ${res.slotCode || entry.offeredSlotCode} at ${res.locationName || entry.locationName} is booked for you.`);
        navigate('/reservations');
        return;
      }
      await api.cancelWaitlist(entry.id);
      toast('Waitlist entry cancelled');
      await load();
    } catch (e) {
      toast(e.message, 'error');
      await load();
    } finally {
      setActing(null);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>My waitlist</h2>
          <p className="muted">
            When a window is fully booked you can queue for it. If a booking is cancelled, the earliest matching
            entry is offered the slot — offers expire after about 15 minutes, so claim fast.
          </p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/locations">Find a location</Link>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && (
        <div className="card-list" aria-hidden="true">
          <div className="skel skel-card" /><div className="skel skel-card" />
        </div>
      )}
      {!loading && items.length === 0 && (
        <div className="panel empty-state">
          You are not on any waitlist. Open a location, pick a fully-booked time window, and use
          “Join the waitlist” in the booking panel.
        </div>
      )}

      <div className="card-list stagger">
        {items.map((w) => (
          <div key={w.id} className={`panel reservation-card ${w.status === 'OFFERED' ? 'offer-card' : ''}`}>
            <div className="reservation-main">
              <div className="reservation-title">
                <strong>{w.locationName}</strong>
                <StatusChip status={w.status} />
              </div>
              <p className="muted small">
                {w.licensePlate} · {fmtDateTime(w.startTime)} → {fmtDateTime(w.endTime)}
              </p>
              {w.status === 'WAITING' && (
                <p className="muted small">Waiting — you will get a notification the moment a matching slot frees up.</p>
              )}
              {w.status === 'OFFERED' && (
                <p className="offer-note">
                  <strong>Slot {w.offeredSlotCode} is being held for you.</strong>{' '}
                  Claim it now — unclaimed offers expire after about 15 minutes and pass to the next in line.
                </p>
              )}
              {w.status === 'CLAIMED' && (
                <p className="muted small">Claimed — this became a reservation on slot {w.offeredSlotCode || '—'}. See My Reservations.</p>
              )}
              {w.status === 'EXPIRED' && (
                <p className="muted small">This offer expired before it was claimed. Join the waitlist again for a new window.</p>
              )}
            </div>
            <div className="reservation-actions">
              {w.status === 'OFFERED' && (
                <button className="btn btn-primary btn-sm" disabled={acting !== null} onClick={() => act(w, 'claim')}>
                  {acting === w.id + 'claim' ? 'Claiming…' : `Claim slot ${w.offeredSlotCode || ''}`}
                </button>
              )}
              {(w.status === 'WAITING' || w.status === 'OFFERED') && (
                <button className="btn btn-danger-ghost btn-sm" disabled={acting !== null} onClick={() => act(w, 'cancel')}>
                  {acting === w.id + 'cancel' ? '…' : 'Cancel entry'}
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
