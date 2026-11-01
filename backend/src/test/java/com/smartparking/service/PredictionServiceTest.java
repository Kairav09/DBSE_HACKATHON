package com.smartparking.service;

import com.smartparking.dto.PredictionDtos;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the in-Java prediction fallback
 * ({@link PredictionService#javaFallback}, package-private — this test sits in
 * the same package on purpose).
 *
 * Fallback formula (from the production code): for each hour, count the
 * historical (non-CANCELLED, already filtered by the repository query)
 * reservations active in that hour on their own start date, divide by the
 * number of distinct history days, then by the location's total slots, x 100,
 * rounded to 1 decimal and capped at 100. No history => flat 10% profile.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredictionServiceTest {

    @Mock
    private ParkingSlotRepository slotRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private LocationService locationService;

    private PredictionService predictionService;
    private ParkingLocation location;

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(slotRepository, reservationRepository,
                locationService, "http://localhost:8000");
        location = new ParkingLocation("KL University Main Gate Parking",
                "Bachupally Campus, Main Gate", "Hyderabad", new BigDecimal("30.00"), true);
        location.setId(1L);
    }

    private Reservation history(LocalDate day, int startHour, int endHour, ReservationStatus status) {
        return new Reservation(new User(), new Vehicle(), new ParkingSlot(),
                day.atTime(startHour, 0), day.atTime(endHour, 0), status, BigDecimal.ZERO);
    }

    @Test
    void javaFallback_hourlyAverageAndPeakHour() {
        LocalDate day1 = LocalDate.of(2026, 10, 20);
        LocalDate day2 = LocalDate.of(2026, 10, 21);
        // 4 slots, 2 distinct history days.
        //  hour 17: day1 17-19 + day2 17-19 active        -> (2/2)/4 x 100 = 25.0
        //  hour 18: those two + day1 18-19 active          -> (3/2)/4 x 100 = 37.5  (peak)
        //  hour  3: nothing                                -> 0.0
        when(slotRepository.countByLocationId(1L)).thenReturn(4L);
        when(reservationRepository.findByLocationIdAndStatusNot(1L, ReservationStatus.CANCELLED))
                .thenReturn(List.of(
                        history(day1, 17, 19, ReservationStatus.COMPLETED),
                        history(day2, 17, 19, ReservationStatus.COMPLETED),
                        history(day1, 18, 19, ReservationStatus.COMPLETED)));

        PredictionDtos.PredictionResponse res =
                predictionService.javaFallback(location, LocalDate.of(2026, 10, 31));

        assertEquals(24, res.hourlyPredictions().size());
        assertEquals(25.0, res.hourlyPredictions().get(17).predictedOccupancyPercent(), 0.001);
        assertEquals(37.5, res.hourlyPredictions().get(18).predictedOccupancyPercent(), 0.001);
        assertEquals(0.0, res.hourlyPredictions().get(3).predictedOccupancyPercent(), 0.001);
        assertEquals(18, res.peakHour());
        assertEquals(37.5, res.peakOccupancyPercent(), 0.001);
    }

    @Test
    void javaFallback_noHistory_flatTenPercentProfile() {
        when(slotRepository.countByLocationId(1L)).thenReturn(16L);
        when(reservationRepository.findByLocationIdAndStatusNot(1L, ReservationStatus.CANCELLED))
                .thenReturn(List.of());

        PredictionDtos.PredictionResponse res =
                predictionService.javaFallback(location, LocalDate.of(2026, 10, 31));

        assertEquals(24, res.hourlyPredictions().size());
        for (PredictionDtos.HourlyPrediction h : res.hourlyPredictions()) {
            assertEquals(10.0, h.predictedOccupancyPercent(), 0.001,
                    "hour " + h.hour() + " should be the flat low-confidence 10%");
        }
        assertEquals(10.0, res.peakOccupancyPercent(), 0.001);
        // First hour wins the flat tie (peak only moves on a strictly greater value).
        assertEquals(0, res.peakHour());
    }
}
