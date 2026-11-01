package com.smartparking.controller;

import com.smartparking.dto.PaymentDtos;
import com.smartparking.dto.ReservationDtos;
import com.smartparking.model.ReservationStatus;
import com.smartparking.service.PaymentService;
import com.smartparking.service.QrService;
import com.smartparking.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final QrService qrService;

    public ReservationController(ReservationService reservationService,
                                 PaymentService paymentService,
                                 QrService qrService) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.qrService = qrService;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReservationDtos.ReservationResponse> create(@Valid @RequestBody ReservationDtos.ReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(req));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ReservationDtos.ReservationResponse>> my(
            @RequestParam(required = false) ReservationStatus status) {
        return ResponseEntity.ok(reservationService.myReservations(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationDtos.ReservationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationDtos.ReservationResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }

    @PostMapping("/{id}/entry")
    public ResponseEntity<ReservationDtos.ReservationResponse> entry(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.entry(id));
    }

    @PostMapping("/{id}/exit")
    public ResponseEntity<ReservationDtos.ReservationResponse> exit(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.exit(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReservationDtos.ReservationResponse> reschedule(
            @PathVariable Long id, @Valid @RequestBody ReservationDtos.RescheduleRequest req) {
        return ResponseEntity.ok(reservationService.reschedule(id, req));
    }

    @GetMapping("/{id}/payment")
    public ResponseEntity<PaymentDtos.PaymentResponse> payment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getForReservation(id));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentDtos.PaymentResponse> pay(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.pay(id));
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(qrService.qrPngForReservation(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReservationDtos.ReservationResponse>> listForAdmin(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.listForAdmin(locationId, status, date));
    }
}
