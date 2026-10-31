# Postman Collection — ParkGrid API

`docs/postman_collection.json` (Postman v2.1) covers the v1 + v3 API:
**35 requests in 8 folders** — Auth, Locations & Slots, Pricing & Booking,
Payments, Waitlist, Notifications, Reviews, Admin.

## Import

1. Start the backend (see root `README.md` — Way 1 MySQL or Way 2 dev profile,
   or `docker compose up`).
2. Postman → **Import** → select `docs/postman_collection.json`.
3. The collection is pre-configured: `baseUrl = http://localhost:8080`, and
   the collection-level auth is `Bearer {{token}}`, so most requests just work
   after step 1 below.

## Run order

1. **Auth → Login (USER)** — a test script saves the JWT into `{{token}}`.
   Seeded account: `user1@example.com / user123` (owns vehicle `1`,
   plate `TS09AB1234`).
2. **Locations & Slots** — browse locations, the slot-map data, availability,
   and the occupancy prediction.
3. **Pricing & Booking** — quote (with seeded promo `WELCOME10`; `FLAT50`
   also exists), create a reservation (script saves `{{reservationId}}`),
   then run the duplicate request to see the **409 double-booking demo**.
   Reschedule must run *before* Entry; Exit completes the lifecycle; the QR
   request returns a PNG (use *Save Response* to view it).
4. **Payments** — the reservation's payment starts `PENDING`; *Pay (simulated)*
   marks it `PAID` with a `TXN-…` reference. Paying again returns 409.
5. **Waitlist / Notifications / Reviews** — join the waitlist, check the
   notification inbox (booking + payment create entries), and review the
   reservation **after Exit** (reviews require a COMPLETED reservation;
   a second review for the same reservation returns 409).
6. **Auth → Login (ADMIN)** — saves `{{adminToken}}`, which the **Admin**
   folder uses for the dashboard, the CSV export (use *Save Response* to
   download `reservations.csv`), promo management, and the admin waitlist.

## Variables

| Variable | Default | Set by |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | you, if the backend runs elsewhere |
| `token` | — | Login (USER) test script |
| `adminToken` | — | Login (ADMIN) test script |
| `locationId` / `slotId` / `vehicleId` | `1` | seeded data (location 1 = KL University Main Gate, ₹30/hr) |
| `reservationId` | `1` | Create reservation test script |
| `waitlistId` | `1` | Join waitlist test script |
| `startTime` / `endTime` | `2026-11-05T10:00:00` / `12:00:00` | **keep these in the future** — edit the collection variables if they have passed |

> Note: the seeded demo dataset also contains ready-made reservations, so
> some demo calls (e.g. reviewing reservation `1`) may behave differently
> than on a fresh booking — the descriptions inside each request call out
> the preconditions.
