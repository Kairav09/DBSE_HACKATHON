# Backend — Spring Boot (Phase 2)

Smart Parking Management System · CODESPARK Hackathon 2026 · Problem #01

Java 17 · Spring Boot 3.3.5 · Maven · Spring Web / Data JPA / Security · JWT (jjwt 0.12) · MySQL 8 (default) / H2 (dev profile)

Package: `com.smartparking` — layered `controller → service → repository`, with `model`, `dto`, `security`, `config`, `exception` packages. The API implements every endpoint in [`../docs/API.md`](../docs/API.md) (28 endpoints).

## Prerequisites

- JDK 17+
- Maven 3.9+ (`mvn`), or use a Maven distribution directly — no wrapper download is bundled in this repo copy, so use a system `mvn` (the commands below use `mvn`; if you generate the wrapper once with `mvn wrapper:wrapper`, `./mvnw` works identically).
- MySQL 8 — **only for the default profile**. The `dev` profile needs nothing.

## Run — Option A: dev profile (H2 in-memory, fastest, no MySQL)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- H2 in-memory (MySQL compatibility mode), tables created by JPA, seeded automatically by `DataInitializer`.
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:smart_parking`, user `sa`, empty password)
- Data resets on every restart.

## Run — Option B: MySQL (evaluated configuration)

```bash
mysql -u root -p -e "CREATE DATABASE smart_parking;"
mysql -u root -p smart_parking < ../database/schema.sql
mysql -u root -p smart_parking < ../database/seed.sql

cd backend
mvn spring-boot:run
```

Datasource is env-overridable:

| Env var | Default |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/smart_parking?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `root` |
| `JWT_SECRET` | dev-only default in `application.yml` — set a real one outside local dev |
| `PREDICTION_SERVICE_URL` | `http://localhost:8000` |

API base URL: `http://localhost:8080` · CORS is open to the React frontend at `http://localhost:5173`.

## Seeded demo accounts

Seeded both by `database/seed.sql` (MySQL) and by `DataInitializer` (dev/H2 profile):

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@smartparking.local` | `admin123` |
| USER | `user1@example.com` | `user123` |
| USER | `user2@example.com` | `user123` |

Seed data: 2 locations (KL University Main Gate Parking ₹30/hr, City Center Mall Parking ₹50/hr), 16 slots per location (mixed FOUR_WHEELER / TWO_WHEELER / EV / ACCESSIBLE / GENERAL), 4 vehicles, and reservations in RESERVED / OCCUPIED / COMPLETED states across today plus the past 7 days at varied hours (08/09/12/17/18) so the admin dashboard and the prediction endpoint have real history.

## Quick smoke test

```bash
# login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user1@example.com","password":"user123"}'
# -> { "token": "<jwt>", "user": { ... } }

# use the token
curl -s http://localhost:8080/api/locations -H "Authorization: Bearer <jwt>"
curl -s http://localhost:8080/api/locations/1/slots -H "Authorization: Bearer <jwt>"
curl -s http://localhost:8080/api/admin/dashboard -H "Authorization: Bearer <admin-jwt>"
```

## Key design notes (from Phase 1 docs)

- **Slot status is derived, never stored** — `parking_slots` has no status column; AVAILABLE / RESERVED / OCCUPIED is computed at read time from `reservations` (ARCHITECTURE.md §4).
- **`reservations` has no `location_id`** — location is reached via `slot_id → parking_slots.location_id`; the API exposes it as a read-only convenience field only.
- **Double-booking:** overlap check + insert run in one `@Transactional` service method; overlap = existing `start < new end AND existing end > new start` among RESERVED/OCCUPIED reservations for the slot → `409`.
- **Fee:** billable hours = ceil(duration, min 1) × the location's `hourly_rate`, snapshotted onto the reservation at creation.
- **Prediction (Phase 2 shape):** `GET /api/predictions/occupancy` first tries the Python service at `prediction.service.url` (`GET /predict/occupancy`); if it is unreachable it falls back to an in-Java hourly-occupancy average computed from historical reservations, so the endpoint works standalone. Phase 4 wires the real Python service.
- **Errors:** every error returns `{ timestamp, status, error, message, path }` (docs/API.md).
