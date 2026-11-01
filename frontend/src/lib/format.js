// Formatting helpers shared across pages.

export const VEHICLE_TYPES = ['FOUR_WHEELER', 'TWO_WHEELER', 'EV', 'ACCESSIBLE', 'GENERAL'];

export const VEHICLE_TYPE_LABELS = {
  FOUR_WHEELER: 'Four Wheeler',
  TWO_WHEELER: 'Two Wheeler',
  EV: 'EV Charging',
  ACCESSIBLE: 'Accessible',
  GENERAL: 'General',
};

export function inr(value) {
  const n = Number(value ?? 0);
  return '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: n % 1 === 0 ? 0 : 2 });
}

/** "2026-10-09T09:00:00" -> "9 Oct 2026, 9:00 AM" */
export function fmtDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: 'numeric', minute: '2-digit', hour12: true,
  });
}

export function fmtTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('en-IN', { hour: 'numeric', minute: '2-digit', hour12: true });
}

/** 17 -> "5 PM", 8 -> "8 AM" */
export function fmtHour(h) {
  const hour = ((h + 11) % 12) + 1;
  return `${hour} ${h >= 12 && h < 24 ? 'PM' : 'AM'}`;
}

/** Value for a datetime-local input, from a Date. */
export function toLocalInput(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Default booking window: next full hour -> +2 hours. */
export function defaultWindow() {
  const start = new Date();
  start.setMinutes(0, 0, 0);
  start.setHours(start.getHours() + 1);
  const end = new Date(start.getTime() + 2 * 3600 * 1000);
  return { start: toLocalInput(start), end: toLocalInput(end) };
}

/** Backend fee rule: billable hours = ceil(duration), minimum 1, x hourly rate. */
export function estimateFee(startStr, endStr, hourlyRate) {
  const start = new Date(startStr);
  const end = new Date(endStr);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) return null;
  const hours = Math.max(1, Math.ceil((end - start) / 3600000));
  return { hours, fee: hours * Number(hourlyRate) };
}

/** datetime-local value -> ISO string the backend accepts ("2026-10-09T09:00:00"). */
export function toIso(localValue) {
  return localValue && localValue.length === 16 ? `${localValue}:00` : localValue;
}

export function todayStr() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
