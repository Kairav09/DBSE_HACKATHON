package com.smartparking.dto;

import java.time.LocalDateTime;

public class NotificationDtos {

    public record NotificationResponse(
            Long id, String type, String title, String message,
            Long relatedId, boolean read, LocalDateTime createdAt
    ) {}

    public record UnreadCountResponse(long count) {}
}
