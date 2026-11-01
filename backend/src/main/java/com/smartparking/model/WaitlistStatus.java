package com.smartparking.model;

/** Mirrors waitlist_entries.status ENUM in database/schema.sql */
public enum WaitlistStatus {
    WAITING, OFFERED, CLAIMED, EXPIRED, CANCELLED
}
