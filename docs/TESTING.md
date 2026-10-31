# Testing — Smart Parking Management System ("ParkGrid")

How this project was tested, what the results were, and how to re-run everything.
Four layers: **backend unit tests** (JUnit 5 + Mockito), **Python service tests**
(pytest), **API smoke tests** (the running backend exercised with curl against the
seeded demo data), and a **manual UI checklist** for a laptop run.

---

## 1. Backend unit tests (JUnit 5 + Mockito)

Location: `backend/src/test/java/com/smartparking/service/`
Style: plain unit tests — repositories and collaborator services are Mockito
mocks, **no Spring context and no database**, so they run fast on any machine.

Run:

```bash
cd backend
mvn test
```

### Result (executed 2026-10-31; v3 run after the v1/v2 run)

```
Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0  -- AuthServiceTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0  -- PaymentServiceTest        (v3)
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0  -- PredictionServiceTest
Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0  -- PricingServiceTest        (v3)
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0  -- ReservationServiceTest    (12 v1/v2 + 5 v3)
Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0  -- ReviewServiceTest         (v3)
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0  -- BUILD SUCCESS
```

All 15 pre-v3 tests still pass unchanged in behaviour: `ReservationServiceTest`
stubs the new `PricingService` to return the plain v1 price (base fee, ×1.00,
no promo) for the original fee/overlap assertions.

### What is covered

**ReservationServiceTest (12)** — the booking core:

| Test | Asserts |
|---|---|
| `computeFee_exactlyOneHour_chargesOneHour` | 1h booking = 1 × hourly rate (minimum-1-hour rule) |
| `computeFee_partialHour_roundsUpToNextBillableHour` | 2h05m = 3 billable hours (ceil) × rate |
| `create_identicalWindowOverlap_rejected409` | Same slot, same window → `409` "already booked" |
| `create_partialOverlapAtStart_rejected409` | New window overlapping the start of an existing RESERVED → `409` |
| `create_partialOverlapAtEnd_rejected409` | New window overlapping the end of an existing OCCUPIED → `409` |
| `create_adjacentWindow_allowed_andFeeSnapshottedFromLocationRate` | New start == existing end is **not** an overlap (half-open `[start, end)`); booking succeeds with fee = 2h × the location's ₹30 rate, snapshotted at booking time |
| `create_cancelledReservationInWindow_doesNotBlock` | A CANCELLED reservation in the same window does not block (only RESERVED/OCCUPIED are blocking) |
| `create_endNotAfterStart_rejected400` | `endTime <= startTime` → `400` |
| `create_startTooFarInPast_rejected400` | Start beyond the 5-minute grace in the past → `400` |
| `cancel_occupiedReservation_rejected409` | Cancel is only legal from RESERVED |
| `entry_cancelledReservation_rejected409` | Entry is only legal from RESERVED |
| `exit_reservedReservation_rejected409` | Exit is only legal from OCCUPIED |

Note on the overlap tests: the overlap geometry (`existing.start < newEnd AND
existing.end > newStart`, restricted to the statuses passed in) lives in the
repository JPQL. In these unit tests the repository mock answers
`existsOverlapping()` by applying that exact predicate to a fixture
reservation, which verifies the service passes the right blocking statuses and
rejects/allows correctly. The real JPQL itself is covered end-to-end by the API
smoke tests in §3 (a real overlapping booking against the running app returns
409).

**PredictionServiceTest (2)** — the in-Java fallback used when the Python
service is unreachable:

| Test | Asserts |
|---|---|
| `javaFallback_hourlyAverageAndPeakHour` | On a 2-day, 4-slot fixture: hour 17 → 25.0%, hour 18 → 37.5%, quiet hour → 0.0%, peak = 18:00 at 37.5% |
| `javaFallback_noHistory_flatTenPercentProfile` | No history → all 24 hours at the flat low-confidence 10.0% |

**ReservationServiceTest — v3 additions (5)**:

