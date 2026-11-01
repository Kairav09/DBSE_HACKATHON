import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useToast } from '../context/AppContext';
import { fmtHour, inr } from '../lib/format';

const STATUS_ORDER = ['RESERVED', 'OCCUPIED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];
const STATUS_COLORS = { RESERVED: '#eda812', OCCUPIED: '#cf3f38', COMPLETED: '#1d9d63', CANCELLED: '#b9beb9', NO_SHOW: '#6d7268' };
const DOW_ORDER = [1, 2, 3, 4, 5, 6, 0]; // Monday first
const DOW_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function countStatuses(slots) {
  const counts = { AVAILABLE: 0, RESERVED: 0, OCCUPIED: 0, total: 0 };
  for (const s of slots) {
    if (s.isActive === false) continue;
    counts.total += 1;
    if (counts[s.status] !== undefined) counts[s.status] += 1;
  }
  return counts;
}

export default function AdminAnalytics() {
  const toast = useToast();
  const [dash, setDash] = useState(null);
  const [reservations, setReservations] = useState(null);
  const [occByLoc, setOccByLoc] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  const exportCsv = async () => {
    setExporting(true);
    try {
      const blob = await api.exportReservationsCsv();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'reservations.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast('reservations.csv downloaded');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setExporting(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [d, all, locs] = await Promise.all([api.dashboard(), api.adminReservations(), api.locations()]);
        const occ = await Promise.all(locs.map(async (loc) => {
          try {
            return { name: loc.name, counts: countStatuses(await api.slotsByLocation(loc.id)) };
          } catch {
            return { name: loc.name, counts: null };
          }
        }));
        if (cancelled) return;
        setDash(d);
        setReservations(all);
        setOccByLoc(occ);
      } catch (e) {
        if (!cancelled) setError(e.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  // Aggregations over the full admin reservation list.
  const revenueByLoc = new Map();
  const byStatus = { RESERVED: 0, OCCUPIED: 0, COMPLETED: 0, CANCELLED: 0, NO_SHOW: 0 };
  for (const r of reservations || []) {
    if (byStatus[r.status] !== undefined) byStatus[r.status] += 1;
    if (r.status !== 'CANCELLED') {
      revenueByLoc.set(r.locationName, (revenueByLoc.get(r.locationName) || 0) + Number(r.feeAmount || 0));
    }
  }
  const revenueRows = [...revenueByLoc.entries()].sort((a, b) => b[1] - a[1]);
  const maxRevenue = revenueRows.length ? revenueRows[0][1] : 0;
  const totalBookings = STATUS_ORDER.reduce((s, k) => s + byStatus[k], 0);

  // v3: occupancy heatmap — bookings per (day-of-week x start hour),
  // CANCELLED excluded (a cancelled booking never occupied anything).
  const heat = new Map();
  let heatMax = 0;
  for (const r of reservations || []) {
    if (r.status === 'CANCELLED' || !r.startTime) continue;
    const d = new Date(r.startTime);
    if (Number.isNaN(d.getTime())) continue;
    const key = `${d.getDay()}-${d.getHours()}`;
    const v = (heat.get(key) || 0) + 1;
    heat.set(key, v);
    if (v > heatMax) heatMax = v;
  }

  let donut = 'var(--line)';
  if (totalBookings > 0) {
    let acc = 0;
    const parts = STATUS_ORDER.filter((s) => byStatus[s] > 0).map((s) => {
      const from = (acc / totalBookings) * 360;
      acc += byStatus[s];
      const to = (acc / totalBookings) * 360;
      return `${STATUS_COLORS[s]} ${from}deg ${to}deg`;
    });
    donut = `conic-gradient(${parts.join(', ')})`;
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Analytics</h2>
          <p className="muted">Compact views aggregated from the dashboard and the full reservation list.</p>
        </div>
        <div className="head-actions">
          <button className="btn btn-dark btn-sm" onClick={exportCsv} disabled={exporting}>
            {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
          <Link className="btn btn-ghost btn-sm" to="/admin">← Dashboard</Link>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && (
        <div className="two-col" aria-hidden="true">
          <div className="skel skel-panel" /><div className="skel skel-panel" />
        </div>
      )}

      {!loading && !error && (
        <>
          <div className="analytics-grid">
            <section className="panel">
              <h3>Revenue by location</h3>
              <p className="muted small">Sum of booking fees across all recorded reservations, cancelled excluded.</p>
              {revenueRows.length === 0 && <p className="muted">No revenue yet.</p>}
              <div className="hbar-list">
                {revenueRows.map(([name, value]) => (
                  <div key={name} className="hbar-row">
                    <span className="hbar-label">{name}</span>
                    <span className="hbar-track">
                      <span className="hbar-fill" style={{ width: `${maxRevenue ? Math.max(3, (value / maxRevenue) * 100) : 0}%` }} />
                    </span>
                    <strong className="hbar-value">{inr(value)}</strong>
                  </div>
                ))}
              </div>
              {dash && <p className="muted small">For comparison: revenue today across all locations is {inr(dash.revenueToday)}.</p>}
            </section>

            <section className="panel">
              <h3>Bookings by status</h3>
              <p className="muted small">{totalBookings} reservation{totalBookings === 1 ? '' : 's'} in total.</p>
              <div className="donut-wrap">
                <div className="donut" style={{ background: donut }} role="img" aria-label="Bookings by status donut chart">
                  <span className="donut-hole"><strong>{totalBookings}</strong><small>bookings</small></span>
                </div>
                <ul className="donut-legend">
                  {STATUS_ORDER.map((s) => (
                    <li key={s}>
                      <i className="dot" style={{ background: STATUS_COLORS[s] }} />
                      {s} <strong>{byStatus[s]}</strong>
                      {totalBookings ? <span className="muted"> · {Math.round((byStatus[s] / totalBookings) * 100)}%</span> : null}
                    </li>
                  ))}
                </ul>
              </div>
            </section>
          </div>

          <section className="panel">
            <h3>Occupancy by location — right now</h3>
            <p className="muted small">Derived live from each location&apos;s slots: (reserved + occupied) ÷ total slots.</p>
            <div className="hbar-list">
              {(occByLoc || []).map(({ name, counts }) => {
                const pct = counts && counts.total ? Math.round(((counts.RESERVED + counts.OCCUPIED) / counts.total) * 100) : null;
                return (
                  <div key={name} className="hbar-row">
                    <span className="hbar-label">{name}</span>
                    <span className="hbar-track">
                      <span className="hbar-fill hbar-fill-occ" style={{ width: `${pct ?? 0}%` }} />
                    </span>
                    <strong className="hbar-value">{pct === null ? '—' : `${pct}%`}</strong>
                  </div>
                );
              })}
            </div>
            {dash && (
              <p className="muted small">
                Overall: {Number(dash.occupancyPercent).toFixed(1)}% occupied · {dash.availableSlots} of {dash.totalSlots} slots available.
              </p>
            )}
          </section>

          <section className="panel" style={{ marginTop: 18 }}>
            <h3>When do bookings start? — occupancy heatmap</h3>
            <p className="muted small">
              Count of reservations by start hour and day of week, across all locations (cancelled bookings excluded).
              Darker = more bookings starting in that hour.
            </p>
            <div className="heatmap" role="img" aria-label="Heatmap of reservation start times by hour and day of week">
              <div className="heat-row heat-head">
                <span className="heat-dow" />
                {Array.from({ length: 24 }, (_, h) => (
                  <span key={h} className="heat-hour">{h % 3 === 0 ? fmtHour(h).replace(' ', '') : ''}</span>
                ))}
              </div>
              {DOW_ORDER.map((dow) => (
                <div key={dow} className="heat-row">
                  <span className="heat-dow">{DOW_LABELS[dow]}</span>
                  {Array.from({ length: 24 }, (_, h) => {
                    const v = heat.get(`${dow}-${h}`) || 0;
                    const intensity = v && heatMax ? 0.14 + 0.86 * (v / heatMax) : 0;
                    return (
                      <span
                        key={h}
                        className="heat-cell"
                        style={v ? { background: `rgba(217, 169, 18, ${intensity.toFixed(2)})` } : undefined}
                        title={`${DOW_LABELS[dow]} ${fmtHour(h)} — ${v} booking${v === 1 ? '' : 's'}`}
                      >
                        {v > 0 && intensity > 0.55 ? v : ''}
                      </span>
                    );
                  })}
                </div>
              ))}
            </div>
            <div className="heat-legend">
              <span className="muted small">Less</span>
              <span className="heat-swatch" style={{ background: 'rgba(217,169,18,0.2)' }} />
              <span className="heat-swatch" style={{ background: 'rgba(217,169,18,0.5)' }} />
              <span className="heat-swatch" style={{ background: 'rgba(217,169,18,0.8)' }} />
              <span className="heat-swatch" style={{ background: 'rgba(217,169,18,1)' }} />
              <span className="muted small">More</span>
              {heatMax > 0 && <span className="muted small">· busiest cell: {heatMax} bookings</span>}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
