# Problem Definition — Smart Parking Management System

**Source:** CODESPARK Hackathon 2026, Problem Statement #01, Track 1 — Smart Mobility & Transportation

> *"Build a platform that allows users to find available parking spaces, reserve slots, and manage vehicle entry and exit. Core functionality: Parking locations and slots; Vehicle registration; Slot availability; Reservations; Entry/exit records; Admin dashboard. Suggested Python component: Predict parking occupancy or peak-hour demand using Python."*

**Duration context:** 8-hour hackathon sprint · Team size: 4 · Mandatory stack: Java Spring Boot backend, React frontend, MySQL/PostgreSQL persistence, Python only as a supporting intelligence component.

---

## 1. The Problem

1. **Availability is invisible.** A driver only learns whether a parking location has a free slot after reaching it. The result is circling, congestion at entrances, and wasted time and fuel.
2. **Reservations are unmanaged.** Without a booking system, slots cannot be held for a time window, and there is no protection against two drivers expecting the same slot at the same time.
3. **Entry/exit is untracked.** Operators cannot reliably say which vehicle is inside, when it entered, or what fee is due on exit.
4. **Operators fly blind.** Location/slot management, current occupancy, and daily revenue live in registers or spreadsheets — there is no live dashboard, and no view of when peak demand occurs.

## 2. The Solution (Summary)

A web platform with three integrated parts:

1. **React frontend** — users browse locations, see a live slot map (AVAILABLE / RESERVED / OCCUPIED), register vehicles, and book; admins manage locations/slots and view a dashboard.
2. **Spring Boot REST backend** — all business logic: authentication and roles, locations, slots, vehicles, reservations with a double-booking (overlap) check, entry/exit, fee calculation, and dashboard statistics. Persists to MySQL via Spring Data JPA.
3. **Python prediction service (supporting only)** — analyses historical `reservations` data and predicts hourly occupancy / peak-hour demand for a location and date. The Java backend calls it for the prediction endpoint; core parking flows never depend on it.

## 3. Users and Roles

Two roles, enforced by Spring Security. The role is stored on `users.role`.

### USER (driver)

- Registers and logs in.
- Browses parking locations and views live slot availability per location.
- Registers and manages their own vehicles (`vehicles.user_id` = themselves).
- Creates a reservation for one of their vehicles on a chosen slot for a `start_time`–`end_time` window.
- Views only their own reservations ("my reservations").
- Cancels their own reservation while it is still `RESERVED`.
- Records entry and exit for their own reservation (simulated in the MVP with an action in the app — see Out of Scope).
- Views the occupancy prediction for a location/date.

### ADMIN (parking operator)

- Everything is managed through the admin views; an admin does not book parking for themselves in the MVP.
- Creates, updates, and deactivates/deletes parking locations and their slots.
- Views all reservations, with filters by location, status, and date.
- Can cancel a reservation, and can record entry/exit on behalf of a user (e.g. at a gate desk).
- Views the admin dashboard: total / available / reserved / occupied slots, today's reservations, today's revenue, occupancy %.
- Views the occupancy prediction for planning.

No other roles exist in the MVP (no separate gate-operator or super-admin role).

## 4. Scope

### In scope (MVP — must work in the demo)

| Area | Included |
|---|---|
| Auth | Register, login, current-user (`/api/auth/me`); passwords stored hashed (`users.password_hash`); role-based access (USER / ADMIN) |
| Locations | Admin CRUD for `parking_locations`; user browsing (list + detail) |
| Slots | Admin CRUD for `parking_slots` under a location; unique `slot_code` per location |
| Live availability | Slot list per location with status derived live from reservations: AVAILABLE / RESERVED / OCCUPIED |
| Vehicles | User CRUD for their own `vehicles` (license plate, vehicle type, model, colour) |
| Reservations | Create with time-overlap check on the same slot; cancel; list mine; detail view |
| Entry / exit | Entry moves a reservation `RESERVED → OCCUPIED` and stamps `entry_time`; exit moves it `OCCUPIED → COMPLETED`, stamps `exit_time`, and finalises `fee_amount` |
| Fee | `fee_amount` = billable hours (rounded up, minimum 1 hour) × the location's `hourly_rate` |
| Admin dashboard | Slot counts by derived status, today's reservations, today's revenue, occupancy % |
| Prediction | Python service predicts hourly occupancy % and the peak hour for a location + date; exposed via the backend at `/api/predictions/occupancy` |
| Quality basics | Input validation, meaningful error responses, FK integrity, unique constraints (see `database/schema.sql`) |

