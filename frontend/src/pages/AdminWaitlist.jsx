import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { fmtDateTime } from '../lib/format';
import StatusChip from '../components/StatusChip';

export default function AdminWaitlist() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.adminWaitlist();
        if (!cancelled) setItems(data || []);
      } catch (e) {
        if (!cancelled) setError(e.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Waitlist — all entries</h2>
          <p className="muted">
            Every waitlist entry across locations, newest first. Entries move WAITING → OFFERED when a matching
            booking is cancelled; unclaimed offers expire after about 15 minutes and the next entry is promoted.
          </p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/admin">← Dashboard</Link>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <div className="skel skel-panel" aria-hidden="true" />}
      {!loading && !error && items.length === 0 && (
        <div className="panel empty-state">No waitlist entries yet.</div>
      )}

      {!loading && items.length > 0 && (
        <div className="panel table-panel">
          <table className="table">
            <thead>
              <tr>
                <th>#</th><th>Location</th><th>Plate</th><th>Requested window</th><th>Offered slot</th><th>Status</th><th>Joined</th>
              </tr>
            </thead>
            <tbody>
              {items.map((w) => (
                <tr key={w.id}>
                  <td>{w.id}</td>
                  <td>{w.locationName}</td>
                  <td><span className="plate plate-sm">{w.licensePlate}</span></td>
                  <td>{fmtDateTime(w.startTime)} → {fmtDateTime(w.endTime)}</td>
                  <td>{w.offeredSlotCode || '—'}</td>
                  <td><StatusChip status={w.status} /></td>
                  <td>{fmtDateTime(w.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
