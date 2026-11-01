import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { fmtDateTime, inr } from '../lib/format';
import StatusChip from '../components/StatusChip';

const STATUSES = ['', 'RESERVED', 'OCCUPIED', 'COMPLETED', 'CANCELLED'];

export default function AdminReservations() {
  const [items, setItems] = useState([]);
  const [locations, setLocations] = useState([]);
  const [locationId, setLocationId] = useState('');
  const [status, setStatus] = useState('');
  const [date, setDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.locations().then(setLocations).catch(() => {});
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      setItems(await api.adminReservations({
        locationId: locationId || undefined,
        status: status || undefined,
        date: date || undefined,
      }));
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [locationId, status, date]);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>All reservations</h2>
          <p className="muted">Every booking across all locations.</p>
        </div>
      </div>

      <div className="panel filter-panel">
        <label className="inline-label">
          Location
          <select value={locationId} onChange={(e) => setLocationId(e.target.value)}>
            <option value="">All locations</option>
            {locations.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
          </select>
        </label>
        <label className="inline-label">
          Status
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map((s) => <option key={s || 'ALL'} value={s}>{s || 'All statuses'}</option>)}
          </select>
        </label>
        <label className="inline-label">
          Date
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        {(locationId || status || date) && (
          <button className="btn btn-ghost btn-sm" onClick={() => { setLocationId(''); setStatus(''); setDate(''); }}>
            Clear filters
          </button>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p className="muted">Loading reservations…</p>}
      {!loading && items.length === 0 && <div className="panel empty-state">No reservations match these filters.</div>}

      {!loading && items.length > 0 && (
        <div className="panel table-panel">
          <table className="table">
            <thead>
              <tr>
                <th>#</th><th>Location</th><th>Slot</th><th>Vehicle</th>
                <th>Start</th><th>End</th><th>Fee</th><th>Status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.id}>
                  <td>{r.id}</td>
                  <td>{r.locationName}</td>
                  <td><span className="slot-badge">{r.slotCode}</span></td>
                  <td>{r.licensePlate}</td>
                  <td>{fmtDateTime(r.startTime)}</td>
                  <td>{fmtDateTime(r.endTime)}</td>
                  <td>{inr(r.feeAmount)}</td>
                  <td><StatusChip status={r.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted small">{items.length} reservation{items.length === 1 ? '' : 's'}</p>
        </div>
      )}
    </div>
  );
}
