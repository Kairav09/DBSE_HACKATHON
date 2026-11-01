import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useToast } from '../context/AppContext';
import { fmtDateTime, inr, toIso } from '../lib/format';
import StatusChip from '../components/StatusChip';
import BookingPass from '../components/BookingPass';

const FILTERS = ['', 'RESERVED', 'OCCUPIED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];
const REVIEWED_KEY = 'parkgrid_reviewed_reservations';

function loadReviewed() {
  try {
    return new Set(JSON.parse(localStorage.getItem(REVIEWED_KEY) || '[]'));
  } catch {
    return new Set();
  }
}

function StarInput({ value, onChange }) {
  return (
    <div className="star-input" role="radiogroup" aria-label="Your rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          className={`star-btn ${n <= value ? 'is-on' : ''}`}
          onClick={() => onChange(n)}
          aria-label={`${n} star${n > 1 ? 's' : ''}`}
        >
          <svg width="26" height="26" viewBox="0 0 24 24" fill={n <= value ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 2.8l2.85 5.9 6.45.9-4.7 4.5 1.15 6.4L12 17.5l-5.75 3-1.15-6.4-4.7-4.5 6.45-.9z" />
          </svg>
        </button>
      ))}
    </div>
  );
}

export default function Reservations() {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [acting, setActing] = useState(null);
  const [passFor, setPassFor] = useState(null);

  // v3: reschedule + review + reviewed-tracking
  const [rescheduleFor, setRescheduleFor] = useState(null);
  const [reschedWin, setReschedWin] = useState({ start: '', end: '' });
  const [reschedError, setReschedError] = useState('');
  const [reschedBusy, setReschedBusy] = useState(false);
  const [reviewFor, setReviewFor] = useState(null);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewed, setReviewed] = useState(loadReviewed);

  const load = async () => {
    try {
      setItems(await api.myReservations(filter || undefined));
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { setLoading(true); load(); }, [filter]);

  const markReviewed = (id) => {
    setReviewed((prev) => {
      const next = new Set(prev);
      next.add(id);
      try { localStorage.setItem(REVIEWED_KEY, JSON.stringify([...next])); } catch { /* private mode */ }
      return next;
    });
  };

  const act = async (r, action) => {
    setActing(r.id + action);
    try {
      if (action === 'pay') {
        const p = await api.payReservation(r.id);
        toast(`Payment done (simulated) — ${p.transactionRef || 'PAID'}`);
      } else {
        const fn = { cancel: api.cancelReservation, entry: api.entryReservation, exit: api.exitReservation }[action];
        const updated = await fn(r.id);
        toast(`Reservation #${r.id} is now ${updated.status}`);
      }
      await load();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setActing(null);
    }
  };

  const openReschedule = (r) => {
    setRescheduleFor(r);
    setReschedError('');
    setReschedWin({ start: (r.startTime || '').slice(0, 16), end: (r.endTime || '').slice(0, 16) });
  };

  const submitReschedule = async (e) => {
    e.preventDefault();
    if (!reschedWin.start || !reschedWin.end || reschedWin.end <= reschedWin.start) {
      setReschedError('End time must be after start time.');
      return;
    }
    setReschedBusy(true);
    setReschedError('');
    try {
      const updated = await api.rescheduleReservation(rescheduleFor.id, {
        startTime: toIso(reschedWin.start),
        endTime: toIso(reschedWin.end),
      });
      toast(`Reservation #${updated.id} moved — new fee ${inr(updated.feeAmount)}`);
      setRescheduleFor(null);
      await load();
    } catch (err) {
      // 409 (overlap) and friends are shown in the modal, not swallowed.
      setReschedError(err.message);
    } finally {
      setReschedBusy(false);
    }
  };

  const openReview = (r) => {
    setReviewFor(r);
    setRating(5);
    setComment('');
  };

  const submitReview = async (e) => {
    e.preventDefault();
    setReviewBusy(true);
    try {
      await api.createReview({
        reservationId: reviewFor.id,
        rating,
        comment: comment.trim() || undefined,
      });
      markReviewed(reviewFor.id);
      toast('Thanks — your review is live on the location page.');
      setReviewFor(null);
    } catch (err) {
      if (err.status === 409) {
        // Already reviewed (e.g. from another device) — stop offering the form.
        markReviewed(reviewFor.id);
        setReviewFor(null);
        toast('This booking has already been reviewed.', 'error');
      } else {
        toast(err.message, 'error');
      }
    } finally {
      setReviewBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>My reservations</h2>
          <p className="muted">Check in when you arrive, check out when you leave — and open a pass for the gate.</p>
        </div>
        <div className="filter-row">
          {FILTERS.map((f) => (
            <button key={f || 'ALL'} className={`filter-chip ${filter === f ? 'is-active' : ''}`} onClick={() => setFilter(f)}>
              {f || 'All'}
            </button>
          ))}
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && (
        <div className="card-list" aria-hidden="true">
          <div className="skel skel-card" /><div className="skel skel-card" /><div className="skel skel-card" />
        </div>
      )}
      {!loading && items.length === 0 && (
        <div className="panel empty-state">
          No reservations{filter ? ` with status ${filter}` : ''} yet. <Link to="/locations">Find a parking location</Link> to make your first booking.
        </div>
      )}

      <div className="card-list stagger">
        {items.map((r) => (
          <div key={r.id} className="panel reservation-card">
            <div className="reservation-main">
              <div className="reservation-title">
                <strong>{r.locationName}</strong>
                <span className="slot-badge">Slot {r.slotCode}</span>
                <StatusChip status={r.status} />
                {r.paymentStatus && (
                  <span className={`chip chip-pay-${String(r.paymentStatus).toLowerCase()}`}>
                    {r.paymentStatus === 'PAID' ? 'PAID' : r.paymentStatus === 'PENDING' ? 'PAYMENT PENDING' : r.paymentStatus}
                  </span>
                )}
              </div>
              <p className="muted small">
                {r.licensePlate} · {fmtDateTime(r.startTime)} → {fmtDateTime(r.endTime)}
              </p>
              <p className="muted small">
                Fee <strong className="ink">{inr(r.feeAmount)}</strong>
                {r.entryTime ? ` · Entered ${fmtDateTime(r.entryTime)}` : ''}
                {r.exitTime ? ` · Exited ${fmtDateTime(r.exitTime)}` : ''}
              </p>
            </div>
            <div className="reservation-actions">
              <button className="btn btn-ghost btn-sm" onClick={() => setPassFor(r)}>View pass</button>
              {r.paymentStatus === 'PENDING' && r.status !== 'CANCELLED' && (
                <button className="btn btn-primary btn-sm" disabled={acting !== null} onClick={() => act(r, 'pay')}>
                  {acting === r.id + 'pay' ? '…' : 'Pay now (simulated)'}
                </button>
              )}
              {r.status === 'RESERVED' && (
                <>
                  <button className="btn btn-dark btn-sm" disabled={acting !== null} onClick={() => act(r, 'entry')}>
                    {acting === r.id + 'entry' ? '…' : 'Entry — I have arrived'}
                  </button>
                  <button className="btn btn-ghost btn-sm" disabled={acting !== null} onClick={() => openReschedule(r)}>
                    Reschedule
                  </button>
                  <button className="btn btn-danger-ghost btn-sm" disabled={acting !== null} onClick={() => act(r, 'cancel')}>
                    {acting === r.id + 'cancel' ? '…' : 'Cancel'}
                  </button>
                </>
              )}
              {r.status === 'OCCUPIED' && (
                <button className="btn btn-primary btn-sm" disabled={acting !== null} onClick={() => act(r, 'exit')}>
                  {acting === r.id + 'exit' ? '…' : 'Exit — Check out'}
                </button>
              )}
              {r.status === 'COMPLETED' && !reviewed.has(r.id) && (
                <button className="btn btn-ghost btn-sm" onClick={() => openReview(r)}>
                  Write a review
                </button>
              )}
              {r.status === 'COMPLETED' && reviewed.has(r.id) && (
                <span className="reviewed-note">Reviewed</span>
              )}
            </div>
          </div>
        ))}
      </div>

      {passFor && <BookingPass reservation={passFor} onClose={() => setPassFor(null)} />}

      {rescheduleFor && (
        <div className="modal-backdrop" onClick={() => setRescheduleFor(null)} role="presentation">
          <div className="modal-card form-modal" role="dialog" aria-modal="true" aria-label="Reschedule reservation" onClick={(e) => e.stopPropagation()}>
            <h3>Reschedule reservation #{rescheduleFor.id}</h3>
            <p className="muted small">
              {rescheduleFor.locationName} · Slot {rescheduleFor.slotCode} · {rescheduleFor.licensePlate}.
              The fee is recomputed for the new window (including surge); any promo on the original booking is removed.
            </p>
            <form onSubmit={submitReschedule} className="form">
              <label>
                New start
                <input type="datetime-local" required value={reschedWin.start}
                  onChange={(e) => setReschedWin((w) => ({ ...w, start: e.target.value }))} />
              </label>
              <label>
                New end
                <input type="datetime-local" required value={reschedWin.end}
                  onChange={(e) => setReschedWin((w) => ({ ...w, end: e.target.value }))} />
              </label>
              {reschedError && <div className="alert alert-error">{reschedError}</div>}
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setRescheduleFor(null)}>Keep original</button>
                <button className="btn btn-primary" disabled={reschedBusy}>
                  {reschedBusy ? 'Moving…' : 'Save new time'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {reviewFor && (
        <div className="modal-backdrop" onClick={() => setReviewFor(null)} role="presentation">
          <div className="modal-card form-modal" role="dialog" aria-modal="true" aria-label="Write a review" onClick={(e) => e.stopPropagation()}>
            <h3>Review {reviewFor.locationName}</h3>
            <p className="muted small">
              Slot {reviewFor.slotCode} · {fmtDateTime(reviewFor.startTime)}. One review per completed booking —
              it appears on the location page with your name.
            </p>
            <form onSubmit={submitReview} className="form">
              <div>
                <span className="form-label-text">Your rating</span>
                <StarInput value={rating} onChange={setRating} />
              </div>
              <label>
                Comment <span className="optional">(optional)</span>
                <input
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Easy entry, plenty of space…"
                  maxLength={500}
                />
              </label>
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setReviewFor(null)}>Not now</button>
                <button className="btn btn-primary" disabled={reviewBusy}>
                  {reviewBusy ? 'Posting…' : 'Post review'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
