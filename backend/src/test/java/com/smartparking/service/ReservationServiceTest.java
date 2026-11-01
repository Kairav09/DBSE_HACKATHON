package com.smartparking.service;

import com.smartparking.dto.ReservationDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.Role;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import com.smartparking.model.PromoCode;
import com.smartparking.model.PromoType;
import com.smartparking.repository.PaymentRepository;
import com.smartparking.repository.PromoCodeRepository;
import com.smartparking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReservationService}: fee calculation, the
 * double-booking overlap check, window validation, and lifecycle guards.
 *
 * The overlap geometry itself lives in the repository JPQL
 * (existing.start &lt; newEnd AND existing.end &gt; newStart, restricted to the
 * statuses the service passes). To exercise the service end-to-end without a
 * database, the repository mock answers existsOverlapping() by applying that
 * exact predicate to a fixture reservation — so these tests verify the
 * service passes the blocking statuses (RESERVED, OCCUPIED) and rejects or
 * allows exactly as the real query would.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

    private static final BigDecimal HOURLY_RATE = new BigDecimal("30.00");
    /** A safely-future day so the "start cannot be in the past" rule never trips. */
    private static final LocalDate BASE_DAY = LocalDate.now().plusDays(2);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private SlotService slotService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PricingService pricingService;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WaitlistService waitlistService;

    @InjectMocks
    private ReservationService reservationService;

    private User user;
    private Vehicle vehicle;
    private ParkingSlot slot;

    private static LocalDateTime at(int hour) {
        return BASE_DAY.atTime(hour, 0);
    }

    private static LocalDateTime at(int hour, int minute) {
        return BASE_DAY.atTime(hour, minute);
    }

    @BeforeEach
    void setUp() {
        ParkingLocation location = new ParkingLocation("KL University Main Gate Parking",
                "Bachupally Campus, Main Gate", "Hyderabad", HOURLY_RATE, true);
        location.setId(1L);

        slot = new ParkingSlot(location, "A-01", VehicleType.FOUR_WHEELER, true);
        slot.setId(11L);

        user = new User("Test User", "user1@example.com", "hash", "9999999999", Role.USER);
        user.setId(1L);

        vehicle = new Vehicle(user, "TS09AB1234", VehicleType.FOUR_WHEELER, "Hyundai Creta", "White");
        vehicle.setId(5L);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(vehicleService.findOrThrow(5L)).thenReturn(vehicle);
        when(slotService.findOrThrow(11L)).thenReturn(slot);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // v3 pricing: default stub = the plain v1/v2 price (base fee, x1.00 surge,
        // no promo) so the original fee expectations below still hold exactly.
        // Individual promo tests re-stub compute() with their own PricingResult.
        when(pricingService.compute(any(ParkingSlot.class), any(LocalDateTime.class),
                any(LocalDateTime.class), nullable(String.class), anyBoolean()))
                .thenAnswer(inv -> {
                    ParkingSlot s = inv.getArgument(0);
                    LocalDateTime st = inv.getArgument(1);
                    LocalDateTime en = inv.getArgument(2);
                    BigDecimal base = ReservationService.computeFee(st, en, s.getLocation().getHourlyRate());
                    return new PricingService.PricingResult(
                            ReservationService.billableHours(st, en), s.getLocation().getHourlyRate(),
                            base, new BigDecimal("1.00"), "Standard pricing",
                            null, false, null, new BigDecimal("0.00"), base);
                });
        when(paymentRepository.save(any(com.smartparking.model.Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByReservationId(any()))
                .thenReturn(java.util.Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ReservationDtos.ReservationRequest request(LocalDateTime start, LocalDateTime end) {
        return new ReservationDtos.ReservationRequest(5L, 11L, start, end);
    }

    private Reservation existing(ReservationStatus status, LocalDateTime start, LocalDateTime end) {
        Reservation r = new Reservation(user, vehicle, slot, start, end, status, new BigDecimal("60.00"));
        r.setId(99L);
        return r;
    }

    /** Makes existsOverlapping() answer with the real JPQL predicate applied to the fixture. */
    private void stubOverlapAgainst(Reservation existing) {
        when(reservationRepository.existsOverlapping(eq(11L), anyCollection(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    Collection<ReservationStatus> statuses = inv.getArgument(1);
                    LocalDateTime start = inv.getArgument(2);
                    LocalDateTime end = inv.getArgument(3);
                    return statuses.contains(existing.getStatus())
                            && existing.getStartTime().isBefore(end)
                            && existing.getEndTime().isAfter(start);
                });
    }

    // ---------- Fee calculation (computeFee: ceil hours, min 1, x hourly rate) ----------

    @Test
    void computeFee_exactlyOneHour_chargesOneHour() {
        BigDecimal fee = ReservationService.computeFee(at(10), at(11), HOURLY_RATE);
        assertEquals(0, new BigDecimal("30.00").compareTo(fee));
    }

    @Test
    void computeFee_partialHour_roundsUpToNextBillableHour() {
        // 2h05m -> 3 billable hours -> 3 x 30.00
        BigDecimal fee = ReservationService.computeFee(at(10), at(12, 5), HOURLY_RATE);
        assertEquals(0, new BigDecimal("90.00").compareTo(fee));
    }

    // ---------- Overlap check (double-booking prevention, 409) ----------

    @Test
    void create_identicalWindowOverlap_rejected409() {
        stubOverlapAgainst(existing(ReservationStatus.RESERVED, at(10), at(12)));
        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.create(request(at(10), at(12))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already booked"));
    }

    @Test
    void create_partialOverlapAtStart_rejected409() {
        stubOverlapAgainst(existing(ReservationStatus.RESERVED, at(10), at(12)));
        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.create(request(at(9), at(11))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void create_partialOverlapAtEnd_rejected409() {
        stubOverlapAgainst(existing(ReservationStatus.OCCUPIED, at(10), at(12)));
        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.create(request(at(11), at(13))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void create_adjacentWindow_allowed_andFeeSnapshottedFromLocationRate() {
        // New window starts exactly when the existing one ends: [start, end) => no overlap.
        stubOverlapAgainst(existing(ReservationStatus.RESERVED, at(10), at(12)));
        ReservationDtos.ReservationResponse res = reservationService.create(request(at(12), at(14)));
        assertNotNull(res);
        assertEquals("RESERVED", res.status());
        // 2h x the location's hourly rate (30.00) — snapshot taken at booking time.
        assertEquals(0, new BigDecimal("60.00").compareTo(res.feeAmount()));
        assertEquals("A-01", res.slotCode());
    }

    @Test
    void create_cancelledReservationInWindow_doesNotBlock() {
        // Same window, but the existing reservation is CANCELLED: the service only
        // passes RESERVED/OCCUPIED as blocking, so the query answer is false.
        stubOverlapAgainst(existing(ReservationStatus.CANCELLED, at(10), at(12)));
        ReservationDtos.ReservationResponse res = reservationService.create(request(at(10), at(12)));
        assertNotNull(res);
        assertEquals("RESERVED", res.status());
    }

    // ---------- Window validation (400) ----------

    @Test
    void create_endNotAfterStart_rejected400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.create(request(at(12), at(12))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("endTime must be after startTime"));
    }

    @Test
    void create_startTooFarInPast_rejected400() {
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.create(request(start, start.plusHours(3))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("startTime cannot be in the past"));
    }

    // ---------- Lifecycle guards (409 on illegal transitions) ----------

    private Reservation lifecycleFixture(Long id, ReservationStatus status) {
        Reservation r = existing(status, at(10), at(12));
        r.setId(id);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void cancel_occupiedReservation_rejected409() {
        lifecycleFixture(31L, ReservationStatus.OCCUPIED);
        ApiException ex = assertThrows(ApiException.class, () -> reservationService.cancel(31L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only a RESERVED reservation can be cancelled"));
    }

    @Test
    void entry_cancelledReservation_rejected409() {
        lifecycleFixture(32L, ReservationStatus.CANCELLED);
        ApiException ex = assertThrows(ApiException.class, () -> reservationService.entry(32L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Entry is only allowed from RESERVED"));
    }

    @Test
    void exit_reservedReservation_rejected409() {
        lifecycleFixture(33L, ReservationStatus.RESERVED);
        ApiException ex = assertThrows(ApiException.class, () -> reservationService.exit(33L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Exit is only allowed from OCCUPIED"));
    }

    // ---------- v3: promo codes on create ----------

    @Test
    void create_withValidPercentPromo_discountAppliedAndUsedCountIncremented() {
        stubOverlapAgainst(existing(ReservationStatus.CANCELLED, at(10), at(12))); // window free
        PromoCode promo = new PromoCode("WELCOME10", PromoType.PERCENT, new BigDecimal("10.00"), 1000, null, true);
        promo.setUsedCount(0);
        // base 60.00 (2h x 30), surge x1.00, 10% off -> discount 6.00, total 54.00
        when(pricingService.compute(any(ParkingSlot.class), any(LocalDateTime.class),
                any(LocalDateTime.class), nullable(String.class), anyBoolean()))
                .thenReturn(new PricingService.PricingResult(2, HOURLY_RATE, new BigDecimal("60.00"),
                        new BigDecimal("1.00"), "Standard pricing", promo, true, "10% off applied",
                        new BigDecimal("6.00"), new BigDecimal("54.00")));

        ReservationDtos.ReservationRequest req = new ReservationDtos.ReservationRequest(5L, 11L, at(10), at(12), "WELCOME10");
        ReservationDtos.ReservationResponse res = reservationService.create(req);

        assertEquals(0, new BigDecimal("54.00").compareTo(res.feeAmount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(res.baseAmount()));
        assertEquals(0, new BigDecimal("6.00").compareTo(res.discountAmount()));
        assertEquals("WELCOME10", res.promoCode());
        assertEquals("PENDING", res.paymentStatus());
        assertEquals(1, promo.getUsedCount());
    }

    @Test
    void create_withInvalidPromo_rejected400() {
        stubOverlapAgainst(existing(ReservationStatus.CANCELLED, at(10), at(12))); // window free
        when(pricingService.compute(any(ParkingSlot.class), any(LocalDateTime.class),
                any(LocalDateTime.class), nullable(String.class), anyBoolean()))
                .thenThrow(ApiException.badRequest("Promo code has expired: OLD10"));
        ReservationDtos.ReservationRequest req = new ReservationDtos.ReservationRequest(5L, 11L, at(10), at(12), "OLD10");
        ApiException ex = assertThrows(ApiException.class, () -> reservationService.create(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("expired"));
    }

    // ---------- v3: reschedule ----------

    @Test
    void reschedule_overlappingOnlyItself_allowed() {
        // The reservation already occupies 10:00-12:00; moving it to 11:00-13:00
        // overlaps ONLY its own old window, which the exclude-self check ignores.
        Reservation r = lifecycleFixture(41L, ReservationStatus.RESERVED);
        ReservationDtos.ReservationResponse res = reservationService.reschedule(41L,
                new ReservationDtos.RescheduleRequest(at(11), at(13)));
        assertEquals(at(11), res.startTime());
        assertEquals(at(13), res.endTime());
        // Fee recomputed for the new window: 2h x 30 = 60.00 at x1.00.
        assertEquals(0, new BigDecimal("60.00").compareTo(res.feeAmount()));
        assertEquals("RESERVED", res.status());
    }

    @Test
    void reschedule_removesPromoAndGivesBackUsedCount() {
        Reservation r = lifecycleFixture(42L, ReservationStatus.RESERVED);
        PromoCode promo = new PromoCode("WELCOME10", PromoType.PERCENT, new BigDecimal("10.00"), 1000, null, true);
        promo.setUsedCount(3);
        r.setPromoCode(promo);
        r.setDiscountAmount(new BigDecimal("6.00"));

        ReservationDtos.ReservationResponse res = reservationService.reschedule(42L,
                new ReservationDtos.RescheduleRequest(at(12), at(14)));

        assertEquals(null, res.promoCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(res.discountAmount()));
        assertEquals(2, promo.getUsedCount());
    }

    @Test
    void reschedule_completedReservation_rejected409() {
        lifecycleFixture(43L, ReservationStatus.COMPLETED);
        ApiException ex = assertThrows(ApiException.class, () -> reservationService.reschedule(43L,
                new ReservationDtos.RescheduleRequest(at(12), at(14))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }
}
