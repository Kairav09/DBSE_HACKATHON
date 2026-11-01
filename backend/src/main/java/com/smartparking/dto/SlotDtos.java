package com.smartparking.dto;

import com.smartparking.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SlotDtos {

    public record SlotRequest(
            @NotBlank(message = "slotCode is required") String slotCode,
            @NotNull(message = "vehicleType is required") VehicleType vehicleType,
            Boolean isActive
    ) {}

    /** Slot object with DERIVED live status (no status column exists in the DB). */
    public record SlotResponse(
            Long id, Long locationId, String slotCode, VehicleType vehicleType,
            Boolean isActive, String status
    ) {}
}
