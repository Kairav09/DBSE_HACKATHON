# ER Diagram — Smart Parking Management System

This diagram matches `database/schema.sql` **exactly** — same tables, same columns, same types, same keys. If the schema changes, this file must change with it.

```mermaid
erDiagram
    users {
        BIGINT id PK
        VARCHAR full_name
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR phone
        ENUM role "USER, ADMIN"
        DATETIME created_at
        DATETIME updated_at
    }

    parking_locations {
        BIGINT id PK
        VARCHAR name
        VARCHAR address
        VARCHAR city
        DECIMAL hourly_rate
        TINYINT is_active
        DATETIME created_at
        DATETIME updated_at
    }

    parking_slots {
        BIGINT id PK
        BIGINT location_id FK
        VARCHAR slot_code UK "unique with location_id"
        ENUM vehicle_type "TWO_WHEELER, FOUR_WHEELER, EV, ACCESSIBLE, GENERAL"
        TINYINT is_active
        DATETIME created_at
        DATETIME updated_at
    }

    vehicles {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR license_plate UK
        ENUM vehicle_type "TWO_WHEELER, FOUR_WHEELER, EV, ACCESSIBLE, GENERAL"
        VARCHAR model
        VARCHAR color
        DATETIME created_at
        DATETIME updated_at
    }

    reservations {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT vehicle_id FK
        BIGINT slot_id FK
        BIGINT promo_code_id FK "nullable (v3)"
        DATETIME start_time
        DATETIME end_time
        ENUM status "RESERVED, OCCUPIED, COMPLETED, CANCELLED, NO_SHOW"
        DECIMAL fee_amount "FINAL amount (v3 note)"
        DECIMAL base_amount "v3: pre-surge base"
        DECIMAL pricing_multiplier "v3: 1.00 / 1.25 / 1.50"
        DECIMAL discount_amount "v3"
        DATETIME entry_time
        DATETIME exit_time
        DATETIME created_at
        DATETIME updated_at
    }

    promo_codes {
        BIGINT id PK
        VARCHAR code UK
        ENUM type "PERCENT, FLAT"
        DECIMAL discount_value "column name avoids the H2 reserved word VALUE"
        INT max_uses "nullable = unlimited"
        INT used_count
        DATETIME expires_at "nullable"
        TINYINT is_active
        DATETIME created_at
        DATETIME updated_at
    }

    payments {
        BIGINT id PK
        BIGINT reservation_id FK UK "one payment per reservation"
        DECIMAL amount
        ENUM status "PENDING, PAID, REFUNDED"
        VARCHAR method "nullable until paid"
        VARCHAR transaction_ref UK "nullable until paid"
        DATETIME paid_at "nullable"
        DATETIME created_at
        DATETIME updated_at
    }

    waitlist_entries {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT location_id FK
        BIGINT vehicle_id FK
        DATETIME start_time
        DATETIME end_time
        ENUM status "WAITING, OFFERED, CLAIMED, EXPIRED, CANCELLED"
        BIGINT offered_slot_id FK "nullable, set on OFFERED"
        DATETIME offer_expires_at "nullable"
        DATETIME created_at
        DATETIME updated_at
    }

    notifications {
        BIGINT id PK
        BIGINT user_id FK
        ENUM type "BOOKING_CONFIRMED, PAYMENT_DONE, WAITLIST_OFFER, RESERVATION_NO_SHOW, RESERVATION_CANCELLED"
        VARCHAR title
        VARCHAR message
        TINYINT is_read
        DATETIME created_at
        DATETIME updated_at
    }

    reviews {
        BIGINT id PK
        BIGINT reservation_id FK UK "one review per reservation"
        BIGINT user_id FK
        BIGINT location_id FK
        INT rating "1..5"
        VARCHAR comment "nullable"
        DATETIME created_at
        DATETIME updated_at
    }

    parking_locations ||--o{ parking_slots : "has (location_id, ON DELETE CASCADE)"
    users ||--o{ vehicles : "owns (user_id, ON DELETE CASCADE)"
    users ||--o{ reservations : "makes (user_id, ON DELETE RESTRICT)"
    vehicles ||--o{ reservations : "booked in (vehicle_id, ON DELETE RESTRICT)"
    parking_slots ||--o{ reservations : "booked by (slot_id, ON DELETE RESTRICT)"
    promo_codes o|--o{ reservations : "discounts (promo_code_id, ON DELETE SET NULL)"
    reservations ||--o| payments : "paid via (reservation_id, ON DELETE CASCADE)"
    users ||--o{ waitlist_entries : "joins (user_id, ON DELETE CASCADE)"
    parking_locations ||--o{ waitlist_entries : "queued for (location_id, ON DELETE CASCADE)"
    vehicles ||--o{ waitlist_entries : "queued with (vehicle_id, ON DELETE CASCADE)"
    parking_slots o|--o{ waitlist_entries : "offered as (offered_slot_id, ON DELETE SET NULL)"
    users ||--o{ notifications : "receives (user_id, ON DELETE CASCADE)"
    reservations ||--o| reviews : "reviewed by (reservation_id, ON DELETE CASCADE)"
    users ||--o{ reviews : "writes (user_id, ON DELETE CASCADE)"
    parking_locations ||--o{ reviews : "rated for (location_id, ON DELETE CASCADE)"
```