| Test | Asserts |
|---|---|
| `create_withValidPercentPromo_discountAppliedAndUsedCountIncremented` | Booking with a stubbed 10%-off pricing result: fee 54.00 (60 − 6), breakdown fields on the response, promo `usedCount` 0 → 1 |
| `create_withInvalidPromo_rejected400` | Pricing layer rejecting an expired promo on create → `400` |
| `reschedule_overlappingOnlyItself_allowed` | Moving 10:00–12:00 to 11:00–13:00 succeeds (exclude-self overlap); fee recomputed |
| `reschedule_removesPromoAndGivesBackUsedCount` | Reschedule clears promo + discount and decrements the promo's `usedCount` |
| `reschedule_completedReservation_rejected409` | Only RESERVED reservations can be rescheduled |

**PricingServiceTest (8)** — `PredictionService` mocked with fixed hourly profiles:

| Test | Asserts |
|---|---|
| `surge_peakOccupancy85Plus_multiplier150` | 90% predicted occupancy → ×1.50, "Peak demand surge", total 90.00 on a 60.00 base |
| `surge_highOccupancy70to84_multiplier125` | 75% → ×1.25, total 75.00 |
| `surge_lowOccupancy_multiplier100` | 42% → ×1.00, total 60.00 |
| `promo_percentDiscount_appliesToSurgedAmount` | 10% code on a surged 90.00 → discount 9.00, total 81.00 |
| `promo_flatDiscount_subtractsRupees` | FLAT50 on 60.00 → discount 50.00, total 10.00 (via the quote path) |
| `promo_unknownCode_quoteSoftFails` | Quote: `promoApplied=false`, "not recognized", full price |
| `promo_expiredCode_createFails400` | Expired code with fail-on-invalid → `400` |
| `promo_exhaustedCode_quoteSoftFails` | `usedCount == maxUses` → soft fail with "usage limit" |

**PaymentServiceTest (2)**:

| Test | Asserts |
|---|---|
| `pay_pendingPayment_becomesPaidWithTxnRef` | PENDING → PAID, method `SIMULATED_CARD`, ref matches `TXN-[A-Z0-9]{8}`, `paidAt` set |
| `pay_alreadyPaid_rejected409` | Second pay → `409` |

**ReviewServiceTest (4)**:

| Test | Asserts |
|---|---|
| `create_completedOwnReservation_succeeds` | Own COMPLETED booking → review created with rater's name |
| `create_nonCompletedReservation_rejected400` | RESERVED booking → `400` |
| `create_duplicateReview_rejected409` | Second review for the same reservation → `409` |
| `create_someoneElsesReservation_rejected403` | Another user's booking → `403` |

**AuthServiceTest (1)**:

| Test | Asserts |
|---|---|
| `register_duplicateEmail_rejected409` | Registering an existing email → `409` "Email already registered", and nothing is saved |

---

## 2. Python prediction service tests (pytest)

Run:

```bash
cd python-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
pytest
```

**Result: 4 passed.** Covers the occupancy math on hand-computed fixtures
(including multi-hour overlap and the empty-history flat profile), the
evening-peak assertion on the bundled sample history, the exact response shape
the Java DTO expects, and the HTTP endpoints (`/health`, `/predict/occupancy`)
via FastAPI's TestClient.

---

## 3. API smoke tests (backend running, seeded demo data)

The backend was booted on the `dev` (H2) profile with the standard seed
(2 locations, 32 slots, 56 reservations; accounts in the root README) and every
call below was made with curl. These are the **actual observed results**:

