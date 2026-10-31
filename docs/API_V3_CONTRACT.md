# API v3 Contract — Expansion Round (authoritative for v3 work)

Extends docs/API.md (28 endpoints). All v3 endpoints follow the same error shape
`{timestamp,status,error,message,path}` and JWT rules. Roles: USER / ADMIN as noted.
Money is in ₹ (BigDecimal, 2dp). Times are ISO-8601 local datetimes, same as v1/v2.

## Schema changes
- NEW tables: `promo_codes`, `payments`, `waitlist_entries`, `notifications`, `reviews`.
- `reservations` NEW columns: `promo_code_id` (FK nullable), `base_amount`,
  `pricing_multiplier` (default 1.00), `discount_amount` (default 0).
  `fee_amount` remains the FINAL charged amount.
- `reservations.status` enum gains `NO_SHOW`.
- Location responses gain `averageRating` (number|null) and `reviewCount` (int).

## Pricing & promo
- Surge pricing from predicted occupancy at the booking's location + start hour
  (PredictionService data, Java fallback included): >=85% -> x1.50 ("Peak demand surge"),
  >=70% -> x1.25 ("High demand surge"), else x1.00.
- `GET /api/pricing/quote?slotId&startTime&endTime&promoCode?` (auth)
  -> `{slotId, locationId, hours, hourlyRate, baseAmount, surgeMultiplier, surgeReason,
      promoCode, promoApplied, promoMessage, discountAmount, totalAmount}`
  baseAmount = billableHours(ceil, min 1) x hourlyRate; total = base x surge - discount (floor 0).
- Promo codes: PERCENT (value = % off the surged amount) or FLAT (₹ off). A code is valid
  only if active, not expired, usedCount < maxUses. Invalid code on quote -> promoApplied=false
  + promoMessage explains; invalid code on CREATE reservation -> 400 (explicit user action).
- `POST /api/reservations` body gains optional `promoCode`. Server NEVER trusts client
  amounts: it recomputes base/surge/discount itself. On success usedCount increments.
- Seed promos: `WELCOME10` = 10% PERCENT, `FLAT50` = ₹50 FLAT (both active, generous limits).

## Payments (simulated)
- `payments`: one row per reservation, created PENDING at booking with amount = fee_amount.
- `GET /api/reservations/{id}/payment` (owner/ADMIN) -> `{id, reservationId, amount, status, method, transactionRef, paidAt}`
- `POST /api/reservations/{id}/pay` (owner) -> status PAID, method `SIMULATED_CARD`,
  transactionRef `TXN-` + 8 uppercase alphanumerics, paidAt set. Paying twice -> 409.
  Cancelling a PAID reservation sets its payment REFUNDED.
- Reservation DTO gains `paymentStatus`.

## Reschedule
- `PUT /api/reservations/{id}` (owner) body `{startTime, endTime}` — only when RESERVED.
  Overlap check EXCLUDES the reservation itself. Fee recomputed (base + surge);
  any promo on the original booking is removed (discount 0, promo cleared, usedCount decremented back).

## Waitlist
- `waitlist_entries` statuses: WAITING, OFFERED, CLAIMED, EXPIRED, CANCELLED.
- `POST /api/waitlist` (USER) `{locationId, vehicleId, startTime, endTime}` -> WAITING entry DTO
  `{id, locationId, locationName, vehicleId, licensePlate, startTime, endTime, status, offeredSlotId, offeredSlotCode, createdAt}`.
- `GET /api/waitlist/my` (USER); `POST /api/waitlist/{id}/cancel` (owner);
  `POST /api/waitlist/{id}/claim` (owner, only OFFERED) — creates a reservation on the offered
  slot for the entry window (overlap re-checked; 409 if it no longer fits), entry -> CLAIMED.
- `GET /api/admin/waitlist` (ADMIN) — all entries, newest first.
- Promotion: when a reservation is CANCELLED (or marked NO_SHOW), for the freed slot, find the
  earliest WAITING entry for the same location whose requested window overlaps the freed window
  and whose vehicle fits; set it OFFERED with that slot + create a notification.
  OFFERED entries not claimed within 15 minutes -> EXPIRED by the scheduler, then promote the next.

## Notifications
- `notifications`: `{id, type, title, message, relatedId, read, createdAt}` — types:
  BOOKING_CONFIRMED, PAYMENT_DONE, WAITLIST_OFFER, RESERVATION_NO_SHOW, RESERVATION_CANCELLED.
- `GET /api/notifications` (mine, newest first, max 50); `GET /api/notifications/unread-count`
  -> `{count}`; `POST /api/notifications/{id}/read`; `POST /api/notifications/read-all`.
- Created on: booking created, payment paid, waitlist offer, no-show marking, admin/user cancel.

## Reviews
- `reviews`: one per reservation (UNIQUE reservation_id).
- `POST /api/reviews` (USER) `{reservationId, rating (1-5), comment?}` — only for own
  COMPLETED reservation; duplicates -> 409; non-completed -> 400.
- `GET /api/locations/{id}/reviews` (auth) -> list newest first `{id, userName, rating, comment, createdAt}`.

## Scheduler (backend)
- `@EnableScheduling`, sweep every 60s: RESERVED reservations whose startTime is >30 min past
  and never entered -> NO_SHOW (+ notification + waitlist promotion for the freed window is
  SKIPPED for no-show since the window has passed — promotion only on cancel).
  OFFERED waitlist entries older than 15 min -> EXPIRED (+ try promoting the next WAITING entry for that slot/location).

## QR pass + export + docs
- `GET /api/reservations/{id}/qr` (owner/ADMIN) -> `image/png` (ZXing, ~240px) encoding
  `PARKGRID|RES:<id>|SLOT:<slotCode>|PLATE:<plate>|FROM:<start>|TO:<end>`.
- `GET /api/admin/reservations/export` (ADMIN) -> `text/csv` attachment `reservations.csv`
  (respects the same optional filters as the admin list: locationId, status, date).
- springdoc-openapi UI: `/swagger-ui.html` + `/v3/api-docs` publicly reachable
  (permit in SecurityConfig), springdoc-openapi-starter-webmvc-ui 2.6.0.

## Frontend usage notes
- Booking panel: promo input + "Apply" calls quote; the price display shows the QUOTE
  breakdown (base, surge w/ reason, discount, total) — no more purely local estimate.
  If the chosen window has NO available slots at all, show "Join waitlist" (location + window +
  vehicle) instead of a dead end. After booking, reservation shows paymentStatus; pass modal
  shows the QR <img> from the qr endpoint (fetch as blob with JWT).
- Reservations page: Pay button when paymentStatus=PENDING; Reschedule (RESERVED) with
  datetime modal; "Write review" (COMPLETED, once) with star input; reviews shown on location detail.
- Nav: notification bell with unread badge + dropdown (mark read on open item click; "mark all").
  New page `/notifications` optional if dropdown suffices — build the page too, it's cheap.
  New page `/waitlist` (my waitlist entries with Claim/Cancel).
- Admin: `/admin/promos` (create/list/deactivate promo codes), `/admin/waitlist` (table),
  Analytics gains an occupancy HEATMAP (hour x day-of-week from admin reservations data,
  small cells) + "Export CSV" button.
- Locations cards show average rating stars when reviewCount > 0.
