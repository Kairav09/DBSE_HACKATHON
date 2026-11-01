package com.smartparking.dto;

import com.smartparking.model.PromoType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PromoDtos {

    public record PromoRequest(
            @NotBlank(message = "code is required") String code,
            @NotNull(message = "type is required") PromoType type,
            @NotNull(message = "value is required") @DecimalMin(value = "0.01", message = "value must be > 0") BigDecimal value,
            Integer maxUses,
            LocalDateTime expiresAt,
            Boolean isActive
    ) {}

    public record PromoResponse(
            Long id, String code, String type, BigDecimal value,
            Integer maxUses, int usedCount, LocalDateTime expiresAt,
            Boolean isActive, LocalDateTime createdAt
    ) {}
}
