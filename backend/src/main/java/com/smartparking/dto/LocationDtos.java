package com.smartparking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LocationDtos {

    public record LocationRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "address is required") String address,
            String city,
            @NotNull(message = "hourlyRate is required") @DecimalMin(value = "0.0", message = "hourlyRate must be >= 0") BigDecimal hourlyRate,
            Boolean isActive
    ) {}

    public record LocationResponse(
            Long id, String name, String address, String city,
            BigDecimal hourlyRate, Boolean isActive,
            // v3: review aggregates (averageRating is null when reviewCount is 0)
            Double averageRating, long reviewCount,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {}
}
