package com.smartparking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ReviewDtos {

    public record ReviewRequest(
            @NotNull(message = "reservationId is required") Long reservationId,
            @NotNull(message = "rating is required") @Min(value = 1, message = "rating must be between 1 and 5") @Max(value = 5, message = "rating must be between 1 and 5") Integer rating,
            @Size(max = 500, message = "comment must be at most 500 characters") String comment
    ) {}

    public record ReviewResponse(
            Long id, String userName, int rating, String comment, LocalDateTime createdAt
    ) {}
}
