package com.smartparking.dto;

import com.smartparking.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class VehicleDtos {

    public record VehicleRequest(
            @NotBlank(message = "licensePlate is required") String licensePlate,
            @NotNull(message = "vehicleType is required") VehicleType vehicleType,
            String model,
            String color
    ) {}

    public record VehicleResponse(
            Long id, Long userId, String licensePlate, VehicleType vehicleType,
            String model, String color, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {}
}
