import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth, useToast } from '../context/AppContext';
import PredictionPanel from '../components/PredictionPanel';
import {
  VEHICLE_TYPES, VEHICLE_TYPE_LABELS, defaultWindow, estimateFee,
  fmtDateTime, inr, toIso,
} from '../lib/format';
import Stars from '../components/Stars';

const TYPE_ORDER = ['FOUR_WHEELER', 'TWO_WHEELER', 'EV', 'ACCESSIBLE', 'GENERAL'];

export default function LocationDetail() {
  const { id } = useParams();
  const locationId = Number(id);
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const toast = useToast();
  const navigate = useNavigate();

  const [location, setLocation] = useState(null);
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [lastUpdated, setLastUpdated] = useState(null);

  const [selected, setSelected] = useState(null); // slot object
  const [win, setWin] = useState(defaultWindow); // {start, end} datetime-local strings
  const [windowSlotIds, setWindowSlotIds] = useState(null); // Set of slot ids free in window
  const [windowError, setWindowError] = useState('');

  const [vehicles, setVehicles] = useState([]);
  const [vehicleId, setVehicleId] = useState('');
  const [showAddVehicle, setShowAddVehicle] = useState(false);
  const [newVehicle, setNewVehicle] = useState({ licensePlate: '', vehicleType: 'FOUR_WHEELER', model: '', color: '' });

  const [booking, setBooking] = useState(false);
  const [bookError, setBookError] = useState('');
  const [conflict, setConflict] = useState(false);

  // v3: server pricing quote + promo, waitlist, reviews
  const [quote, setQuote] = useState(null);
  const [promoInput, setPromoInput] = useState('');
  const [appliedPromo, setAppliedPromo] = useState('');
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [joiningWaitlist, setJoiningWaitlist] = useState(false);
  const [joinedWaitlist, setJoinedWaitlist] = useState(false);
  const [reviews, setReviews] = useState([]);

  const timerRef = useRef(null);

  const loadSlots = useCallback(async (quiet = false) => {
    try {
      const data = await api.slotsByLocation(locationId);
      setSlots(data);
      setLastUpdated(new Date());
      setSelected((prev) => (prev ? data.find((s) => s.id === prev.id) || prev : prev));
    } catch (e) {
      if (!quiet) setError(e.message);
    }
  }, [locationId]);

  // Initial load: location + slots (+ my vehicles for USER)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const loc = await api.location(locationId);
        if (!cancelled) setLocation(loc);
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
      await loadSlots();
      if (!cancelled) setLoading(false);
      try {
        const rv = await api.locationReviews(locationId);
        if (!cancelled) setReviews(rv || []);
      } catch { /* reviews are an enhancement; the map still works without them */ }
      if (!isAdmin) {
        try {
          const vs = await api.myVehicles();
          if (!cancelled) {
            setVehicles(vs);
            if (vs.length) setVehicleId(String(vs[0].id));
          }
        } catch { /* surfaced later in the panel if needed */ }
      }
    })();
    return () => { cancelled = true; };
  }, [locationId, isAdmin, loadSlots]);

  // Auto-refresh the grid every 15s
  useEffect(() => {
    timerRef.current = setInterval(() => loadSlots(true), 15000);
    return () => clearInterval(timerRef.current);
  }, [loadSlots]);

  // Re-check availability whenever the booking window changes
  useEffect(() => {
    setJoinedWaitlist(false);
    if (!win.start || !win.end || win.end <= win.start) {
      setWindowSlotIds(null);
      setWindowError(win.end && win.start && win.end <= win.start ? 'End time must be after start time.' : '');
      return;
    }
    let cancelled = false;
    setWindowError('');
    api.availability(locationId, toIso(win.start), toIso(win.end))
      .then((list) => { if (!cancelled) setWindowSlotIds(new Set(list.map((s) => s.id))); })
      .catch((e) => { if (!cancelled) { setWindowSlotIds(null); setWindowError(e.message); } });
    return () => { cancelled = true; };
  }, [locationId, win.start, win.end]);

  const grouped = useMemo(() => {
    const groups = new Map();
    for (const s of slots) {
      if (!groups.has(s.vehicleType)) groups.set(s.vehicleType, []);
      groups.get(s.vehicleType).push(s);
    }
    return TYPE_ORDER.filter((t) => groups.has(t)).map((t) => ({ type: t, slots: groups.get(t) }));
  }, [slots]);

  const counts = useMemo(() => {
    const c = { AVAILABLE: 0, RESERVED: 0, OCCUPIED: 0 };
    for (const s of slots) if (s.isActive !== false && c[s.status] !== undefined) c[s.status] += 1;
    return c;
  }, [slots]);

  const fee = location ? estimateFee(win.start, win.end, location.hourlyRate) : null;
  const selectedFreeInWindow = !selected || !windowSlotIds || windowSlotIds.has(selected.id);

  // v3: suggested slot — among slots bookable for the chosen window (free in
  // the window AND free right now, so the panel can actually book it), prefer
  // a type match with the selected vehicle, then the lowest slot code.
  const selectedVehicle = vehicles.find((v) => String(v.id) === String(vehicleId)) || null;
  const suggested = useMemo(() => {
    const pool = slots.filter((s) => {
      if (s.isActive === false || s.status !== 'AVAILABLE') return false;
      return !windowSlotIds || windowSlotIds.has(s.id);
    });
    if (!pool.length) return null;
    return [...pool].sort((a, b) => {
      const am = selectedVehicle && a.vehicleType === selectedVehicle.vehicleType ? 0 : 1;
      const bm = selectedVehicle && b.vehicleType === selectedVehicle.vehicleType ? 0 : 1;
      if (am !== bm) return am - bm;
      return String(a.slotCode).localeCompare(String(b.slotCode), undefined, { numeric: true });
    })[0];
  }, [slots, windowSlotIds, selectedVehicle]);

  const windowValid = Boolean(win.start && win.end && win.end > win.start);
  const windowFull = !isAdmin && windowValid && windowSlotIds !== null && windowSlotIds.size === 0;

  // v3: live server quote (base + surge + promo) for the selected slot/window.
  // Re-fetched whenever slot or window changes; a promo applied earlier is
  // dropped because the price basis changed.
  const selectedId = selected ? selected.id : null;
  useEffect(() => {
    setQuote(null);
    setAppliedPromo('');
    if (!selectedId || !windowValid) return undefined;
    let cancelled = false;
    api.pricingQuote(selectedId, toIso(win.start), toIso(win.end))
      .then((q) => { if (!cancelled) setQuote(q); })
      .catch(() => { if (!cancelled) setQuote(null); }); // local estimate is the fallback display
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, win.start, win.end]);

  const applyPromo = async () => {
    const code = promoInput.trim().toUpperCase();
    if (!code || !selected) return;
    setQuoteLoading(true);
    try {
      const q = await api.pricingQuote(selected.id, toIso(win.start), toIso(win.end), code);
      setQuote(q);
      if (q.promoApplied) {
        setAppliedPromo(code);
        toast(`Promo ${code} applied — you save ${inr(q.discountAmount)}`);
      } else {
        setAppliedPromo('');
        toast(q.promoMessage || 'That promo code does not apply to this booking.', 'error');
      }
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setQuoteLoading(false);
    }
  };

  const joinWaitlist = async () => {
    if (!vehicleId) { toast('Select a vehicle to join the waitlist.', 'error'); return; }
    setJoiningWaitlist(true);
    try {
      await api.joinWaitlist({
        locationId,
        vehicleId: Number(vehicleId),
        startTime: toIso(win.start),
        endTime: toIso(win.end),
      });
      setJoinedWaitlist(true);
      toast('Added to the waitlist — we will notify you if a slot frees up.');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setJoiningWaitlist(false);
    }
  };

  const pickSlot = (slot) => {
    setBookError('');
    setConflict(false);
    if (slot.status !== 'AVAILABLE') {
      setSelected(slot);
      return;
    }
    setSelected(slot);
  };

  const addVehicle = async (e) => {
    e.preventDefault();
    try {
      const v = await api.createVehicle({
        licensePlate: newVehicle.licensePlate.trim().toUpperCase(),
        vehicleType: newVehicle.vehicleType,
        model: newVehicle.model.trim() || undefined,
        color: newVehicle.color.trim() || undefined,
      });
      setVehicles((vs) => [...vs, v]);
      setVehicleId(String(v.id));
      setShowAddVehicle(false);
      setNewVehicle({ licensePlate: '', vehicleType: 'FOUR_WHEELER', model: '', color: '' });
      toast(`Vehicle ${v.licensePlate} added`);
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  const book = async (e) => {
    e.preventDefault();
    if (!selected) return;
    if (!vehicleId) { setBookError('Add or select a vehicle first.'); return; }
    setBooking(true);
    setBookError('');
    setConflict(false);
    try {
      const res = await api.createReservation({
        vehicleId: Number(vehicleId),
        slotId: selected.id,
        startTime: toIso(win.start),
        endTime: toIso(win.end),
        // Only send a promo the quote endpoint actually applied — the server
        // rejects (400) a reservation carrying an invalid code.
        promoCode: appliedPromo || undefined,
      });
      toast(`Booked slot ${res.slotCode} at ${res.locationName} — ${inr(res.feeAmount)}`);
      navigate('/reservations');
    } catch (err) {
      if (err.status === 409) {
        setConflict(true);
        setBookError(err.message);
        loadSlots(true);
      } else {
        setBookError(err.message);
      }
    } finally {
      setBooking(false);
    }
  };

  return (
    <div className="page">
      <Link to="/locations" className="back-link">← All locations</Link>
      {location && (
        <div className="page-head">
          <div>
            <h2>{location.name}</h2>
            <p className="muted">{location.address}{location.city ? `, ${location.city}` : ''} · {inr(location.hourlyRate)}/hr</p>
          </div>
          <div className="head-actions">
            <span className="muted small">{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString('en-IN')}` : ''}</span>
            <button className="btn btn-ghost btn-sm" onClick={() => loadSlots()}>Refresh</button>
          </div>
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p className="muted">Loading slot map…</p>}

      {!loading && (
        <div className="detail-layout">
          <div className="detail-main">
            <div className="legend panel-flat">
              <span><i className="dot dot-available" />Available · {counts.AVAILABLE}</span>
              <span><i className="dot dot-reserved" />Reserved · {counts.RESERVED}</span>
              <span><i className="dot dot-occupied" />Occupied · {counts.OCCUPIED}</span>
              <span className="legend-note">Status is live — derived from current reservations</span>
            </div>

            {grouped.map(({ type, slots: groupSlots }) => (
              <section key={type} className="slot-section">
                <h3 className="slot-section-title">{VEHICLE_TYPE_LABELS[type] || type}</h3>
                <div className="slot-grid">
                  {groupSlots.map((slot) => {
                    const bookedInWindow = windowSlotIds && slot.status === 'AVAILABLE' && !windowSlotIds.has(slot.id);
                    const isSuggested = suggested && slot.id === suggested.id;
                    const cls = [
                      'slot-tile',
                      `slot-${slot.status.toLowerCase()}`,
                      selected?.id === slot.id ? 'is-selected' : '',
                      isSuggested ? 'is-suggested' : '',
                      slot.isActive === false ? 'is-inactive' : '',
                    ].join(' ');
                    return (
                      <button
                        key={slot.id}
                        type="button"
                        className={cls}
                        disabled={slot.isActive === false}
                        onClick={() => pickSlot(slot)}
                        title={slot.isActive === false ? 'Inactive' : `${slot.slotCode} — ${slot.status}${bookedInWindow ? ' (booked in selected window)' : ''}${isSuggested ? ' (suggested for your vehicle & window)' : ''}`}
                      >
                        {isSuggested && <span className="slot-suggest-tag">Suggested</span>}
                        <span className="slot-code">{slot.slotCode}</span>
                        <span className="slot-status-text">
                          {slot.isActive === false ? 'INACTIVE' : bookedInWindow ? 'BOOKED*' : slot.status}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </section>
            ))}
            {windowSlotIds && <p className="muted small">* Booked for the window selected in the booking panel, even if free right now.</p>}

            <PredictionPanel locationId={locationId} locationName={location?.name} />

            <section className="panel reviews-panel">
              <div className="panel-head">
                <h3>Reviews{reviews.length ? ` · ${reviews.length}` : ''}</h3>
                {location && Number(location.reviewCount) > 0 && location.averageRating != null && (
                  <span className="review-summary">
                    <Stars value={location.averageRating} size={15} />
                    <strong>{Number(location.averageRating).toFixed(1)}</strong>
                    <span className="muted small">from {location.reviewCount} review{Number(location.reviewCount) === 1 ? '' : 's'}</span>
                  </span>
                )}
              </div>
              {reviews.length === 0 ? (
                <p className="muted">No reviews yet for this location. Drivers can review a booking after it is completed, from My Reservations.</p>
              ) : (
                <ul className="review-list">
                  {reviews.map((rv) => (
                    <li key={rv.id} className="review-item">
                      <div className="review-head">
                        <Stars value={rv.rating} />
                        <strong>{rv.userName}</strong>
                        <span className="muted small">{fmtDateTime(rv.createdAt)}</span>
                      </div>
                      {rv.comment && <p className="review-comment">{rv.comment}</p>}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <aside className="booking-panel panel">
            {!selected && (
              <>
                <h3>Book a slot</h3>
                <p className="muted">Click any green (available) slot on the map to start booking. Amber and red slots are already taken right now.</p>
                {suggested && !isAdmin && (
                  <div className="suggest-box">
                    <p>
                      <strong>Suggested slot: {suggested.slotCode}</strong>{' '}
                      <span className="muted small">
                        — a suggestion based on {selectedVehicle ? `your ${VEHICLE_TYPE_LABELS[selectedVehicle.vehicleType] || 'vehicle'} type` : 'the chosen window'} and slot order. You can pick any free slot instead.
                      </span>
                    </p>
                    <button type="button" className="btn btn-dark btn-sm" onClick={() => pickSlot(suggested)}>
                      Use suggested slot
                    </button>
                  </div>
                )}
              </>
            )}
            {selected && (
              <>
                <h3>Slot {selected.slotCode}</h3>
                <p className="muted small">{VEHICLE_TYPE_LABELS[selected.vehicleType] || selected.vehicleType} · Now: <strong>{selected.status}</strong></p>

                {isAdmin ? (
                  <div className="alert alert-info">
                    You are signed in as ADMIN. Bookings are made from a driver (USER) account — sign in as
                    user1@example.com / user123 to run the booking flow.
                  </div>
                ) : selected.status !== 'AVAILABLE' ? (
                  <div className="alert alert-info">
                    This slot is {selected.status.toLowerCase()} right now. Pick a green slot, or choose a later
                    window — the map marks slots booked inside your selected window with BOOKED*.
                  </div>
                ) : (
                  <form onSubmit={book} className="form">
                    <label>
                      Start
                      <input type="datetime-local" required value={win.start}
                        onChange={(e) => setWin((w) => ({ ...w, start: e.target.value }))} />
                    </label>
                    <label>
                      End
                      <input type="datetime-local" required value={win.end}
                        onChange={(e) => setWin((w) => ({ ...w, end: e.target.value }))} />
                    </label>
                    {windowError && <div className="alert alert-error">{windowError}</div>}
                    {windowSlotIds && !selectedFreeInWindow && (
                      <div className="alert alert-error">
                        Heads up: slot {selected.slotCode} already has a booking that overlaps this window.
                        Submitting will be rejected — pick another slot or time.
                      </div>
                    )}
                    {quote ? (
                      <div className="quote-box" aria-live="polite">
                        <div className="quote-row">
                          <span>{quote.hours} billable hr{Number(quote.hours) > 1 ? 's' : ''} × {inr(quote.hourlyRate)}</span>
                          <span>{inr(quote.baseAmount)}</span>
                        </div>
                        {Number(quote.surgeMultiplier) > 1 && (
                          <div className="quote-row quote-surge">
                            <span>Surge ×{Number(quote.surgeMultiplier).toFixed(2)}{quote.surgeReason ? ` — ${quote.surgeReason}` : ''}</span>
                            <span>{inr(Number(quote.baseAmount) * Number(quote.surgeMultiplier))}</span>
                          </div>
                        )}
                        {quote.promoApplied && (
                          <div className="quote-row quote-discount">
                            <span>Promo {quote.promoCode} discount</span>
                            <span>−{inr(quote.discountAmount)}</span>
                          </div>
                        )}
                        {!quote.promoApplied && quote.promoMessage && (
                          <p className="quote-msg">{quote.promoMessage}</p>
                        )}
                        <div className="quote-row quote-total">
                          <span>Total</span>
                          <strong>{inr(quote.totalAmount)}</strong>
                        </div>
                        <p className="quote-note">Live price from the server quote — surge follows predicted occupancy at your start hour.</p>
                      </div>
                    ) : fee && (
                      <div className="fee-box">
                        <span>{fee.hours} billable hr{fee.hours > 1 ? 's' : ''} × {inr(location.hourlyRate)}</span>
                        <strong>{inr(fee.fee)}</strong>
                      </div>
                    )}

                    <label>
                      Vehicle
                      <select value={vehicleId} onChange={(e) => setVehicleId(e.target.value)} required>
                        <option value="" disabled>Select a vehicle</option>
                        {vehicles.map((v) => (
                          <option key={v.id} value={v.id}>
                            {v.licensePlate} — {VEHICLE_TYPE_LABELS[v.vehicleType] || v.vehicleType}{v.model ? ` · ${v.model}` : ''}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowAddVehicle((s) => !s)}>
                      {showAddVehicle ? 'Hide add-vehicle form' : '+ Add a vehicle'}
                    </button>

                    <label>
                      Promo code <span className="optional">(optional)</span>
                      <span className="promo-row">
                        <input
                          value={promoInput}
                          onChange={(e) => setPromoInput(e.target.value.toUpperCase())}
                          placeholder="WELCOME10"
                          autoComplete="off"
                        />
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm"
                          onClick={applyPromo}
                          disabled={quoteLoading || !promoInput.trim()}
                        >
                          {quoteLoading ? 'Applying…' : 'Apply'}
                        </button>
                      </span>
                    </label>
                    {appliedPromo && (
                      <p className="promo-applied">Promo {appliedPromo} applied — the discount is included in the total above.</p>
                    )}

                    {bookError && (
                      <div className={`alert ${conflict ? 'alert-conflict' : 'alert-error'}`}>
                        {conflict && <strong>Slot just got booked for that window. </strong>}
                        {bookError}
                      </div>
                    )}
                    <button className="btn btn-primary btn-block" disabled={booking}>
                      {booking ? 'Booking…' : `Book slot ${selected.slotCode}`}
                    </button>
                  </form>
                )}

                {showAddVehicle && !isAdmin && (
                  <form onSubmit={addVehicle} className="form vehicle-mini-form">
                    <label>
                      License plate
                      <input required value={newVehicle.licensePlate}
                        onChange={(e) => setNewVehicle((v) => ({ ...v, licensePlate: e.target.value }))}
                        placeholder="TS09AB1234" />
                    </label>
                    <label>
                      Type
                      <select value={newVehicle.vehicleType}
                        onChange={(e) => setNewVehicle((v) => ({ ...v, vehicleType: e.target.value }))}>
                        {VEHICLE_TYPES.map((t) => <option key={t} value={t}>{VEHICLE_TYPE_LABELS[t]}</option>)}
                      </select>
                    </label>
                    <label>
                      Model <span className="optional">(optional)</span>
                      <input value={newVehicle.model}
                        onChange={(e) => setNewVehicle((v) => ({ ...v, model: e.target.value }))} placeholder="Hyundai Creta" />
                    </label>
                    <label>
                      Colour <span className="optional">(optional)</span>
                      <input value={newVehicle.color}
                        onChange={(e) => setNewVehicle((v) => ({ ...v, color: e.target.value }))} placeholder="White" />
                    </label>
                    <button className="btn btn-dark btn-block">Add vehicle</button>
                  </form>
                )}
              </>
            )}

            {windowFull && location && (
              <div className="waitlist-box">
                <h3>No slots free for this window</h3>
                <p className="muted small">
                  Every slot at {location.name} is booked for{' '}
                  <strong className="ink">{fmtDateTime(toIso(win.start))} → {fmtDateTime(toIso(win.end))}</strong>.
                  Join the waitlist instead — if a booking for this window is cancelled, the earliest matching
                  entry is offered the freed slot and gets about 15 minutes to claim it.
                </p>
                {joinedWaitlist ? (
                  <div className="alert alert-success">
                    You are on the waitlist for this window. <Link to="/waitlist">View my waitlist →</Link>
                  </div>
                ) : vehicles.length === 0 ? (
                  <p className="muted small">
                    You need a registered vehicle to join. <Link to="/vehicles">Add one under My Vehicles</Link>, then come back.
                  </p>
                ) : (
                  <>
                    <label className="waitlist-vehicle">
                      Vehicle
                      <select value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
                        {vehicles.map((v) => (
                          <option key={v.id} value={v.id}>
                            {v.licensePlate} — {VEHICLE_TYPE_LABELS[v.vehicleType] || v.vehicleType}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button type="button" className="btn btn-primary btn-block" onClick={joinWaitlist} disabled={joiningWaitlist || !vehicleId}>
                      {joiningWaitlist ? 'Joining…' : 'Join the waitlist'}
                    </button>
                  </>
                )}
              </div>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}
