# API — Smart Parking Management System

REST API of the Spring Boot backend — **48 endpoints** (§1–§7 are the v1/v2 core; §8–§14 are the v3 expansion: pricing, payments, waitlist, notifications, reviews, QR pass, admin extras). **Base URL:** `http://localhost:8080` · All paths below are relative to it. All request/response bodies are JSON.

Field names in responses use the same names as `database/schema.sql`, converted to camelCase (e.g. DB `slot_code` → JSON `slotCode`, DB `start_time` → JSON `startTime`). Table and column names referenced below are the exact names from `database/schema.sql`.

## Conventions

- **Auth:** `POST /api/auth/login` returns a token. Send it on every protected call as `Authorization: Bearer <token>`.
- **Roles:** `Public` = no token needed · `USER` / `ADMIN` = token with that role from `users.role`. "Owner" means the resource's `user_id` equals the logged-in user.
- **Timestamps:** ISO-8601 strings, e.g. `"2026-10-09T09:00:00"`.
- **IDs:** JSON `id` maps to the table's `id` column.
- **Errors:** every error returns the same shape:

```json
{ "timestamp": "2026-10-09T10:15:30", "status": 409, "error": "Conflict", "message": "Slot A-01 is already booked for the selected time window", "path": "/api/reservations" }
```

| HTTP status | Used for |
|---|---|
| 400 Bad Request | Validation failure (e.g. `endTime` not after `startTime`, missing/blank field) |
| 401 Unauthorized | Missing/invalid token, wrong login credentials |
| 403 Forbidden | Logged in but wrong role, or not the owner of the resource |
| 404 Not Found | Location, slot, vehicle, or reservation id does not exist |
| 409 Conflict | Overlapping reservation; illegal status transition (e.g. entry on a CANCELLED reservation); duplicate `email` or `license_plate` |

---

## 1. Auth

### POST /api/auth/register — Public

Request:
```json
{ "fullName": "Kairav Vashi", "email": "kairav@example.com", "password": "secret123", "phone": "9876543210" }
```
Response `201 Created` (`role` is always `USER` for self-registration; ADMIN accounts are seeded/created directly):
```json
{ "id": 1, "fullName": "Kairav Vashi", "email": "kairav@example.com", "phone": "9876543210", "role": "USER", "createdAt": "2026-10-09T08:05:00" }
```
Maps to `users` (`full_name`, `email`, `password_hash` — the hash is never returned, `phone`, `role`, `created_at`).

### POST /api/auth/login — Public

Request:
```json
{ "email": "kairav@example.com", "password": "secret123" }
```
Response `200 OK`:
```json
{ "token": "<jwt>", "user": { "id": 1, "fullName": "Kairav Vashi", "email": "kairav@example.com", "role": "USER" } }
```

### GET /api/auth/me — USER, ADMIN

Response `200 OK`: the current user object (same shape as the register response). `401` if the token is missing/invalid.

---

## 2. Parking Locations — `parking_locations`

