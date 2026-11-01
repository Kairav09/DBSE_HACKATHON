package com.smartparking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDtos {

    public record PaymentResponse(
            Long id, Long reservationId, BigDecimal amount,
            String status, String method, String transactionRef, LocalDateTime paidAt
    ) {}
}