| # | Call | Observed result |
|---|---|---|
| 1 | `POST /api/auth/register` | `201`, role forced to `USER` |
| 2 | Register with an already-used email | `409` |
| 3 | Register/login with an invalid payload | `400` with per-field messages |
| 4 | Login with wrong password | `401` in the standard error shape |
| 5 | Any protected call without a token | `401` |
| 6 | `GET /api/locations/{id}/slots` | Slots returned with **derived** status (a seeded slot shows `OCCUPIED`; there is no status column in the DB) |
| 7 | `POST /api/reservations` (2h at ₹30/hr location) | `201`, status `RESERVED`, **fee ₹60.00** |
| 8 | Same slot, overlapping window | **`409` "Slot A-01 is already booked for the selected time window"** |
| 9 | `POST /api/reservations/{id}/entry` | `200`, status `OCCUPIED`, `entryTime` set |
| 10 | `POST /api/reservations/{id}/cancel` after entry | `409` (only RESERVED can be cancelled) |
| 11 | `POST /api/reservations/{id}/exit` | `200`, status `COMPLETED`, `exitTime` set |
| 12 | `GET /api/slots/availability` for a test window | Correct free subset (14 of 16 slots for the tested window) |
| 13 | `GET /api/admin/dashboard` as ADMIN | `{ totalLocations: 2, totalSlots: 32, available: 27, reserved: 3, occupied: 2, occupancyPercent: 15.6, totalReservationsToday: 7, revenueToday: 620.00 }` — hand-verified against the seed |
| 14 | `GET /api/admin/dashboard` as USER | `403` |
| 15 | `GET /predict/occupancy` on the Python service directly (sample-data mode) | Location 1: **peak 18:00 at 88.8%**, full 24-hour series; Location 2: peak 18:00 at 78.6%; unknown location → `404` |
| 16 | Backend prediction endpoint with Python **down** | `200` with the in-Java fallback calculation in the same response shape (peak hour 18 from the H2 seed data) |
| 17 | Full booking flow (login → book → overlap 409 → entry → exit) with all three components up | Passed, twice, identical results |

### 3b. v3 API smoke tests (executed 2026-10-31, same dev-profile boot)

40 live checks against the running backend with the v3 seed (promos
`WELCOME10`/`FLAT50`, payments backfilled for seeded reservations, 4 seeded
reviews) — **all passed**:

| Area | Observed result |
|---|---|
| Pricing quote | No promo: base ₹60.00 (2h × ₹30), ×1.00, total ₹60.00 · `WELCOME10`: applied, discount ₹6.00, total ₹54.00 · bogus code: `promoApplied=false`, "not recognized", no error |
| Booking with promo | `201` with `feeAmount` 54.00, `discountAmount` 6.00, `promoCode` WELCOME10, `paymentStatus` PENDING · booking with a bogus promo → `400` |
| Payments | Payment starts PENDING at the fee amount · pay → PAID, `SIMULATED_CARD`, ref `TXN-…` · pay again → `409` · cancel a PAID booking → payment REFUNDED + `RESERVATION_CANCELLED` notification |
| Notifications | After book+pay: list contains `PAYMENT_DONE` + `BOOKING_CONFIRMED`, unread-count 2 → read-all → 0 |
| QR pass | `GET /api/reservations/{id}/qr` → `200`, `Content-Type: image/png`, real PNG bytes |
| Reschedule | PUT new window → `200`, window moved, promo stripped (`promoCode` null, discount 0), fee recomputed to ₹60.00 |
| Reviews | On RESERVED → `400` · after entry+exit (COMPLETED) → `201` · duplicate → `409` · location review list contains it · location `reviewCount` 2 → 3, `averageRating` present (4.5 seeded) |
| Waitlist | Join → WAITING · cancel a booking freeing a matching four-wheeler slot → entry OFFERED with that slot code · claim → `201` reservation · entry CLAIMED · admin waitlist list `200` |
| Admin promos | List contains WELCOME10 + FLAT50 · create `TEST5` → `201` · deactivate → `isActive=false` · as USER → `403` |
| CSV export | `200`, `text/csv`, `Content-Disposition: attachment; filename="reservations.csv"`, 60 data rows · `?status=COMPLETED` filter → 52 rows, all COMPLETED |
| Swagger | `GET /v3/api-docs` → `200` OpenAPI JSON · `GET /swagger-ui.html` → `200` |
| Regression | Prediction endpoint still `200` with 24 hourly entries; admin dashboard still `200` with 32 total slots |

One real bug was found and fixed by this boot test: the promo column originally
named `value` is a reserved word in H2, so the `promo_codes` table failed to
create on the dev profile — the column is now `discount_value` (API field is
still `value`).

