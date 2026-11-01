package com.smartparking.model;

/** Mirrors reservations.status ENUM in database/schema.sql */
public enum ReservationStatus {
    RESERVED, OCCUPIED, COMPLETED, CANCELLED, NO_SHOW
}
