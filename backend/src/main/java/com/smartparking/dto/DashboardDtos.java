package com.smartparking.dto;

import java.math.BigDecimal;

public class DashboardDtos {
    public record DashboardResponse(
            long totalLocations,
            long totalSlots,
            long availableSlots,
            long reservedSlots,
            long occupiedSlots,
            double occupancyPercent,
            long totalReservationsToday,
            BigDecimal revenueToday
    ) {}
}
