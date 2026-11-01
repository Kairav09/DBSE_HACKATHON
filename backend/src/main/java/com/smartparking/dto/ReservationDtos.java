package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReservationDtos {

    public record ReservationRequest(
            @NotNull(message = "vehicleId is required") Long vehicleId,
            @NotNull(message = "slotId is required") Long slotId,
            @NotNull(message = "startTime is required") LocalDateTime startTime,
            @NotNull(message = "endTime is required") LocalDateTime endTime,
            /** v3: optional promo code; the server recomputes every amount itself. */
            String promoCode
    ) {
        /** Backwards-compatible constructor for callers/tests that don't use promos. */
        public ReservationRequest(Long vehicleId, Long slotId, LocalDateTime startTime, LocalDateTime endTime) {
            this(vehicleId, slotId, startTime, endTime, null);
        }
    }

    public record RescheduleRequest(
            @NotNull(message = "startTime is required") LocalDateTime startTime,
            @NotNull(message = "endTime is required") LocalDateTime endTime
    ) {}

    public record ReservationResponse(
            Long id, Long userId, Long vehicleId, Long slotId, Long locationId,
            String licensePlate, String slotCode, String locationName,
            LocalDateTime startTime, LocalDateTime endTime,
            String status, BigDecimal feeAmount,
            // v3 pricing breakdown (additive — v1/v2 fields unchanged, feeAmount stays the FINAL amount)
            BigDecimal baseAmount, BigDecimal pricingMultiplier, BigDecimal discountAmount,
            String promoCode, String paymentStatus,
            LocalDateTime entryTime, LocalDateTime exitTime,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {}
}
