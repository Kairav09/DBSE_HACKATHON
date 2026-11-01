package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class WaitlistDtos {

    public record WaitlistRequest(
            @NotNull(message = "locationId is required") Long locationId,
            @NotNull(message = "vehicleId is required") Long vehicleId,
            @NotNull(message = "startTime is required") LocalDateTime startTime,
            @NotNull(message = "endTime is required") LocalDateTime endTime
    ) {}

    public record WaitlistResponse(
            Long id, Long locationId, String locationName,
            Long vehicleId, String licensePlate,
            LocalDateTime startTime, LocalDateTime endTime,
            String status, Long offeredSlotId, String offeredSlotCode,
            LocalDateTime createdAt
    ) {}
}
