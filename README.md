# Smart Parking Management System — "ParkGrid"

**CODESPARK Hackathon 2026 — Problem Statement #01 (Track 1: Smart Mobility & Transportation)**
KL University, Bachupally Campus · In association with Yellowspot Technologies

> Find available parking, reserve a slot, and manage vehicle entry and exit — with a live slot map, role-based access, double-booking prevention, and a Python-powered prediction of occupancy and peak-hour demand.

---

## Problem Summary

Drivers waste time circling for parking because availability is invisible until they arrive. Parking operators, in turn, have no single system for locations, slots, reservations, and entry/exit records.

This platform solves both sides:

- **Users (drivers)** see live slot availability per location, register their vehicles, and reserve a slot for a chosen time window. They then record entry and exit against that reservation.
- **Admins (operators)** manage parking locations and slots, monitor occupancy and revenue from a dashboard, and see predicted peak hours so they can plan capacity.

Full problem definition: [docs/PROBLEM_DEFINITION.md](docs/PROBLEM_DEFINITION.md)

## Features

- Authentication and role-based access — `USER` and `ADMIN` (Spring Security + JWT, BCrypt passwords)
- Parking locations — admin CRUD, user browsing (seeded: KL University Main Gate ₹30/hr, City Center Mall ₹50/hr)
- Live slot map per location — slots shown as AVAILABLE / RESERVED / OCCUPIED, grouped by vehicle type, auto-refreshing; slot status is *derived* from reservations, never stored, so it can't go stale
- Vehicle registration — a user registers vehicles once, then books with them
- Reservations — create with a transactional overlap check (**double-booking is rejected with `409`**), cancel, my-reservations list
- Entry / exit recording against a reservation; fee = billable hours (rounded up, min 1) × the location's `hourly_rate`, snapshotted at booking time
- Admin dashboard — total / available / reserved / occupied slots, today's reservations, today's revenue, occupancy %
- Occupancy / peak-hour prediction — a Python FastAPI service analyses reservation history and predicts hourly occupancy + peak hour for a location and date; if it is down, the backend falls back to the same calculation in Java and still returns `200`
- Input validation, consistent API error responses, and role/ownership checks on every endpoint

**v3 expansion (backend):**

- **Dynamic pricing** — a surge multiplier from predicted occupancy at the booking hour (≥85% → ×1.50, ≥70% → ×1.25), quoted live via `GET /api/pricing/quote`
- **Promo codes** — `WELCOME10` (10% off), `FLAT50` (₹50 off) seeded; admin can create/deactivate codes; discounts apply to the surged amount and are snapshotted on the booking
- **Simulated payments** — one payment per booking (PENDING → PAID with a transaction reference; cancelling a paid booking auto-refunds)
- **Reschedule** — move a booking to a new window (overlap check excludes the booking itself; fee recomputed)
- **Waitlist with auto-promotion** — join a location queue; when a matching slot frees up you're offered it for 15 minutes and notified
- **Notifications** — booking / payment / waitlist-offer / cancellation / no-show events, with unread counts
- **Reviews & ratings** — one review per completed booking; locations show average rating + review count
- **No-show detection** — a background scheduler marks unclaimed bookings NO_SHOW after a 30-minute grace
- **QR booking pass** — server-generated QR PNG per reservation (`GET /api/reservations/{id}/qr`)
- **Admin extras** — reservations CSV export, promo management, waitlist overview; interactive API docs at `/swagger-ui.html`

Out of scope: real payment processing (payments are simulated), ANPR / camera plate recognition, native mobile apps, Google Maps integration. See [docs/PROBLEM_DEFINITION.md](docs/PROBLEM_DEFINITION.md).

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 (Vite), React Router — "ParkGrid" UI |
| Backend | Java 17, Spring Boot 3.3 — Spring MVC / REST, Spring Data JPA / Hibernate, Spring Security + JWT |
| Database | MySQL 8 (primary) · H2 in-memory (dev profile, no local MySQL needed) |
| Prediction service | Python 3, FastAPI (pure-stdlib occupancy math, no pandas) — supporting component only |
| Build / tooling | Maven, npm / Vite, Git |

