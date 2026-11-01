import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { inr } from '../lib/format';
import PredictionPanel from '../components/PredictionPanel';

function Tile({ label, value, sub, tone }) {
  return (
    <div className={`stat-tile ${tone ? `tile-${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

export default function AdminDashboard() {
  const [dash, setDash] = useState(null);
  const [locations, setLocations] = useState([]);
  const [predLocationId, setPredLocationId] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    try {
      const [d, locs] = await Promise.all([api.dashboard(), api.locations()]);
      setDash(d);
      setLocations(locs);
      setPredLocationId((prev) => prev || (locs[0] ? String(locs[0].id) : ''));
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Admin dashboard</h2>
          <p className="muted">Live picture across every parking location.</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={load}>Refresh</button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p className="muted">Loading dashboard…</p>}

      {dash && (
        <>
          <div className="stat-grid">
            <Tile label="Locations" value={dash.totalLocations} />
            <Tile label="Total slots" value={dash.totalSlots} />
            <Tile label="Available" value={dash.availableSlots} tone="available" />
            <Tile label="Reserved" value={dash.reservedSlots} tone="reserved" />
            <Tile label="Occupied" value={dash.occupiedSlots} tone="occupied" />
            <Tile label="Occupancy" value={`${Number(dash.occupancyPercent).toFixed(1)}%`} sub="reserved + occupied" />
            <Tile label="Reservations today" value={dash.totalReservationsToday} />
            <Tile label="Revenue today" value={inr(dash.revenueToday)} />
          </div>

          <div className="admin-actions-row">
            <Link className="btn btn-dark btn-sm" to="/admin/locations">Manage locations & slots</Link>
            <Link className="btn btn-ghost btn-sm" to="/admin/analytics">Analytics</Link>
            <Link className="btn btn-ghost btn-sm" to="/admin/reservations">View all reservations</Link>
          </div>

          <div className="panel prediction-select-panel">
            <label className="inline-label">
              Prediction for
              <select value={predLocationId} onChange={(e) => setPredLocationId(e.target.value)}>
                {locations.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
              </select>
            </label>
          </div>
          {predLocationId && <PredictionPanel locationId={Number(predLocationId)} />}
        </>
      )}
    </div>
  );
}
