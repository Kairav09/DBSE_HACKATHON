// Client-side aggregation over the real endpoints (GET /api/vehicles/my,
// GET /api/reservations/my). Nothing here invents data — every number is
// computed from the arrays those endpoints return.

export function summarizeUserActivity(vehicles, reservations) {
  const list = reservations || [];
  const active = list.filter((r) => r.status === 'RESERVED' || r.status === 'OCCUPIED');
  const totalSpent = list
    .filter((r) => r.status !== 'CANCELLED')
    .reduce((sum, r) => sum + Number(r.feeAmount || 0), 0);
  return {
    vehicleCount: (vehicles || []).length,
    totalBookings: list.length,
    activeBookings: active.length,
    totalSpent,
  };
}

/** The reservation to highlight: an OCCUPIED one first, else the RESERVED one
 *  whose window is nearest (in-progress, then upcoming, then most recent). */
export function pickNextReservation(reservations) {
  const now = Date.now();
  const active = (reservations || []).filter(
    (r) => r.status === 'RESERVED' || r.status === 'OCCUPIED'
  );
  if (!active.length) return null;
  const rank = (r) => {
    const start = new Date(r.startTime).getTime();
    const end = new Date(r.endTime).getTime();
    if (r.status === 'OCCUPIED') return 0; // already parked — most relevant
    if (start <= now && now <= end) return 1; // window is now
    if (start > now) return 2; // upcoming
    return 3; // window passed but never entered
  };
  return [...active].sort((a, b) => {
    const d = rank(a) - rank(b);
    if (d !== 0) return d;
    return new Date(a.startTime) - new Date(b.startTime);
  })[0];
}

export function bookingsByVehicle(reservations) {
  const map = new Map();
  for (const r of reservations || []) {
    map.set(r.vehicleId, (map.get(r.vehicleId) || 0) + 1);
  }
  return map;
}
