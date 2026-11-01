package com.smartparking.model;

/**
 * Derived live status of a parking slot. NEVER stored — computed at read time
 * from reservations (see docs/ARCHITECTURE.md §4). There is deliberately no
 * status column on the ParkingSlot entity / parking_slots table.
 */
public enum SlotStatus {
    AVAILABLE, RESERVED, OCCUPIED
}
