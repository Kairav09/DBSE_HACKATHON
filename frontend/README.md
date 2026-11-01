# Frontend — React (Vite)

**ParkGrid** — the Smart Parking Management System UI · CODESPARK Hackathon 2026 · Problem #01

React 18 + Vite 5 + React Router 6, plain JavaScript, hand-rolled CSS (no UI framework). The frontend talks **only** to the Spring Boot backend (`http://localhost:8080`) — never to MySQL or the Python service directly.

## Run

Prerequisites: Node 18+ and the backend running (see `../backend/README.md` — the `dev` profile is the fastest way).

```bash
cd frontend
npm install
npm run dev
```

App URL: **http://localhost:5173** (the backend's CORS is configured for exactly this origin).

Production build check:

```bash
npm run build
```

### Configuration

| Env var | Default | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend base URL. Copy `.env.example` to `.env` to override. |

## Demo accounts (seeded by the backend)

| Role | Email | Password |
|---|---|---|
| USER | `user1@example.com` | `user123` |
| USER | `user2@example.com` | `user123` |
| ADMIN | `admin@smartparking.local` | `admin123` |

The login screen also has one-click demo fill buttons, and the landing page has a “For judges” section with the same accounts.

## Pages

| Route | Who | What |
|---|---|---|
| `/` | Public | **Landing page** — hero with an illustrated slot-map mock (labelled as an illustration; real availability needs the auth-protected API), feature grid (live slot map, no-double-booking, surge pricing & promo codes, waitlist, QR pass, reviews, notifications, peak-hour prediction), how-it-works, USER vs ADMIN roles, a sample prediction strip (labelled sample data), and a judges/demo section. Signed-in visitors get “Open app” CTAs. Scroll-reveal animations throughout. |
| `/login`, `/register` | Public | Split-layout auth pages: brand panel + form, show/hide password, inline validation (email format, name required, password ≥ 6 — mirroring the backend rules). Registration always creates a USER and returns to login with a notice. Session is kept in `localStorage`; any `401` from the API auto-logs-out to `/login`. Login lands USER → `/home`, ADMIN → `/admin`. |
| `/home` | USER | **User dashboard** — greeting, count-up stat tiles (vehicles, total bookings, active bookings, total spent — aggregated client-side from `/api/vehicles/my` + `/api/reservations/my`), next/active booking highlight with Entry/Exit quick actions, quick-action cards, recent bookings. |
| `/locations` | USER, ADMIN | Location cards with hourly rate, live available/reserved/occupied counts (skeleton loaders while fetching), and — when the location has reviews — its average rating (stars) + review count from the location response. |
| `/locations/:id` | USER, ADMIN | **Slot map** — slots grouped by vehicle type, tiles colour-coded by live derived status (green AVAILABLE / amber RESERVED / red OCCUPIED), auto-refresh every 15 s + manual Refresh. Clicking a slot opens the booking panel: window pickers re-check the backend availability endpoint, vehicles can be added inline, and a backend `409` overlap is shown as a clear “slot just got booked” message. **v3:** the price comes from the server quote (`GET /api/pricing/quote`) — billable hours × rate, surge multiplier + reason when predicted occupancy is high, promo discount line; a promo code can be applied (only a quote-applied code is sent on create, since the server `400`s invalid ones). A **Suggested slot** (vehicle-type match first, then lowest slot code, among slots bookable for the window) is tagged on the grid. If the chosen window has **zero** available slots, a **Join the waitlist** panel replaces the dead end. Below the prediction panel: a **Reviews** section (average rating + newest-first list from `GET /api/locations/{id}/reviews`). |
| `/vehicles` | USER | My Vehicles — plate-styled cards with type badge and per-vehicle booking count, add form (plate auto-uppercased, validated), **edit via `PUT /api/vehicles/{id}`**, and two-click confirm delete (the server still rejects deleting a vehicle that has reservations). |
| `/reservations` | USER | My Reservations — status filters (incl. NO_SHOW), Cancel / Entry / Exit actions per status, a booking-pass modal, and **v3:** a `paymentStatus` chip with **Pay now** while PENDING (payments are simulated — labelled as such; paying shows the transaction ref), **Reschedule** for RESERVED bookings (datetime modal → `PUT /api/reservations/{id}`, `409`s surfaced in the modal), and **Write a review** for COMPLETED bookings (1–5 stars + comment, once per booking; the button hides after reviewing). |
| `/waitlist` | USER | My Waitlist — entries as cards with status chips; an OFFERED entry shows its held slot + **Claim** button and the ~15-minute expiry note; WAITING/OFFERED entries can be cancelled. Joined from the booking panel when a window is fully booked. |
| `/notifications` | USER, ADMIN | Full notification list (booking, payment, waitlist offer, no-show, cancellation), unread highlighted; the nav **bell** shows the unread count (polled ~30 s and on route change), a dropdown of the latest, mark-read on click (waitlist offers route to `/waitlist`), and mark-all-read. |
| `/profile` | USER, ADMIN | Read-only profile from `GET /api/auth/me` (the API has no profile-edit endpoint), activity stats for users, session info, sign out. |
| `/help` | USER, ADMIN | How booking works: slot lifecycle diagram, the fee rule with a worked example, and an animated FAQ accordion (409 conflicts, surge pricing, promo codes, waitlist + claim expiry, the simulated-payment honesty note, the one-review-per-completed-booking rule, no-shows, cancel rules, entry/exit, prediction fallback, judge demo accounts). |
| `/admin` | ADMIN | Dashboard stat tiles from `GET /api/admin/dashboard` + compact prediction chart per location, with links to management, analytics and all reservations. |
| `/admin/analytics` | ADMIN | Revenue by location and bookings-by-status (compact donut) aggregated from the full admin reservation list, plus live occupancy by location. **v3:** an occupancy **heatmap** (bookings by start hour × day of week, cancelled excluded, small cells + legend) and an **Export CSV** button (`GET /api/admin/reservations/export`, downloaded as `reservations.csv`). Small charts only, by design. |
| `/admin/locations` | ADMIN | Create/edit/delete locations; add/delete/activate/deactivate slots per location. |
| `/admin/reservations` | ADMIN | All reservations with location / status / date filters. |
| `/admin/promos` | ADMIN | Promo codes — list with discount, used/max-uses, expiry and status; create form (code, PERCENT/FLAT, value, max uses, expiry); deactivate and two-click delete. |
| `/admin/waitlist` | ADMIN | All waitlist entries across locations (plate, requested window, offered slot, status), newest first. |
| *(unknown)* | — | Logged-in users are redirected to their role home; logged-out visitors get a simple 404 page. |