**Known environment caveat:** in the build VM, the Java→Python live call could
not be exercised because the VM sandbox denies direct TCP from Java processes
(the backend therefore used its fallback, which is the designed behaviour).
Both sides are individually verified against the identical contract; on a
normal laptop the same code path reaches Python — re-check in one minute:
start the Python service, open the prediction panel, and Location 1 should show
peak **18:00 at ~88.8%** on the sample history.

---

## 4. Frontend build check

```bash
cd frontend
npm install
npm run build   # must pass
npm run dev     # http://localhost:5173 for the manual checklist below
```

**Result:** production build passes (≈205 KB JS bundle); the built app serves
on :5173 with all API calls exercised against the live backend during
development. A real browser click-through was **not** possible in the build VM
(no browser), so the visual pass is the checklist in §5.

---

## 5. Manual UI checklist (laptop run)

Setup: backend running (Way 2 quick-demo from the root README is fine), Python
service running, `npm run dev` for the frontend. Demo accounts:
`user1@example.com / user123`, `user2@example.com / user123`,
`admin@smartparking.local / admin123`.

**Landing & auth**

- [ ] `/` landing page loads logged-out: hero, live location availability preview, how-it-works, prediction teaser all render; animations play without jank
- [ ] Sign in with wrong password → clear error, no navigation
- [ ] Sign in as `user1` → lands on `/home`; register a new account → can sign in with it
- [ ] Refresh on any logged-in page → session survives; Logout → back to landing

**User flow**

- [ ] `/home` dashboard: stat tiles, upcoming reservation, quick actions all show real data
- [ ] `/locations`: both locations with rates and live counts; open KL University Main Gate
- [ ] Slot map: tiles green/amber/red match the legend, grouped by vehicle type; auto-refresh (15s) picks up a change made in another tab/session
- [ ] `/vehicles`: add a vehicle (plate, type, model, colour) → it appears in the booking form's vehicle dropdown; delete works; duplicate plate is rejected with a message
- [ ] Book an available slot: fee shown before confirming = hours (rounded up) × rate; after booking the slot turns amber and the reservation appears in `/reservations`
- [ ] Book the **same slot, overlapping time** (second user or second window) → highlighted "already booked for that window" alert, not a dead end
- [ ] `/reservations`: Entry → status `OCCUPIED` + pass view updates; Exit → `COMPLETED` with final fee; Cancel on a fresh RESERVED booking → `CANCELLED` and the slot frees up
- [ ] `/profile`: details correct; `/help`: FAQ/support content renders

**Admin flow**

- [ ] Sign in as admin → nav shows admin items, user booking items hidden (admins cannot book — by design)
- [ ] `/admin` dashboard tiles match §3 row 13's numbers on the seed (32 slots, 15.6% occupancy, ₹620 revenue today)
- [ ] `/admin/analytics`: hourly prediction chart renders, peak hour highlighted, location switcher works; with the Python service stopped, the page still loads (Java fallback values)
- [ ] Admin locations: create a location, add a slot, deactivate a slot → changes reflect in the user slot map
- [ ] Admin all-reservations: location/status/date filters return the right rows

**Cross-cutting**

- [ ] As a USER, typing `/admin` in the address bar redirects away (role guard)
- [ ] Resize to a phone-width viewport: nav, slot grid, and tables stay usable
- [ ] No emoji icons anywhere; charts stay small/compact per design

---

## 6. What is NOT covered (honest gaps)

- **MySQL live run** — in the build VM there is no MySQL server, so
  `schema.sql` / `seed.sql` and the Python MySQL read path are
  syntax-/logic-reviewed only; everything above ran on the H2 dev profile with
  the same entities and seed data. First laptop run should use Way 1 (MySQL)
  from the README at least once.
- **Java→Python over real TCP in the VM** — blocked by the VM sandbox (see §3);
  the fallback path is unit-tested and smoke-tested instead.
- **Browser click-through in the VM** — not possible; §5 is the substitute.
- **Scheduler live behaviour (NO_SHOW / offer expiry)** — time-based (30/15
  minutes), so it was verified by code/logic review and the repository queries
  it runs, not by waiting out a live sweep in the smoke run.
- No load/performance testing — out of scope for the hackathon MVP.
