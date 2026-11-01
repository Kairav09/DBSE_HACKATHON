package com.smartparking.service;

import com.smartparking.dto.PredictionDtos;
import com.smartparking.dto.PricingDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.PromoCode;
import com.smartparking.model.PromoType;
import com.smartparking.model.VehicleType;
import com.smartparking.repository.PromoCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * v3 pricing engine: surge tiers from predicted occupancy, promo math.
 * PredictionService is mocked with a fixed hourly profile so the surge
 * thresholds (>=85% -> x1.50, >=70% -> x1.25, else x1.00) are exact.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingServiceTest {

    private static final LocalDate DAY = LocalDate.now().plusDays(2);

    @Mock
    private PredictionService predictionService;
    @Mock
    private SlotService slotService;
    @Mock
    private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private PricingService pricingService;

    private ParkingSlot slot;

    @BeforeEach
    void setUp() {
        ParkingLocation location = new ParkingLocation("KL University Main Gate Parking",
                "Bachupally Campus, Main Gate", "Hyderabad", new BigDecimal("30.00"), true);
        location.setId(1L);
        slot = new ParkingSlot(location, "A-01", VehicleType.FOUR_WHEELER, true);
        slot.setId(11L);
        when(slotService.findOrThrow(11L)).thenReturn(slot);
    }

    private void stubOccupancy(int hour, double pct) {
        List<PredictionDtos.HourlyPrediction> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourly.add(new PredictionDtos.HourlyPrediction(h, h == hour ? pct : 0.0));
        }
        when(predictionService.predict(eq(1L), any(LocalDate.class)))
                .thenReturn(new PredictionDtos.PredictionResponse(1L, "loc", DAY, hour, pct, hourly));
    }

    private PricingService.PricingResult compute(int startHour, String promo, boolean failOnInvalid) {
        return pricingService.compute(slot, DAY.atTime(startHour, 0), DAY.atTime(startHour + 2, 0), promo, failOnInvalid);
    }

    // ---------- Surge tiers (2h booking: base = 2 x 30.00 = 60.00) ----------

    @Test
    void surge_peakOccupancy85Plus_multiplier150() {
        stubOccupancy(18, 90.0);
        PricingService.PricingResult r = compute(18, null, false);
        assertEquals(0, new BigDecimal("1.50").compareTo(r.surgeMultiplier()));
        assertEquals("Peak demand surge", r.surgeReason());
        assertEquals(0, new BigDecimal("90.00").compareTo(r.totalAmount()));
    }

    @Test
    void surge_highOccupancy70to84_multiplier125() {
        stubOccupancy(18, 75.0);
        PricingService.PricingResult r = compute(18, null, false);
        assertEquals(0, new BigDecimal("1.25").compareTo(r.surgeMultiplier()));
        assertEquals("High demand surge", r.surgeReason());
        assertEquals(0, new BigDecimal("75.00").compareTo(r.totalAmount()));
    }

    @Test
    void surge_lowOccupancy_multiplier100() {
        stubOccupancy(10, 42.0);
        PricingService.PricingResult r = compute(10, null, false);
        assertEquals(0, new BigDecimal("1.00").compareTo(r.surgeMultiplier()));
        assertEquals(0, new BigDecimal("60.00").compareTo(r.totalAmount()));
    }

    // ---------- Promo math ----------

    @Test
    void promo_percentDiscount_appliesToSurgedAmount() {
        stubOccupancy(18, 90.0); // surged = 90.00
        PromoCode promo = new PromoCode("WELCOME10", PromoType.PERCENT, new BigDecimal("10.00"), 1000, null, true);
        when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(promo));
        PricingService.PricingResult r = compute(18, "WELCOME10", false);
        assertTrue(r.promoApplied());
        assertEquals(0, new BigDecimal("9.00").compareTo(r.discountAmount())); // 10% of 90
        assertEquals(0, new BigDecimal("81.00").compareTo(r.totalAmount()));
    }

    @Test
    void promo_flatDiscount_subtractsRupees() {
        stubOccupancy(10, 10.0); // surged = 60.00
        PromoCode promo = new PromoCode("FLAT50", PromoType.FLAT, new BigDecimal("50.00"), 1000, null, true);
        when(promoCodeRepository.findByCodeIgnoreCase("FLAT50")).thenReturn(Optional.of(promo));
        PricingDtos.QuoteResponse q = pricingService.quote(11L, DAY.atTime(10, 0), DAY.atTime(12, 0), "FLAT50");
        assertTrue(q.promoApplied());
        assertEquals(0, new BigDecimal("50.00").compareTo(q.discountAmount()));
        assertEquals(0, new BigDecimal("10.00").compareTo(q.totalAmount()));
    }

    @Test
    void promo_unknownCode_quoteSoftFails() {
        stubOccupancy(10, 10.0);
        when(promoCodeRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        PricingDtos.QuoteResponse q = pricingService.quote(11L, DAY.atTime(10, 0), DAY.atTime(12, 0), "NOPE");
        assertFalse(q.promoApplied());
        assertTrue(q.promoMessage().contains("not recognized"));
        assertEquals(0, new BigDecimal("60.00").compareTo(q.totalAmount()));
    }

    @Test
    void promo_expiredCode_createFails400() {
        stubOccupancy(10, 10.0);
        PromoCode expired = new PromoCode("OLD10", PromoType.PERCENT, new BigDecimal("10.00"), 1000,
                LocalDateTime.now().minusDays(1), true);
        when(promoCodeRepository.findByCodeIgnoreCase("OLD10")).thenReturn(Optional.of(expired));
        ApiException ex = assertThrows(ApiException.class, () -> compute(10, "OLD10", true));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void promo_exhaustedCode_quoteSoftFails() {
        stubOccupancy(10, 10.0);
        PromoCode usedUp = new PromoCode("GONE20", PromoType.PERCENT, new BigDecimal("20.00"), 5, null, true);
        usedUp.setUsedCount(5);
        when(promoCodeRepository.findByCodeIgnoreCase("GONE20")).thenReturn(Optional.of(usedUp));
        PricingService.PricingResult r = compute(10, "GONE20", false);
        assertFalse(r.promoApplied());
        assertTrue(r.promoMessage().contains("usage limit"));
    }
}