Location object:
```json
{ "id": 1, "name": "KL University Main Gate Parking", "address": "Bachupally Campus, Main Gate", "city": "Hyderabad", "hourlyRate": 30.00, "isActive": true, "averageRating": 4.5, "reviewCount": 2, "createdAt": "2026-10-09T08:10:00", "updatedAt": "2026-10-09T08:10:00" }
```
(v3) `averageRating` / `reviewCount` are computed from the `reviews` table (through the location's reservations): the average of `reviews.rating` rounded to 1 decimal, or `null` when the location has no reviews yet; `reviewCount` is the number of reviews (0 when none).

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/locations` | USER, ADMIN | List all locations; only `is_active = 1` ones for USER |
| GET | `/api/locations/{id}` | USER, ADMIN | One location; `404` if missing |
| POST | `/api/locations` | ADMIN | Create; `201` |
| PUT | `/api/locations/{id}` | ADMIN | Update name/address/city/hourly rate/active flag |
| DELETE | `/api/locations/{id}` | ADMIN | Delete (its slots cascade; blocked with `409` if the location's slots have reservations — deactivate via `isActive` instead) |

Create/update request:
```json
{ "name": "KL University Main Gate Parking", "address": "Bachupally Campus, Main Gate", "city": "Hyderabad", "hourlyRate": 30.00, "isActive": true }
```

---

## 3. Parking Slots — `parking_slots`

Slot object (includes the **derived** live status — there is no status column in the DB):
```json
{ "id": 11, "locationId": 1, "slotCode": "A-01", "vehicleType": "FOUR_WHEELER", "isActive": true, "status": "AVAILABLE" }
```
`status` ∈ `AVAILABLE | RESERVED | OCCUPIED`, derived from `reservations` as defined in `docs/ARCHITECTURE.md` §4. `vehicleType` ∈ `TWO_WHEELER | FOUR_WHEELER | EV | ACCESSIBLE | GENERAL` (DB column `vehicle_type`).

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/locations/{locationId}/slots` | USER, ADMIN | All slots for a location, each with its live derived `status`. This powers the slot map. |
| GET | `/api/slots/{id}` | USER, ADMIN | One slot with live `status` |
| GET | `/api/slots/availability?locationId={id}&startTime={iso}&endTime={iso}` | USER, ADMIN | Slots of the location with **no overlapping** `RESERVED`/`OCCUPIED` reservation in the given window |
| POST | `/api/locations/{locationId}/slots` | ADMIN | Create a slot under a location; `409` if `slotCode` already exists in that location |
| PUT | `/api/slots/{id}` | ADMIN | Update `slotCode` / `vehicleType` / `isActive` |
| DELETE | `/api/slots/{id}` | ADMIN | Delete; `409` if the slot has reservations (deactivate instead) |

Create/update request:
```json
{ "slotCode": "A-01", "vehicleType": "FOUR_WHEELER", "isActive": true }
```

---

## 4. Vehicles — `vehicles`

Vehicle object:
```json
{ "id": 5, "userId": 1, "licensePlate": "TS09AB1234", "vehicleType": "FOUR_WHEELER", "model": "Hyundai Creta", "color": "White", "createdAt": "2026-10-09T08:20:00", "updatedAt": "2026-10-09T08:20:00" }
```

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/vehicles/my` | USER | Only the logged-in user's vehicles |
| POST | `/api/vehicles` | USER | Create for the logged-in user; `409` if `licensePlate` already exists |
| GET | `/api/vehicles/{id}` | USER (owner), ADMIN | `403` if a USER asks for someone else's vehicle |
| PUT | `/api/vehicles/{id}` | USER (owner) | Update type/model/colour (plate changes re-checked for uniqueness) |
| DELETE | `/api/vehicles/{id}` | USER (owner) | Delete; `409` if the vehicle has reservations |

Create/update request:
```json
{ "licensePlate": "TS09AB1234", "vehicleType": "FOUR_WHEELER", "model": "Hyundai Creta", "color": "White" }
```

---

## 5. Reservations — `reservations`

Reservation object:
```json
{
  "id": 21, "userId": 1, "vehicleId": 5, "slotId": 11, "locationId": 1,
  "licensePlate": "TS09AB1234", "slotCode": "A-01", "locationName": "KL University Main Gate Parking",
  "startTime": "2026-10-09T09:00:00", "endTime": "2026-10-09T12:00:00",
  "status": "RESERVED", "feeAmount": 90.00,
  "baseAmount": 90.00, "pricingMultiplier": 1.00, "discountAmount": 0.00, "promoCode": null,
  "paymentStatus": "PENDING",
  "entryTime": null, "exitTime": null,
  "createdAt": "2026-10-09T08:25:00", "updatedAt": "2026-10-09T08:25:00"
}
```
`status` ∈ `RESERVED | OCCUPIED | COMPLETED | CANCELLED | NO_SHOW` (DB column `status`; `NO_SHOW` added in v3 — see §13). (v3) `baseAmount` = billable hours × hourly rate before surge; `feeAmount` remains the **final** amount = `baseAmount × pricingMultiplier − discountAmount` (floor 0); `promoCode` is the applied code or `null`; `paymentStatus` mirrors the reservation's row in `payments` (`PENDING | PAID | REFUNDED`), or `null` if none exists. `locationId` is a read-only convenience field resolved through the slot's `location_id` — there is no `location_id` column on `reservations`. `licensePlate`, `slotCode`, and `locationName` are likewise read-only display fields joined in for the UI.

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/api/reservations` | USER | Create a reservation (flow in `docs/ARCHITECTURE.md` §3) |
| GET | `/api/reservations/my` | USER | The logged-in user's reservations, newest first; optional `?status=` filter |
| GET | `/api/reservations/{id}` | USER (owner), ADMIN | One reservation |
| POST | `/api/reservations/{id}/cancel` | USER (owner), ADMIN | Only from `RESERVED` → `CANCELLED`; otherwise `409` |
| POST | `/api/reservations/{id}/entry` | USER (owner), ADMIN | Only from `RESERVED` → `OCCUPIED`; sets `entryTime` to now |
| POST | `/api/reservations/{id}/exit` | USER (owner), ADMIN | Only from `OCCUPIED` → `COMPLETED`; sets `exitTime` to now and finalises `feeAmount` |
| PUT | `/api/reservations/{id}` | USER (owner) | (v3) Reschedule — see §9 |
| GET | `/api/reservations?locationId=&status=&date=` | ADMIN | All reservations with optional filters (admin list view) |

Create request:
```json
{ "vehicleId": 5, "slotId": 11, "startTime": "2026-10-09T09:00:00", "endTime": "2026-10-09T12:00:00", "promoCode": "WELCOME10" }
```
(v3) `promoCode` is optional. The server recomputes the whole price itself (base + surge − discount, §8) and **ignores any client-sent amounts**; an invalid/expired/exhausted promo on create is a hard `400`. A successful create also writes a `PENDING` row in `payments` (amount = `feeAmount`) and a `BOOKING_CONFIRMED` notification.
Create response: `201 Created` with the reservation object. `feeAmount` is computed by the backend as billable hours (rounded up, minimum 1) × the location's `hourly_rate` — the client never sends a fee. Overlap with an existing `RESERVED`/`OCCUPIED` reservation on the same slot → `409`.

Entry / exit / cancel take **no request body** and return the updated reservation object.

---

## 6. Admin Dashboard — ADMIN only

### GET /api/admin/dashboard

Response `200 OK`:
```json
{
  "totalLocations": 2,
  "totalSlots": 40,
  "availableSlots": 25,
  "reservedSlots": 9,
  "occupiedSlots": 6,
  "occupancyPercent": 37.5,
  "totalReservationsToday": 14,
  "revenueToday": 1260.00
}
```
Definitions: slot counts cover active slots (`is_active = 1`) with the derived status of §3; `occupancyPercent` = (reserved + occupied) ÷ totalSlots × 100; "today" is the server's current date; `revenueToday` = sum of `fee_amount` for reservations created today whose status is not `CANCELLED`.

---

## 7. Prediction — Python-backed

### GET /api/predictions/occupancy?locationId={id}&date={YYYY-MM-DD} — USER, ADMIN

Exposed by the Spring Boot backend, which calls the Python service internally (`GET http://localhost:8000/predict/occupancy`). The browser never calls Python directly.

Response `200 OK`:
```json
{
  "locationId": 1,
  "locationName": "KL University Main Gate Parking",
  "date": "2026-10-09",
  "peakHour": 17,
  "peakOccupancyPercent": 85.0,
  "hourlyPredictions": [
    { "hour": 8,  "predictedOccupancyPercent": 40.0 },
    { "hour": 9,  "predictedOccupancyPercent": 62.5 },
    { "hour": 17, "predictedOccupancyPercent": 85.0 }
  ]
}
```
If the Python service is unreachable (or returns a non-200 / unparseable response), the backend does **not** error: it falls back to an in-Java calculation over the reservations stored in the database (same hourly-occupancy formula — non-CANCELLED reservations active in each hour, averaged over the distinct history days, as a % of the location's total slots, capped at 100%; a flat 10% profile when there is no history) and still returns `200 OK` in the same response shape. The prediction endpoint therefore has no `503` case, and all other endpoints are unaffected either way. The Python service URL is configurable via the `prediction.service.url` property / `PREDICTION_SERVICE_URL` env var (default `http://localhost:8000`).

---

## 8. Pricing — surge + promo quotes (v3)

The price of a booking is computed **server-side** by `PricingService`:

1. `baseAmount` = billable hours (rounded up, min 1) × the location's `hourlyRate` — unchanged from v1.
2. **Surge multiplier** from the predicted occupancy (§7) at the booking's location for the **start hour**: `>= 85%` → `1.50` ("Peak demand surge") · `70–84.99%` → `1.25` ("High demand surge") · otherwise `1.00` ("Standard pricing"). The prediction source keeps its existing fallback behaviour (Python service, else in-Java).
3. **Promo discount** applied to the *surged* amount: `PERCENT` codes take that % off; `FLAT` codes take ₹ off. A code is valid only while active, unexpired (`expiresAt` null or in the future) and `usedCount < maxUses` (null `maxUses` = unlimited).

`feeAmount` (final) = `baseAmount × multiplier − discountAmount`, floored at 0.

### GET /api/pricing/quote?slotId={id}&startTime={iso}&endTime={iso}&promoCode={code?} — USER

Response `200 OK`:
```json
{
  "slotId": 11, "locationId": 1, "hours": 2, "hourlyRate": 30.00,
  "baseAmount": 60.00, "surgeMultiplier": 1.00, "surgeReason": "Standard pricing",
  "promoCode": "WELCOME10", "promoApplied": true, "promoMessage": "10% off applied",
  "discountAmount": 6.00, "totalAmount": 54.00
}
```
An invalid promo on a **quote** never fails the call: `promoApplied` is `false` and `promoMessage` says why (not recognized / inactive / expired / usage limit reached). On reservation **create** (§5) the same invalid promo is a `400`, so a booking never silently loses a discount the user typed. A successful create increments the promo's `usedCount` by 1.

Seeded codes: `WELCOME10` (10% PERCENT), `FLAT50` (₹50 FLAT).

---

## 9. Reschedule (v3)

### PUT /api/reservations/{id} — USER (owner)

Body: `{ "startTime": "...", "endTime": "..." }`. Only from `RESERVED` (else `409`). The overlap check **excludes the reservation itself**, so moving a booking within/over its own old window is allowed; overlapping anyone else is `409`. The fee is **recomputed** for the new window (base + surge for the new start hour). The original promo (if any) is **removed** — its `usedCount` is decremented, `discountAmount` becomes 0 and `promoCode` becomes `null` — because the discount was priced for the old window. Response: the updated reservation object.

---

## 10. Payments — simulated (v3)

Exactly one row in `payments` per reservation, created `PENDING` at booking time with `amount = feeAmount`. This is a **simulated** gateway — no real money moves; paying always succeeds.

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/reservations/{id}/payment` | USER (owner), ADMIN | The reservation's payment |
| POST | `/api/reservations/{id}/pay` | USER (owner) | Mark paid; paying twice → `409` |

Payment object:
```json
{ "id": 9, "reservationId": 21, "amount": 54.00, "status": "PAID", "method": "SIMULATED_CARD", "transactionRef": "TXN-A1B2C3D4", "paidAt": "2026-10-09T08:40:00", "createdAt": "2026-10-09T08:25:00", "updatedAt": "2026-10-09T08:40:00" }
```
`status` ∈ `PENDING | PAID | REFUNDED`. Paying sets `method = SIMULATED_CARD`, a generated `transactionRef` (`TXN-` + 8 uppercase alphanumerics) and `paidAt`, and creates a `PAYMENT_DONE` notification. **Cancelling a reservation whose payment is `PAID` automatically sets the payment to `REFUNDED`.**

---

## 11. Waitlist (v3) — `waitlist_entries`

A user who finds no suitable slot joins the waitlist for a **location + window** (they do not pick a slot). Statuses: `WAITING → OFFERED → CLAIMED`, with `EXPIRED` (offer not claimed in time) and `CANCELLED` (user gave up).

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/api/waitlist` | USER | Join; body `{ "locationId": 1, "vehicleId": 5, "startTime": "...", "endTime": "..." }` → `201`, status `WAITING` |
| GET | `/api/waitlist/my` | USER | My entries, newest first |
| POST | `/api/waitlist/{id}/cancel` | USER (owner) | From `WAITING`/`OFFERED`, else `409` |
| POST | `/api/waitlist/{id}/claim` | USER (owner) | Only from `OFFERED`, else `409`; creates the reservation → `201` reservation object |
| GET | `/api/admin/waitlist` | ADMIN | All entries, newest first |

Entry object:
```json
{ "id": 4, "userId": 1, "locationId": 1, "locationName": "KL University Main Gate Parking", "vehicleId": 5, "licensePlate": "TS09AB1234", "startTime": "2026-10-09T18:00:00", "endTime": "2026-10-09T20:00:00", "status": "OFFERED", "offeredSlotId": 11, "offeredSlotCode": "A-01", "offerExpiresAt": "2026-10-09T12:15:00", "createdAt": "2026-10-09T11:58:00", "updatedAt": "2026-10-09T12:00:00" }
```

**Promotion:** when a reservation is **cancelled**, the freed slot is offered to the earliest-created `WAITING` entry at the same location whose window overlaps the freed window and whose vehicle fits the slot (vehicle type equals the slot type, or the slot is `GENERAL`) — the entry becomes `OFFERED` with that slot and a `WAITLIST_OFFER` notification. An offer lasts **15 minutes**; the scheduler (§13) then marks it `EXPIRED` and promotes the next matching entry. Claiming re-runs the overlap check — if the slot no longer fits the window, claim returns `409`.

---

## 12. Notifications (v3) — `notifications`

In-app notifications for the logged-in user. Types: `BOOKING_CONFIRMED`, `PAYMENT_DONE`, `WAITLIST_OFFER`, `RESERVATION_NO_SHOW`, `RESERVATION_CANCELLED` — created on booking, payment, waitlist offer, no-show and cancellation respectively.

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/notifications` | USER, ADMIN | Mine, newest first, max 50 |
| GET | `/api/notifications/unread-count` | USER, ADMIN | `{ "count": 3 }` |
| POST | `/api/notifications/{id}/read` | USER, ADMIN (owner) | Mark one read |
| POST | `/api/notifications/read-all` | USER, ADMIN | Mark all mine read; returns the unread-count shape (now 0) |

Notification object: `{ "id": 12, "type": "BOOKING_CONFIRMED", "title": "...", "message": "...", "isRead": false, "createdAt": "..." }`.

---

## 13. Scheduler — no-shows & offer expiry (v3)

A Spring scheduler (`@EnableScheduling`) sweeps every 60 seconds:

- **No-show:** a reservation still `RESERVED` whose `startTime` is more than **30 minutes** in the past and that never recorded an entry (`entryTime` null) becomes `NO_SHOW`, and the user gets a `RESERVATION_NO_SHOW` notification. (No waitlist promotion — the window has already passed.)
- **Offer expiry:** a waitlist entry still `OFFERED` more than **15 minutes** after its offer becomes `EXPIRED`, and the next matching `WAITING` entry is promoted for that slot/window.

---

## 14. Reviews, QR pass & admin extras (v3)

### Reviews — `reviews`

One review per reservation (unique `reservation_id`), only for the user's **own COMPLETED** reservation.

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/api/reviews` | USER | Body `{ "reservationId": 21, "rating": 5, "comment": "optional" }` → `201`; non-COMPLETED → `400`; someone else's → `403`; duplicate → `409`; rating outside 1–5 → `400` |
| GET | `/api/locations/{id}/reviews` | USER, ADMIN | `{ "id", "userName", "rating", "comment", "createdAt" }`, newest first |

### QR booking pass

### GET /api/reservations/{id}/qr — USER (owner), ADMIN

Returns `image/png` (~240×240, generated with ZXing) encoding the text `PARKGRID|RES:<id>|SLOT:<slotCode>|PLATE:<plate>|FROM:<startTime>|TO:<endTime>` — the payload a gate scanner would read to verify the booking.

### Admin: CSV export & promo management

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/admin/reservations/export?locationId=&status=&date=` | ADMIN | `text/csv` attachment `reservations.csv`; same filters as the admin list (§5). Columns: id, userEmail, licensePlate, locationName, slotCode, startTime, endTime, status, feeAmount, paymentStatus, createdAt |
| GET | `/api/admin/promos` | ADMIN | All promo codes |
| POST | `/api/admin/promos` | ADMIN | Create; body `{ "code", "type": "PERCENT"|"FLAT", "value", "maxUses"?, "expiresAt"? }` → `201`; duplicate code → `409` |
| POST | `/api/admin/promos/{id}/deactivate` | ADMIN | Sets `isActive = false` |

Promo object: `{ "id", "code", "type", "value", "maxUses", "usedCount", "expiresAt", "isActive", "createdAt" }`.

### API documentation (Swagger UI)

Interactive docs are served by springdoc-openapi at **`/swagger-ui.html`** (machine-readable spec at `/v3/api-docs`); both are public (no token needed to open the page).

---

## Endpoint Summary

| # | Method | Path | Role |
|---|---|---|---|
| 1 | POST | `/api/auth/register` | Public |
| 2 | POST | `/api/auth/login` | Public |
| 3 | GET | `/api/auth/me` | USER, ADMIN |
| 4 | GET | `/api/locations` | USER, ADMIN |
| 5 | GET | `/api/locations/{id}` | USER, ADMIN |
| 6 | POST | `/api/locations` | ADMIN |
| 7 | PUT | `/api/locations/{id}` | ADMIN |
| 8 | DELETE | `/api/locations/{id}` | ADMIN |
| 9 | GET | `/api/locations/{locationId}/slots` | USER, ADMIN |
| 10 | POST | `/api/locations/{locationId}/slots` | ADMIN |
| 11 | GET | `/api/slots/{id}` | USER, ADMIN |
| 12 | GET | `/api/slots/availability` | USER, ADMIN |
| 13 | PUT | `/api/slots/{id}` | ADMIN |
| 14 | DELETE | `/api/slots/{id}` | ADMIN |
| 15 | GET | `/api/vehicles/my` | USER |
| 16 | POST | `/api/vehicles` | USER |
| 17 | GET | `/api/vehicles/{id}` | USER (owner), ADMIN |
| 18 | PUT | `/api/vehicles/{id}` | USER (owner) |
| 19 | DELETE | `/api/vehicles/{id}` | USER (owner) |
| 20 | POST | `/api/reservations` | USER |
| 21 | GET | `/api/reservations/my` | USER |
| 22 | GET | `/api/reservations/{id}` | USER (owner), ADMIN |
| 23 | POST | `/api/reservations/{id}/cancel` | USER (owner), ADMIN |
| 24 | POST | `/api/reservations/{id}/entry` | USER (owner), ADMIN |
| 25 | POST | `/api/reservations/{id}/exit` | USER (owner), ADMIN |
| 26 | GET | `/api/reservations` | ADMIN |
| 27 | GET | `/api/admin/dashboard` | ADMIN |
| 28 | GET | `/api/predictions/occupancy` | USER, ADMIN |
| 29 | GET | `/api/pricing/quote` | USER |
| 30 | GET | `/api/reservations/{id}/payment` | USER (owner), ADMIN |
| 31 | POST | `/api/reservations/{id}/pay` | USER (owner) |
| 32 | PUT | `/api/reservations/{id}` | USER (owner) |
| 33 | GET | `/api/reservations/{id}/qr` | USER (owner), ADMIN |
| 34 | POST | `/api/waitlist` | USER |
| 35 | GET | `/api/waitlist/my` | USER |
| 36 | POST | `/api/waitlist/{id}/cancel` | USER (owner) |
| 37 | POST | `/api/waitlist/{id}/claim` | USER (owner) |
| 38 | GET | `/api/notifications` | USER, ADMIN |
| 39 | GET | `/api/notifications/unread-count` | USER, ADMIN |
| 40 | POST | `/api/notifications/{id}/read` | USER, ADMIN (owner) |
| 41 | POST | `/api/notifications/read-all` | USER, ADMIN |
| 42 | POST | `/api/reviews` | USER |
| 43 | GET | `/api/locations/{id}/reviews` | USER, ADMIN |
| 44 | GET | `/api/admin/reservations/export` | ADMIN |
| 45 | GET | `/api/admin/waitlist` | ADMIN |
| 46 | GET | `/api/admin/promos` | ADMIN |
| 47 | POST | `/api/admin/promos` | ADMIN |
| 48 | POST | `/api/admin/promos/{id}/deactivate` | ADMIN |
