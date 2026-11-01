package com.smartparking.dto;

import java.math.BigDecimal;

public class PricingDtos {

    /** Response of GET /api/pricing/quote (docs/API_V3_CONTRACT.md). */
    public record QuoteResponse(
            Long slotId, Long locationId,
            long hours, BigDecimal hourlyRate, BigDecimal baseAmount,
            BigDecimal surgeMultiplier, String surgeReason,
            String promoCode, boolean promoApplied, String promoMessage,
            BigDecimal discountAmount, BigDecimal totalAmount
    ) {}
}
