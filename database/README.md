# Database

| File | Contents |
|---|---|
| `schema.sql` | MySQL 8 schema — the source of truth for every table and column name used across the API and code |
| `ER_DIAGRAM.md` | Mermaid ER diagram matching `schema.sql` exactly, with relationship/delete-behaviour notes |

## Tables

`users` · `parking_locations` · `parking_slots` · `vehicles` · `reservations`

## Load into MySQL 8

```sql
CREATE DATABASE smart_parking;
```

```bash
mysql -u root -p smart_parking < database/schema.sql
```

> Local development alternative: run the backend with the Spring `dev` profile (H2 in-memory, JPA-generated tables) — no MySQL install needed. The evaluated configuration is MySQL 8 with this schema.

Key design decisions (full detail in `ER_DIAGRAM.md` and `../docs/ARCHITECTURE.md`):

- Slot live status is **derived** from `reservations`, never stored on `parking_slots`.
- `reservations` has no location column — location is reached via `slot_id → parking_slots.location_id`.
- Index `idx_reservations_slot_time (slot_id, start_time, end_time)` supports the double-booking overlap check.
