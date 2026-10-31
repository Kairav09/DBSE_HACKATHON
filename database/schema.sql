-- ============================================================================
-- Smart Parking Management System — MySQL 8 schema
-- CODESPARK Hackathon 2026 · Problem Statement #01
--
-- Source of truth for all table / column names. docs/API.md and
-- database/ER_DIAGRAM.md use exactly these names.
--
-- Usage:
--   CREATE DATABASE smart_parking;
--   mysql -u root -p smart_parking < database/schema.sql
--
-- Design notes:
--   * Slot live status (AVAILABLE / RESERVED / OCCUPIED) is NOT stored.
--     It is derived from `reservations` at read time — see docs/ARCHITECTURE.md.
--   * `reservations` deliberately has no location column: a reservation's
--     location is reached via slot_id -> parking_slots.location_id.
--   * Double-booking is prevented in the backend service layer with a
--     transactional overlap check; the (slot_id, start_time, end_time)
--     index below exists to make that check fast (MySQL has no
--     time-range exclusion constraint).
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- users — drivers (USER) and parking operators (ADMIN)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(20)  NULL,
    role          ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- parking_locations — a parking site managed by admins
-- hourly_rate is the price per billable hour for every slot in the location.
-- ----------------------------------------------------------------------------
CREATE TABLE parking_locations (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(120)  NOT NULL,
    address     VARCHAR(255)  NOT NULL,
    city        VARCHAR(80)   NULL,
    hourly_rate DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    is_active   TINYINT(1)    NOT NULL DEFAULT 1,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_parking_locations_hourly_rate CHECK (hourly_rate >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- parking_slots — an individual bay inside a parking location.
-- slot_code (e.g. 'A-01') is unique within its location.
-- No status column: live status is derived from reservations.
-- ----------------------------------------------------------------------------
CREATE TABLE parking_slots (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    location_id BIGINT      NOT NULL,
    slot_code   VARCHAR(20) NOT NULL,
    vehicle_type ENUM('TWO_WHEELER', 'FOUR_WHEELER', 'EV', 'ACCESSIBLE', 'GENERAL')
                NOT NULL DEFAULT 'GENERAL',
    is_active   TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_parking_slots_location_code (location_id, slot_code),
    KEY idx_parking_slots_location_active (location_id, is_active),
    CONSTRAINT fk_parking_slots_location
        FOREIGN KEY (location_id) REFERENCES parking_locations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- vehicles — a vehicle owned by a user; booked against reservations.
-- ----------------------------------------------------------------------------
CREATE TABLE vehicles (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    vehicle_type  ENUM('TWO_WHEELER', 'FOUR_WHEELER', 'EV', 'ACCESSIBLE', 'GENERAL')
                  NOT NULL DEFAULT 'GENERAL',
    model         VARCHAR(80) NULL,
    color         VARCHAR(40) NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_vehicles_license_plate (license_plate),
    KEY idx_vehicles_user (user_id),
    CONSTRAINT fk_vehicles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- promo_codes — discount codes. type PERCENT: discount_value = % off the
-- surged amount. type FLAT: discount_value = ₹ off. Valid only while is_active, not expired,
-- and used_count < max_uses (max_uses NULL = unlimited).
-- ----------------------------------------------------------------------------
CREATE TABLE promo_codes (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    code       VARCHAR(40)   NOT NULL,
    type       ENUM('PERCENT', 'FLAT') NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,
    max_uses   INT           NULL,
    used_count INT           NOT NULL DEFAULT 0,
    expires_at DATETIME      NULL,
    is_active  TINYINT(1)    NOT NULL DEFAULT 1,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_promo_codes_code (code),
    CONSTRAINT chk_promo_codes_value CHECK (discount_value > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- reservations — one booking of one slot by one user for one vehicle.
-- Lifecycle: RESERVED --entry--> OCCUPIED --exit--> COMPLETED
--            RESERVED --cancel--> CANCELLED
-- fee_amount is a snapshot (billable hours x the location's hourly_rate at
-- booking time, finalised at exit) so later rate changes never rewrite it.
-- v3: fee_amount = base_amount x pricing_multiplier - discount_amount, where
-- base_amount is the pre-surge snapshot, pricing_multiplier is the surge
-- tier applied (1.00 / 1.25 / 1.50) and discount_amount is the promo saving.
-- ----------------------------------------------------------------------------
CREATE TABLE reservations (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    vehicle_id BIGINT        NOT NULL,
    slot_id    BIGINT        NOT NULL,
    start_time DATETIME      NOT NULL,
    end_time   DATETIME      NOT NULL,
    status     ENUM('RESERVED', 'OCCUPIED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
               NOT NULL DEFAULT 'RESERVED',
    fee_amount DECIMAL(10,2) NULL,
    promo_code_id       BIGINT       NULL,
    base_amount         DECIMAL(10,2) NULL,
    pricing_multiplier  DECIMAL(4,2)  NOT NULL DEFAULT 1.00,
    discount_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    entry_time DATETIME      NULL,
    exit_time  DATETIME      NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Slot + time lookup: powers the overlap / double-booking check and
    -- the derived live slot status.
    KEY idx_reservations_slot_time (slot_id, start_time, end_time),
    KEY idx_reservations_slot_status (slot_id, status),
    KEY idx_reservations_user_created (user_id, created_at),
    KEY idx_reservations_status_start (status, start_time),
    KEY idx_reservations_vehicle (vehicle_id),
    CONSTRAINT chk_reservations_time_range CHECK (end_time > start_time),
    CONSTRAINT chk_reservations_fee CHECK (fee_amount IS NULL OR fee_amount >= 0),
    CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_reservations_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_reservations_slot
        FOREIGN KEY (slot_id) REFERENCES parking_slots (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_reservations_promo_code
        FOREIGN KEY (promo_code_id) REFERENCES promo_codes (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================================
-- v3 expansion tables (docs/API_V3_CONTRACT.md)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- payments — simulated payment, exactly one row per reservation, created
-- PENDING at booking with amount = reservations.fee_amount.
-- PAID via the pay endpoint (method SIMULATED_CARD); cancelling a PAID
-- reservation flips it to REFUNDED.
-- ----------------------------------------------------------------------------
CREATE TABLE payments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    reservation_id  BIGINT        NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    status          ENUM('PENDING', 'PAID', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    method          VARCHAR(30)   NULL,
    transaction_ref VARCHAR(40)   NULL,
    paid_at         DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_reservation (reservation_id),
    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- waitlist_entries — a user's queue entry for a location + time window.
-- WAITING --promotion--> OFFERED --claim--> CLAIMED; OFFERED expires after
-- 15 minutes (scheduler); owner can cancel from WAITING / OFFERED.
-- ----------------------------------------------------------------------------
CREATE TABLE waitlist_entries (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    user_id        BIGINT   NOT NULL,
    location_id    BIGINT   NOT NULL,
    vehicle_id     BIGINT   NOT NULL,
    offered_slot_id BIGINT  NULL,
    start_time     DATETIME NOT NULL,
    end_time       DATETIME NOT NULL,
    status         ENUM('WAITING', 'OFFERED', 'CLAIMED', 'EXPIRED', 'CANCELLED')
                   NOT NULL DEFAULT 'WAITING',
    offered_at     DATETIME NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_waitlist_location_status_created (location_id, status, created_at),
    KEY idx_waitlist_user (user_id),
    CONSTRAINT chk_waitlist_time_range CHECK (end_time > start_time),
    CONSTRAINT fk_waitlist_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_waitlist_location
        FOREIGN KEY (location_id) REFERENCES parking_locations (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_waitlist_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_waitlist_offered_slot
        FOREIGN KEY (offered_slot_id) REFERENCES parking_slots (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- notifications — in-app notifications for a user (booking confirmed, payment
-- done, waitlist offer, no-show, cancellation). related_id points at the
-- reservation or waitlist entry the notification is about.
-- ----------------------------------------------------------------------------
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       ENUM('BOOKING_CONFIRMED', 'PAYMENT_DONE', 'WAITLIST_OFFER', 'RESERVATION_NO_SHOW', 'RESERVATION_CANCELLED')
               NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    related_id BIGINT       NULL,
    is_read    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notifications_user_read_created (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------------------
-- reviews — one review per reservation (UNIQUE reservation_id), written by
-- the reservation's owner after it COMPLETED. location_id is denormalised
-- from the reservation's slot so per-location aggregates are a simple query.
-- ----------------------------------------------------------------------------
CREATE TABLE reviews (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    location_id    BIGINT       NOT NULL,
    rating         TINYINT      NOT NULL,
    comment        VARCHAR(500) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reviews_reservation (reservation_id),
    KEY idx_reviews_location_created (location_id, created_at),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_reviews_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_reviews_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_reviews_location
        FOREIGN KEY (location_id) REFERENCES parking_locations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
