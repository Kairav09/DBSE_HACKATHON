package com.smartparking.service;

import com.smartparking.dto.PaymentDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.NotificationType;
import com.smartparking.model.Payment;
import com.smartparking.model.PaymentStatus;
import com.smartparking.model.Reservation;
import com.smartparking.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/** Simulated payments (v3): one PENDING row per reservation, "paid" via /pay. No real gateway. */
@Service
public class PaymentService {

    static final String SIMULATED_METHOD = "SIMULATED_CARD";
    private static final char[] REF_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final PaymentRepository paymentRepository;
    private final ReservationService reservationService;
    private final NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository,
                          ReservationService reservationService,
                          NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.reservationService = reservationService;
        this.notificationService = notificationService;
    }

    public static PaymentDtos.PaymentResponse toResponse(Payment p) {
        return new PaymentDtos.PaymentResponse(
                p.getId(), p.getReservation().getId(), p.getAmount(),
                p.getStatus().name(), p.getMethod(), p.getTransactionRef(), p.getPaidAt());
    }

    static String newTransactionRef(SecureRandom random) {
        StringBuilder sb = new StringBuilder("TXN-");
        for (int i = 0; i < 8; i++) sb.append(REF_CHARS[random.nextInt(REF_CHARS.length)]);
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentResponse getForReservation(Long reservationId) {
        Reservation r = reservationService.findOwnedOrAdminOrThrow(reservationId);
        Payment p = paymentRepository.findByReservationId(r.getId())
                .orElseThrow(() -> ApiException.notFound("No payment found for reservation: " + reservationId));
        return toResponse(p);
    }

    @Transactional
    public PaymentDtos.PaymentResponse pay(Long reservationId) {
        Reservation r = reservationService.findOwnedOrAdminOrThrow(reservationId);
        Payment p = paymentRepository.findByReservationId(r.getId())
                .orElseThrow(() -> ApiException.notFound("No payment found for reservation: " + reservationId));
        if (p.getStatus() == PaymentStatus.PAID) {
            throw ApiException.conflict("Reservation " + reservationId + " is already paid");
        }
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            throw ApiException.conflict("Reservation " + reservationId + " was refunded and cannot be paid");
        }
        p.setStatus(PaymentStatus.PAID);
        p.setMethod(SIMULATED_METHOD);
        p.setTransactionRef(newTransactionRef(random));
        p.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(p);
        notificationService.create(r.getUser(), NotificationType.PAYMENT_DONE,
                "Payment successful",
                "₹" + saved.getAmount().toPlainString() + " paid for reservation #" + r.getId()
                        + " (" + saved.getTransactionRef() + ").",
                r.getId());
        return toResponse(saved);
    }
}