> Per the CODESPARK rules, Java Spring Boot is the primary application backend. Python is used **only** as a supporting intelligence component (occupancy / peak-hour prediction) and does not replace any Java backend functionality. The browser never calls the Python service directly — only the backend does.

## Architecture (summary)

```
Browser (React, :5173)
        │  REST + JWT
        ▼
Spring Boot backend (:8080) ───────────► MySQL 8 (or H2 in dev profile)
        │  HTTP (prediction.service.url)
        ▼
Python FastAPI prediction service (:8000) ──► reads reservation history
                                              (MySQL, or bundled sample CSV
                                               when no DB is configured)
```

Slot lifecycle: `AVAILABLE → RESERVED → OCCUPIED → AVAILABLE`, with `CANCELLED` as an exit from `RESERVED` and `NO_SHOW` for bookings never claimed (v3). Full diagrams (system architecture, booking sequence, state machine): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). ER diagram: [database/ER_DIAGRAM.md](database/ER_DIAGRAM.md). API reference (48 endpoints): [docs/API.md](docs/API.md).

## Repo Layout

```
smart-parking/
├── README.md                  # this file
├── docs/
│   ├── PROBLEM_DEFINITION.md  # problem, users/roles, scope, demo success criteria
│   ├── ARCHITECTURE.md        # system architecture, booking flow, slot lifecycle
│   └── API.md                 # REST endpoint reference (48 endpoints)
├── database/
│   ├── schema.sql             # MySQL 8 schema — 10 tables (source of truth)
│   ├── seed.sql               # MySQL demo dataset (accounts below, 2 locations, 32 slots, 56 reservations)
│   ├── ER_DIAGRAM.md          # mermaid ER diagram — matches schema.sql
│   └── README.md
├── backend/                   # Spring Boot app (package com.smartparking)
│   ├── pom.xml
│   ├── README.md              # backend-only run notes
│   └── src/main/java/com/smartparking/  # controller / service / repository / model / dto / security / config
├── frontend/                  # React (Vite) app — "ParkGrid"
│   ├── package.json
│   └── src/                   # pages / components / context / lib
└── python-service/            # FastAPI prediction service
    ├── main.py                # GET /health, GET /predict/occupancy
    ├── prediction.py          # occupancy math (pure stdlib)
    ├── data_source.py         # MySQL read, sample-CSV fallback
    ├── sample_history.csv     # bundled history for standalone demo
    ├── test_prediction.py     # pytest suite
    └── requirements.txt
```

## Setup & Run

Prerequisites: JDK 17+, Maven 3.9+, Node.js 18+ (20+ recommended), Python 3.10+, and MySQL 8 **only for Way 1** (Way 2 needs no MySQL).

### Way 1 — MySQL (the evaluated configuration)

```bash
# 1. Database — create, schema, seed
mysql -u root -p -e "CREATE DATABASE smart_parking;"
mysql -u root -p smart_parking < database/schema.sql
mysql -u root -p smart_parking < database/seed.sql

# 2. Backend — default profile connects to MySQL
#    (override with env vars DB_URL / DB_USERNAME / DB_PASSWORD if yours differ)
cd backend && mvn spring-boot:run            # http://localhost:8080

# 3. Python prediction service (reads the same MySQL DB;
#    set DB_USERNAME / DB_PASSWORD env vars if not root with empty password)
cd python-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8000                 # http://localhost:8000

# 4. Frontend
cd frontend && npm install && npm run dev    # http://localhost:5173
```

### Way 2 — Quick demo (no MySQL needed)

The backend `dev` profile uses an in-memory H2 database and seeds the **same** demo data automatically on startup. The Python service independently falls back to its bundled `sample_history.csv`, so the full stack — including prediction — works with zero database setup:

```bash
# 1. Backend on H2 (seeded automatically)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 2. Python prediction service (CSV fallback mode — no DB env vars needed)
cd python-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8000

# 3. Frontend
cd frontend && npm install && npm run dev    # http://localhost:5173
```

