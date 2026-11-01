import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { fmtDateTime, inr } from '../lib/format';
import StatusChip from './StatusChip';

/**
 * Parking-ticket style pass for one reservation. The QR image is the REAL
 * code served by GET /api/reservations/{id}/qr (fetched as a blob with the
 * JWT). If that fetch fails, we fall back to the decorative stripe pattern —
 * which by itself is not a scannable code. Payment is simulated in this app;
 * the pass says so wherever a payment state is shown.
 */
export default function BookingPass({ reservation, onClose }) {
  const [qrUrl, setQrUrl] = useState(null);
  const [payment, setPayment] = useState(null);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    if (!reservation) return undefined;
    let cancelled = false;
    let objectUrl = null;
    (async () => {
      try {
        const blob = await api.reservationQr(reservation.id);
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setQrUrl(objectUrl);
      } catch { if (!cancelled) setQrUrl(null); }
      try {
        const p = await api.reservationPayment(reservation.id);
        if (!cancelled) setPayment(p);
      } catch { if (!cancelled) setPayment(null); }
    })();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [reservation]);

  if (!reservation) return null;
  const r = reservation;
  const payStatus = payment?.status || r.paymentStatus || null;
  const txnRef = payment?.transactionRef || null;

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="modal-card pass-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`Booking pass for reservation ${r.id}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="booking-pass">
          <div className="pass-top">
            <div>
              <p className="pass-brand">PARKGRID</p>
              <p className="pass-type">PARKING PASS</p>
            </div>
            <StatusChip status={r.status} />
          </div>
          <h3 className="pass-location">{r.locationName}</h3>
          <div className="pass-slot-row">
            <span className="pass-slot">{r.slotCode}</span>
            <span className="pass-plate">{r.licensePlate}</span>
          </div>
          <dl className="pass-grid">
            <div><dt>Reservation</dt><dd>#{r.id}</dd></div>
            <div><dt>Fee</dt><dd>{inr(r.feeAmount)}</dd></div>
            <div><dt>From</dt><dd>{fmtDateTime(r.startTime)}</dd></div>
            <div><dt>Until</dt><dd>{fmtDateTime(r.endTime)}</dd></div>
            {r.entryTime && <div><dt>Entered</dt><dd>{fmtDateTime(r.entryTime)}</dd></div>}
            {r.exitTime && <div><dt>Exited</dt><dd>{fmtDateTime(r.exitTime)}</dd></div>}
            {payStatus && (
              <div>
                <dt>Payment</dt>
                <dd>
                  {payStatus}
                  {payStatus === 'PAID' && <span className="muted small"> (simulated)</span>}
                </dd>
              </div>
            )}
            {txnRef && <div><dt>Transaction</dt><dd>{txnRef}</dd></div>}
          </dl>
          <div className="pass-tear" aria-hidden="true" />
          {qrUrl ? (
            <div className="pass-qr">
              <img src={qrUrl} alt={`QR code for reservation ${r.id}`} width="168" height="168" />
              <p className="pass-note">Scan at the gate. Reservation #{r.id} · Slot {r.slotCode}</p>
            </div>
          ) : (
            <>
              <div className="pass-stripes" aria-hidden="true" />
              <p className="pass-note">Show this pass at the gate. Reservation #{r.id} · Slot {r.slotCode}</p>
            </>
          )}
        </div>
        <div className="pass-actions">
          <button className="btn btn-ghost btn-sm" onClick={onClose}>Close</button>
          <button className="btn btn-primary btn-sm" onClick={() => window.print()}>Print pass</button>
        </div>
      </div>
    </div>
  );
}
