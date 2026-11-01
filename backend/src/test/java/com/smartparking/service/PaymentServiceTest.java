package com.smartparking.service;

import com.smartparking.dto.PaymentDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Payment;
import com.smartparking.model.PaymentStatus;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.Role;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import com.smartparking.repository.PaymentRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** v3 simulated payments: pay flow + pay-twice guard. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    private Reservation reservation;
    private Payment payment;

    @BeforeEach
    void setUp() {
        User user = new User("Test User", "user1@example.com", "hash", "9999999999", Role.USER);
        user.setId(1L);
        ParkingLocation location = new ParkingLocation("KL University Main Gate Parking",
                "Bachupally Campus, Main Gate", "Hyderabad", new BigDecimal("30.00"), true);
        location.setId(1L);
        ParkingSlot slot = new ParkingSlot(location, "A-01", VehicleType.FOUR_WHEELER, true);
        slot.setId(11L);
        Vehicle vehicle = new Vehicle(user, "TS09AB1234", VehicleType.FOUR_WHEELER, "Hyundai Creta", "White");
        vehicle.setId(5L);
        reservation = new Reservation(user, vehicle, slot,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2),
                ReservationStatus.RESERVED, new BigDecimal("60.00"));
        reservation.setId(7L);
        payment = new Payment(reservation, new BigDecimal("60.00"), PaymentStatus.PENDING);
        payment.setId(3L);

        when(reservationService.findOwnedOrAdminOrThrow(7L)).thenReturn(reservation);
        when(paymentRepository.findByReservationId(7L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void pay_pendingPayment_becomesPaidWithTxnRef() {
        PaymentDtos.PaymentResponse res = paymentService.pay(7L);
        assertEquals("PAID", res.status());
        assertEquals("SIMULATED_CARD", res.method());
        assertNotNull(res.transactionRef());
        assertTrue(res.transactionRef().matches("TXN-[A-Z0-9]{8}"),
                "txn ref shape: " + res.transactionRef());
        assertNotNull(res.paidAt());
        assertEquals(0, new BigDecimal("60.00").compareTo(res.amount()));
    }

    @Test
    void pay_alreadyPaid_rejected409() {
        payment.setStatus(PaymentStatus.PAID);
        ApiException ex = assertThrows(ApiException.class, () -> paymentService.pay(7L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already paid"));
    }
}