Startup order note: start the backend and Python service in any order — if Python is not up yet, the prediction endpoint temporarily serves the backend's in-Java fallback; it picks Python up automatically on the next request once it is running.

On Windows, use `mvn spring-boot:run -Dspring-boot.run.profiles=dev` the same way (or `mvnw`), and activate the venv with `.venv\Scripts\activate`.

## Demo Accounts

Seeded by both `database/seed.sql` (MySQL) and the backend dev profile:

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@smartparking.local` | `admin123` |
| USER | `user1@example.com` | `user123` |
| USER | `user2@example.com` | `user123` |

The login page also has one-click demo-fill buttons for these accounts.

## Demo Flow (for judges, ~5 minutes)

1. Open `http://localhost:5173` — you land on the **public landing page** (product overview, how it works, demo accounts in the “For judges” section). Click **Sign in** and use the one-click demo fill for **`user1@example.com` / `user123`** — you land on the **user dashboard** (booking stats, next booking, quick actions).
2. Open **Locations**, pick a location (e.g. **KL University Main Gate Parking**) and show the **live slot map** — green available, amber reserved, red occupied, grouped by vehicle type.
3. Pick an available slot, choose a vehicle and a time window, and **book it** — point out the fee estimate (hours × ₹30) matching the backend rule; the slot turns reserved.
4. **Try booking the same slot for an overlapping window** — the system rejects it with `409 Slot already booked for the selected time window` (double-booking prevention).
5. Go to **My Reservations** and click **Entry** — the reservation becomes `OCCUPIED` and the slot turns red on the map. Open **View pass** to show the printable parking pass.
6. Click **Exit** — the reservation completes, the final fee is shown, and the slot is available again.
7. Show the **prediction panel** on the location page: predicted hourly occupancy with the **peak hour highlighted** (served by the Python service via the backend).
8. Log out and **log in as admin** (`admin@smartparking.local` / `admin123`) — you land on the **admin dashboard**: total/available/reserved/occupied slots, occupancy %, today's reservations and revenue.
9. Open **Analytics** (revenue by location, bookings by status, occupancy by location), plus location/slot management and the all-reservations view. The **Help** page explains the lifecycle and fee rule if judges ask.
10. **v3 via Swagger UI** (`http://localhost:8080/swagger-ui.html`, log in through the API first and reuse the token): get a **price quote** with promo `WELCOME10` and show the surge + discount breakdown; **book with the promo** and show `baseAmount / pricingMultiplier / discountAmount / feeAmount` plus the `PENDING` payment; **pay** it (transaction reference issued); open the **QR pass** (`GET /api/reservations/{id}/qr`) — a scannable PNG of the booking.
11. **Waitlist demo:** as user1, join the waitlist for a location/window, then cancel a booking that frees a matching slot — the waitlist entry flips to `OFFERED` with a notification; **claim** it to turn it into a real reservation. Finish a trip (entry → exit) and **post a review** — the location's average rating updates.
12. As admin, download the **reservations CSV export** and show **promo management** (create/deactivate a code) in Swagger UI.

## Deliverables Checklist (CODESPARK compendium §4)

| Expected deliverable | Where |
|---|---|
| Working frontend application | `frontend/` (React + Vite) |
| Java Spring Boot backend with REST APIs | `backend/` (48 endpoints) |
| MySQL/PostgreSQL database with schema and relationships | `database/schema.sql` (10 tables, FKs, indexes) + `database/seed.sql` |
| Source code repository with a clear README | this repository / this file |
| Database ER diagram | `database/ER_DIAGRAM.md` |
| Basic system architecture diagram | `docs/ARCHITECTURE.md` |
| API documentation / endpoint list | `docs/API.md` |
| Working demonstration of the core MVP | Demo flow above; demo accounts seeded |
| Python AI/ML component (supporting) | `python-service/` (occupancy / peak-hour prediction) |

## Team

CODESPARK Hackathon 2026 team project (4 members).
