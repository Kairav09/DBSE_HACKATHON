package com.smartparking.model;

/** Mirrors notifications.type ENUM in database/schema.sql */
public enum NotificationType {
    BOOKING_CONFIRMED, PAYMENT_DONE, WAITLIST_OFFER, RESERVATION_NO_SHOW, RESERVATION_CANCELLED
}