### Out of scope (explicitly not in the MVP)

- Real online payments / payment gateway — the fee is calculated and displayed only.
- ANPR / camera-based number-plate recognition, QR/barcode gates, or any hardware integration — entry/exit is recorded by an in-app action.
- Native Android/iOS apps — responsive web only.
- Maps, routing, or "navigate to this location" (no Google Maps / external maps API).
- Dynamic / surge pricing — `hourly_rate` is fixed per location.
- Waitlists, recurring reservations, monthly passes, and subscriptions.
- Multi-operator / tenant support, and any role beyond USER and ADMIN.
- Email/SMS notifications.
- A stored slot-status column — slot status is always derived from reservations, never saved (see ARCHITECTURE.md).

## 5. Key Business Rules

1. A reservation's `end_time` must be after its `start_time`.
2. **No double-booking:** a new reservation for a slot is rejected if it overlaps an existing reservation for the same slot whose status is `RESERVED` or `OCCUPIED`. Overlap means `existing.start_time < new.end_time AND existing.end_time > new.start_time`. (Enforced in the backend service layer inside a transaction — MySQL has no simple exclusion constraint for time ranges; the `(slot_id, start_time, end_time)` index in the schema exists to make this check fast.)
3. A user can only book, cancel, enter, and exit with their **own** vehicle and their **own** reservation. Admins are exempt for cancel/entry/exit.
4. Cancel is allowed only from status `RESERVED`. Entry is allowed only from `RESERVED`. Exit is allowed only from `OCCUPIED`. All other transitions are rejected with an error.
5. `license_plate` is unique across `vehicles`; `slot_code` is unique within a location — (`location_id`, `slot_code`).
6. `users.email` is unique and is the login identifier.
7. Deleting a location cascades to its slots. A slot, vehicle, or user that has reservations cannot be hard-deleted in a way that breaks reservation history — reservations keep their foreign keys (schema uses `ON DELETE RESTRICT` for reservation references).

## 6. Success Criteria for the Demo

The MVP is demo-ready when a presenter can do all of the following live, in order, without touching the database directly:

1. **Register + login** as a USER; register + login as an ADMIN in a second session/browser.
2. As ADMIN, **create a location** (with an `hourly_rate`) and **add slots** to it.
3. As USER, open the location and see the **live slot map** with every new slot shown as AVAILABLE.
4. As USER, **register a vehicle**, then **reserve a slot** for a time window and see the slot flip to RESERVED and the reservation appear in "My Reservations" with a calculated `fee_amount`.
5. Attempt a **second, overlapping reservation** for the same slot and window and show it being **rejected** (double-booking check).
6. **Record entry** (reservation → OCCUPIED, slot shows OCCUPIED, `entry_time` set), then **record exit** (reservation → COMPLETED, slot returns to AVAILABLE, `exit_time` and final `fee_amount` set).
7. Create a second reservation and **cancel** it (→ CANCELLED, slot available again).
8. As ADMIN, open the **dashboard** and show slot counts by status, today's reservations, and today's revenue reflecting the actions just performed.
9. Open the **prediction** view for the location and show predicted hourly occupancy and the predicted peak hour from the Python service. If the Python service is stopped, show that the rest of the app still works and the prediction endpoint reports it as unavailable.

Supporting artefacts for evaluation: this document, `docs/ARCHITECTURE.md` (with ER + architecture diagrams), `docs/API.md` (endpoint list), `database/schema.sql`, and a README with setup/run instructions.