## Relationships

| Relationship | Via | Cardinality | Delete behaviour |
|---|---|---|---|
| `parking_locations` → `parking_slots` | `parking_slots.location_id` | One location has many slots | `ON DELETE CASCADE` — deleting a location deletes its slots (blocked in practice if any slot has reservations, because of the RESTRICT below) |
| `users` → `vehicles` | `vehicles.user_id` | One user owns many vehicles | `ON DELETE CASCADE` |
| `users` → `reservations` | `reservations.user_id` | One user makes many reservations | `ON DELETE RESTRICT` — reservation history is preserved |
| `vehicles` → `reservations` | `reservations.vehicle_id` | One vehicle appears in many reservations | `ON DELETE RESTRICT` |
| `parking_slots` → `reservations` | `reservations.slot_id` | One slot appears in many reservations | `ON DELETE RESTRICT` |
| `promo_codes` → `reservations` | `reservations.promo_code_id` | One promo discounts many reservations; a reservation uses at most one promo | `ON DELETE SET NULL` — deleting a promo keeps booking history |
| `reservations` → `payments` | `payments.reservation_id` (UNIQUE) | Exactly one payment per reservation | `ON DELETE CASCADE` |
| `users` → `waitlist_entries` | `waitlist_entries.user_id` | One user queues in many entries | `ON DELETE CASCADE` |
| `parking_locations` → `waitlist_entries` | `waitlist_entries.location_id` | One location has many queued entries | `ON DELETE CASCADE` |
| `vehicles` → `waitlist_entries` | `waitlist_entries.vehicle_id` | One vehicle appears in many entries | `ON DELETE CASCADE` |
| `parking_slots` → `waitlist_entries` | `waitlist_entries.offered_slot_id` | A slot may be offered in entries over time | `ON DELETE SET NULL` |
| `users` → `notifications` | `notifications.user_id` | One user receives many notifications | `ON DELETE CASCADE` |
| `reservations` → `reviews` | `reviews.reservation_id` (UNIQUE) | At most one review per reservation | `ON DELETE CASCADE` |
| `users` → `reviews` | `reviews.user_id` | One user writes many reviews | `ON DELETE CASCADE` |
| `parking_locations` → `reviews` | `reviews.location_id` | One location collects many reviews (denormalised from the reservation's slot for fast rating aggregation) | `ON DELETE CASCADE` |

## Deliberate design points

- **No status column on `parking_slots`.** A slot's live status (AVAILABLE / RESERVED / OCCUPIED) is derived from `reservations` — see `docs/ARCHITECTURE.md` §4. `reservations.status` is the only stored status.
- **No `location_id` on `reservations`.** A reservation's location is always reached through `reservations.slot_id → parking_slots.location_id`. The API's reservation object exposes it as a read-only convenience field only.
- **Unique constraints:** `users.email`, `vehicles.license_plate`, and (`parking_slots.location_id`, `parking_slots.slot_code`).
- **Key index:** `idx_reservations_slot_time (slot_id, start_time, end_time)` supports the double-booking overlap check and live-status derivation. Other indexes: `idx_reservations_slot_status`, `idx_reservations_user_created`, `idx_reservations_status_start`, `idx_reservations_vehicle`, `idx_parking_slots_location_active`, `idx_vehicles_user`.
- **Checks:** `reservations.end_time > reservations.start_time`; `parking_locations.hourly_rate >= 0`; `reservations.fee_amount >= 0` when present.
- **(v3) Price breakdown is stored, not recomputed:** `reservations.fee_amount` stays the FINAL charged amount; `base_amount`, `pricing_multiplier` and `discount_amount` snapshot how it was reached (surge tier + promo), so later price-rule changes never rewrite history.
- **(v3) `promo_codes.discount_value`** — the column is not called `value` because `VALUE` is a reserved word in H2 (the dev profile's database); the API still exposes it as `value`.
- **(v3) Simulated payments:** `payments` models a payment gateway record (status/method/transaction_ref/paid_at) with exactly one row per reservation (UNIQUE `reservation_id`); no real payment processor is integrated.
- **(v3) Waitlist is location-based:** entries carry `location_id` + a time window, not a slot; a concrete slot appears only in `offered_slot_id` once the entry is `OFFERED`.
