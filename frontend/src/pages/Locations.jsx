import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { inr } from '../lib/format';
import Stars from '../components/Stars';

function countStatuses(slots) {
  const counts = { AVAILABLE: 0, RESERVED: 0, OCCUPIED: 0, total: 0 };
  for (const s of slots) {
    if (s.isActive === false) continue;
    counts.total += 1;
    if (counts[s.status] !== undefined) counts[s.status] += 1;
  }
  return counts;
}

export default function Locations() {
  const [locations, setLocations] = useState([]);
  const [counts, setCounts] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const locs = await api.locations();
        if (cancelled) return;
        setLocations(locs);
        const entries = await Promise.all(
          locs.map(async (loc) => {
            try {
              const slots = await api.slotsByLocation(loc.id);
              return [loc.id, countStatuses(slots)];
            } catch {
              return [loc.id, null];
            }
          })
        );
        if (!cancelled) setCounts(Object.fromEntries(entries));
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
          <h2>Parking locations</h2>
          <p className="muted">Pick a location to see the live slot map and book.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && (
        <div className="location-grid" aria-hidden="true">
          <div className="skel skel-loc" /><div className="skel skel-loc" />
        </div>
      )}
      {!loading && !error && locations.length === 0 && <p className="muted">No parking locations yet.</p>}

      <div className="location-grid stagger">
        {locations.map((loc) => {
          const c = counts[loc.id];
          const pct = c && c.total ? Math.round(((c.RESERVED + c.OCCUPIED) / c.total) * 100) : 0;
          return (
            <Link key={loc.id} to={`/locations/${loc.id}`} className="location-card">
              <div className="location-card-top">
                <h3>{loc.name}</h3>
                <span className="rate-badge">{inr(loc.hourlyRate)}<small>/hr</small></span>
              </div>
              <p className="muted">{loc.address}{loc.city ? `, ${loc.city}` : ''}</p>
              {Number(loc.reviewCount) > 0 && loc.averageRating != null && (
                <p className="loc-rating">
                  <Stars value={loc.averageRating} />
                  <strong>{Number(loc.averageRating).toFixed(1)}</strong>
                  <span className="muted small">({loc.reviewCount} review{Number(loc.reviewCount) === 1 ? '' : 's'})</span>
                </p>
              )}
              {c ? (
                <>
                  <div className="occ-bar" aria-hidden="true">
                    <span className="occ-occupied" style={{ width: `${c.total ? (c.OCCUPIED / c.total) * 100 : 0}%` }} />
                    <span className="occ-reserved" style={{ width: `${c.total ? (c.RESERVED / c.total) * 100 : 0}%` }} />
                  </div>
                  <div className="slot-counts">
                    <span><i className="dot dot-available" />{c.AVAILABLE} available</span>
                    <span><i className="dot dot-reserved" />{c.RESERVED} reserved</span>
                    <span><i className="dot dot-occupied" />{c.OCCUPIED} occupied</span>
                    <span className="counts-total">{c.total} slots · {pct}% full</span>
                  </div>
                </>
              ) : (
                <p className="muted small">Live counts unavailable</p>
              )}
              <span className="card-cta">View slot map →</span>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
