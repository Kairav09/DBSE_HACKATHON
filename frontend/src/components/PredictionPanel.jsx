import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { fmtHour, todayStr } from '../lib/format';

/**
 * Compact hourly-occupancy prediction for one location.
 * Data: GET /api/predictions/occupancy (backend + Python, via Spring Boot only).
 * Deliberately small — a slim bar strip, not a big chart.
 */
export default function PredictionPanel({ locationId, locationName }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!locationId) return;
    let cancelled = false;
    setLoading(true);
    setError('');
    api.prediction(locationId, todayStr())
      .then((d) => { if (!cancelled) setData(d); })
      .catch((e) => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [locationId]);

  return (
    <section className="panel prediction-panel">
      <div className="panel-head">
        <h3>Expected occupancy today</h3>
        {data && (
          <span className="peak-callout">
            Peak: <strong>{fmtHour(data.peakHour)}</strong> — expect ~{Math.round(data.peakOccupancyPercent)}% full
          </span>
        )}
      </div>
      {loading && <p className="muted">Loading prediction…</p>}
      {!loading && error && <p className="muted">Prediction unavailable right now ({error}).</p>}
      {!loading && data && (
        <>
          <div className="mini-bars" role="img" aria-label={`Hourly predicted occupancy for ${locationName || data.locationName}`}>
            {data.hourlyPredictions.map((h) => (
              <div key={h.hour} className="mini-bar-col" title={`${fmtHour(h.hour)} — ${Math.round(h.predictedOccupancyPercent)}%`}>
                <div
                  className={`mini-bar ${h.hour === data.peakHour ? 'is-peak' : ''}`}
                  style={{ height: `${Math.max(4, h.predictedOccupancyPercent)}%` }}
                />
                <span className="mini-bar-label">{fmtHour(h.hour).replace(' ', '')}</span>
              </div>
            ))}
          </div>
          <p className="muted small">Predicted occupancy by hour for {data.locationName}. Taller = busier.</p>
        </>
      )}
    </section>
  );
}
