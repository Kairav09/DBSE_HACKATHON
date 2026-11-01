// Central API client for the Spring Boot backend.
// - Base URL from VITE_API_BASE_URL (default http://localhost:8080)
// - Attaches the JWT from localStorage
// - On 401: clears the session and bounces to /login
// - Throws Error with the backend's `message` field (see docs/API.md error shape)

const BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export const TOKEN_KEY = 'parkgrid_token';
export const USER_KEY = 'parkgrid_user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}
export function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}
export function saveSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    const err = new Error('Cannot reach the backend. Is it running on ' + BASE + '?');
    err.status = 0;
    throw err;
  }

  if (res.status === 204) return null;

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }

  if (!res.ok) {
    if (res.status === 401 && auth) {
      clearSession();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login');
      }
    }
    const err = new Error(data?.message || `${res.status} ${res.statusText}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

/** Binary download (QR PNG, CSV export) with the JWT attached. */
async function requestBlob(path) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(`${BASE}${path}`, { headers });
  } catch {
    const err = new Error('Cannot reach the backend. Is it running on ' + BASE + '?');
    err.status = 0;
    throw err;
  }
  if (!res.ok) {
    if (res.status === 401) {
      clearSession();
      if (!window.location.pathname.startsWith('/login')) window.location.assign('/login');
    }
    let message = `${res.status} ${res.statusText}`;
    try {
      const data = await res.json();
      if (data?.message) message = data.message;
    } catch { /* binary endpoints return plain errors */ }
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }
  return res.blob();
}

function qs(params) {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params || {})) {
    if (v !== undefined && v !== null && v !== '') q.set(k, v);
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

export const api = {
  // Auth
  register: (payload) => request('/api/auth/register', { method: 'POST', body: payload, auth: false }),
  login: (payload) => request('/api/auth/login', { method: 'POST', body: payload, auth: false }),
  me: () => request('/api/auth/me'),

  // Locations
  locations: () => request('/api/locations'),
  location: (id) => request(`/api/locations/${id}`),
  createLocation: (payload) => request('/api/locations', { method: 'POST', body: payload }),
  updateLocation: (id, payload) => request(`/api/locations/${id}`, { method: 'PUT', body: payload }),
  deleteLocation: (id) => request(`/api/locations/${id}`, { method: 'DELETE' }),

  // Slots
  slotsByLocation: (locationId) => request(`/api/locations/${locationId}/slots`),
  availability: (locationId, startTime, endTime) =>
    request(`/api/slots/availability?locationId=${locationId}&startTime=${encodeURIComponent(startTime)}&endTime=${encodeURIComponent(endTime)}`),
  createSlot: (locationId, payload) => request(`/api/locations/${locationId}/slots`, { method: 'POST', body: payload }),
  updateSlot: (id, payload) => request(`/api/slots/${id}`, { method: 'PUT', body: payload }),
  deleteSlot: (id) => request(`/api/slots/${id}`, { method: 'DELETE' }),

  // Vehicles (USER role only on the backend)
  myVehicles: () => request('/api/vehicles/my'),
  createVehicle: (payload) => request('/api/vehicles', { method: 'POST', body: payload }),
  updateVehicle: (id, payload) => request(`/api/vehicles/${id}`, { method: 'PUT', body: payload }),
  deleteVehicle: (id) => request(`/api/vehicles/${id}`, { method: 'DELETE' }),

  // Reservations
  createReservation: (payload) => request('/api/reservations', { method: 'POST', body: payload }),
  myReservations: (status) =>
    request(`/api/reservations/my${status ? `?status=${status}` : ''}`),
  cancelReservation: (id) => request(`/api/reservations/${id}/cancel`, { method: 'POST' }),
  entryReservation: (id) => request(`/api/reservations/${id}/entry`, { method: 'POST' }),
  exitReservation: (id) => request(`/api/reservations/${id}/exit`, { method: 'POST' }),
  adminReservations: ({ locationId, status, date } = {}) => {
    const q = new URLSearchParams();
    if (locationId) q.set('locationId', locationId);
    if (status) q.set('status', status);
    if (date) q.set('date', date);
    const qs = q.toString();
    return request(`/api/reservations${qs ? `?${qs}` : ''}`);
  },

  // Admin + prediction
  dashboard: () => request('/api/admin/dashboard'),
  prediction: (locationId, date) =>
    request(`/api/predictions/occupancy?locationId=${locationId}${date ? `&date=${date}` : ''}`),

  // ---- v3: pricing & promo ----
  pricingQuote: (slotId, startTime, endTime, promoCode) =>
    request(`/api/pricing/quote${qs({ slotId, startTime, endTime, promoCode })}`),

  // ---- v3: payments (simulated) ----
  reservationPayment: (id) => request(`/api/reservations/${id}/payment`),
  payReservation: (id) => request(`/api/reservations/${id}/pay`, { method: 'POST' }),
  reservationQr: (id) => requestBlob(`/api/reservations/${id}/qr`),

  // ---- v3: reschedule ----
  rescheduleReservation: (id, payload) => request(`/api/reservations/${id}`, { method: 'PUT', body: payload }),

  // ---- v3: reviews ----
  createReview: (payload) => request('/api/reviews', { method: 'POST', body: payload }),
  locationReviews: (locationId) => request(`/api/locations/${locationId}/reviews`),

  // ---- v3: notifications ----
  notifications: () => request('/api/notifications'),
  unreadCount: () => request('/api/notifications/unread-count'),
  markNotificationRead: (id) => request(`/api/notifications/${id}/read`, { method: 'POST' }),
  markAllNotificationsRead: () => request('/api/notifications/read-all', { method: 'POST' }),

  // ---- v3: waitlist ----
  joinWaitlist: (payload) => request('/api/waitlist', { method: 'POST', body: payload }),
  myWaitlist: () => request('/api/waitlist/my'),
  cancelWaitlist: (id) => request(`/api/waitlist/${id}/cancel`, { method: 'POST' }),
  claimWaitlist: (id) => request(`/api/waitlist/${id}/claim`, { method: 'POST' }),
  adminWaitlist: () => request('/api/admin/waitlist'),

  // ---- v3: admin promos ----
  // NOTE: the v3 contract names the /admin/promos page but does not pin the
  // admin promo REST paths/DTO field names; these follow the codebase's
  // /api/admin/** conventions. Reads are defensive about field variants.
  adminPromos: () => request('/api/admin/promos'),
  createPromo: (payload) => request('/api/admin/promos', { method: 'POST', body: payload }),
  deactivatePromo: (id) => request(`/api/admin/promos/${id}/deactivate`, { method: 'POST' }),

  // ---- v3: admin CSV export ----
  exportReservationsCsv: (filters = {}) =>
    requestBlob(`/api/admin/reservations/export${qs(filters)}`),
};