## Motion & polish

- Page transitions (fade + rise on route change), staggered card entrances, scroll-reveal on the landing page, count-up numbers on the user dashboard, skeleton shimmer loaders, animated modal / FAQ accordion / toasts, hover lift on cards, `prefers-reduced-motion` respected.
- Favicon: `public/favicon.svg` (yellow “P” bay mark on ink), referenced from `index.html` with a meta description and theme colour.

Notes:

- The booking pass shows the **real QR image** from `GET /api/reservations/{id}/qr` (fetched as a blob with the JWT) plus the payment status/transaction ref from `GET /api/reservations/{id}/payment`. If the QR fetch fails it falls back to the decorative stripe pattern (not scannable) — the stripes alone are never presented as a code.
- **Payments are simulated** (`SIMULATED_CARD`, generated transaction refs) and the UI labels them as such wherever a payment is made or shown. No real money moves.
- One contract ambiguity, decided frontend-side: `docs/API_V3_CONTRACT.md` names the `/admin/promos` page but does not pin the admin promo REST paths or DTO field names. The UI uses the codebase's `/api/admin/**` conventions — `GET/POST /api/admin/promos`, `POST /api/admin/promos/{id}/deactivate`, `DELETE /api/admin/promos/{id}` — and reads promo fields defensively (`type|discountType`, `expiresAt|expiry|expiryDate`, `active|isActive`). If the backend ships different names, only `src/lib/api.js` + the field helpers in `AdminPromos.jsx` need adjusting.
- Booking and vehicle management are **USER-role endpoints on the backend**, so those nav items are shown to users; admins get the admin views instead. Signed in as ADMIN, the slot-map booking panel explains this rather than failing.
- Slot status is **derived on the backend** from reservations — the UI never stores or invents it.
- Design: asphalt/ink neutrals with a road-marking yellow accent and a green/amber/red status system that matches the slot tiles; Archivo display type + Inter body. No icon fonts/emoji — icons are inline SVG.
