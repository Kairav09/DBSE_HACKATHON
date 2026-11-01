package com.smartparking.service;

import com.smartparking.dto.PredictionDtos;
import com.smartparking.dto.PricingDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.PromoCode;
import com.smartparking.model.PromoType;
import com.smartparking.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * v3 pricing engine: base = billableHours(ceil, min 1) x hourlyRate, then a
 * surge multiplier from the predicted occupancy at the booking's start hour
 * (>=85% -> x1.50, >=70% -> x1.25, else x1.00), then a promo discount.
 * total = base x surge - discount, floored at 0. The server always recomputes;
 * client-sent amounts are never trusted.
 */
@Service
public class PricingService {

    static final BigDecimal SURGE_PEAK = new BigDecimal("1.50");
    static final BigDecimal SURGE_HIGH = new BigDecimal("1.25");
    static final BigDecimal SURGE_NONE = new BigDecimal("1.00");

    private final PredictionService predictionService;
    private final SlotService slotService;
    private final PromoCodeRepository promoCodeRepository;

    public PricingService(PredictionService predictionService,
                          SlotService slotService,
                          PromoCodeRepository promoCodeRepository) {
        this.predictionService = predictionService;
        this.slotService = slotService;
        this.promoCodeRepository = promoCodeRepository;
    }

    /** Full computation result shared by the quote endpoint and reservation create/reschedule. */
    public record PricingResult(
            long hours, BigDecimal hourlyRate, BigDecimal baseAmount,
            BigDecimal surgeMultiplier, String surgeReason,
            PromoCode promo, boolean promoApplied, String promoMessage,
            BigDecimal discountAmount, BigDecimal totalAmount) {}

    /** Surge tier for a predicted occupancy percentage (contract thresholds). */
    static BigDecimal surgeMultiplierFor(double occupancyPercent) {
        if (occupancyPercent >= 85.0) return SURGE_PEAK;
        if (occupancyPercent >= 70.0) return SURGE_HIGH;
        return SURGE_NONE;
    }

    static String surgeReasonFor(BigDecimal multiplier) {
        if (SURGE_PEAK.compareTo(multiplier) == 0) return "Peak demand surge";
        if (SURGE_HIGH.compareTo(multiplier) == 0) return "High demand surge";
        return "Standard pricing";
    }

    /** Predicted occupancy % for the location at the given hour, via PredictionService (Python or Java fallback). */
    double predictedOccupancyAt(Long locationId, LocalDateTime when) {
        PredictionDtos.PredictionResponse prediction =
                predictionService.predict(locationId, when.toLocalDate());
        return prediction.hourlyPredictions().stream()
                .filter(h -> h.hour() == when.getHour())
                .mapToDouble(PredictionDtos.HourlyPrediction::predictedOccupancyPercent)
                .findFirst().orElse(0.0);
    }

    /**
     * Why a promo code cannot be applied, or null when it is usable.
     * A code is valid only if it exists, is active, is not expired, and has uses left.
     */
    String promoInvalidReason(PromoCode promo) {
        if (promo == null) return "Promo code not recognized";
        if (!Boolean.TRUE.equals(promo.getIsActive())) return "Promo code is not active";
        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "Promo code has expired";
        }
        if (promo.getMaxUses() != null && promo.getUsedCount() >= promo.getMaxUses()) {
            return "Promo code usage limit reached";
        }
        return null;
    }

    /**
     * Computes the price for a slot + window (+ optional promo).
     *
     * @param failOnInvalidPromo true for reservation create (an explicitly typed
     *                           invalid code is a 400); false for the quote
     *                           endpoint (invalid code just yields promoApplied=false).
     */
    @Transactional(readOnly = true)
    public PricingResult compute(ParkingSlot slot, LocalDateTime start, LocalDateTime end,
                                 String promoCode, boolean failOnInvalidPromo) {
        BigDecimal hourlyRate = slot.getLocation().getHourlyRate();
        long hours = ReservationService.billableHours(start, end);
        BigDecimal baseAmount = hourlyRate.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP);

        double occupancy = predictedOccupancyAt(slot.getLocation().getId(), start);
        BigDecimal multiplier = surgeMultiplierFor(occupancy);
        BigDecimal surged = baseAmount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        PromoCode promo = null;
        boolean applied = false;
        String message = null;
        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (promoCode != null && !promoCode.isBlank()) {
            String code = promoCode.trim();
            promo = promoCodeRepository.findByCodeIgnoreCase(code).orElse(null);
            String invalid = promoInvalidReason(promo);
            if (invalid != null) {
                if (failOnInvalidPromo) throw ApiException.badRequest(invalid + ": " + code.toUpperCase());
                message = invalid;
                promo = null;
            } else {
                applied = true;
                if (promo.getType() == PromoType.PERCENT) {
                    discount = surged.multiply(promo.getValue())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    message = promo.getValue().stripTrailingZeros().toPlainString() + "% off applied";
                } else { // FLAT ₹ off, never more than the surged amount
                    discount = promo.getValue().min(surged).setScale(2, RoundingMode.HALF_UP);
                    message = "₹" + promo.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString() + " off applied";
                }
            }
        }

        BigDecimal total = surged.subtract(discount);
        if (total.signum() < 0) total = BigDecimal.ZERO;
        total = total.setScale(2, RoundingMode.HALF_UP);

        return new PricingResult(hours, hourlyRate, baseAmount, multiplier, surgeReasonFor(multiplier),
                promo, applied, message, discount, total);
    }

    /** GET /api/pricing/quote — quote never 400s on a bad promo; it reports promoApplied=false instead. */
    @Transactional(readOnly = true)
    public PricingDtos.QuoteResponse quote(Long slotId, LocalDateTime start, LocalDateTime end, String promoCode) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("endTime must be after startTime");
        }
        ParkingSlot slot = slotService.findOrThrow(slotId);
        PricingResult r = compute(slot, start, end, promoCode, false);
        return new PricingDtos.QuoteResponse(
                slot.getId(), slot.getLocation().getId(),
                r.hours(), r.hourlyRate(), r.baseAmount(),
                r.surgeMultiplier(), r.surgeReason(),
                promoCode, r.promoApplied(), r.promoMessage(),
                r.discountAmount(), r.totalAmount());
    }
}
