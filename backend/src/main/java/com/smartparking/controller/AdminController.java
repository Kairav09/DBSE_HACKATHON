package com.smartparking.controller;

import com.smartparking.dto.DashboardDtos;
import com.smartparking.dto.PromoDtos;
import com.smartparking.dto.WaitlistDtos;
import com.smartparking.model.ReservationStatus;
import com.smartparking.service.DashboardService;
import com.smartparking.service.PromoService;
import com.smartparking.service.ReservationService;
import com.smartparking.service.WaitlistService;
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
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final DashboardService dashboardService;
    private final ReservationService reservationService;
    private final WaitlistService waitlistService;
    private final PromoService promoService;

    public AdminController(DashboardService dashboardService,
                           ReservationService reservationService,
                           WaitlistService waitlistService,
                           PromoService promoService) {
        this.dashboardService = dashboardService;
        this.reservationService = reservationService;
        this.waitlistService = waitlistService;
        this.promoService = promoService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDtos.DashboardResponse> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }

    /** v3: CSV export of reservations — same filters as GET /api/reservations (admin list). */
    @GetMapping(value = "/reservations/export", produces = "text/csv")
    public ResponseEntity<String> exportReservations(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String csv = reservationService.exportCsv(locationId, status, date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reservations.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/waitlist")
    public ResponseEntity<List<WaitlistDtos.WaitlistResponse>> waitlist() {
        return ResponseEntity.ok(waitlistService.listForAdmin());
    }

    @GetMapping("/promos")
    public ResponseEntity<List<PromoDtos.PromoResponse>> promos() {
        return ResponseEntity.ok(promoService.list());
    }

    @PostMapping("/promos")
    public ResponseEntity<PromoDtos.PromoResponse> createPromo(@Valid @RequestBody PromoDtos.PromoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoService.create(req));
    }

    @PostMapping("/promos/{id}/deactivate")
    public ResponseEntity<PromoDtos.PromoResponse> deactivatePromo(@PathVariable Long id) {
        return ResponseEntity.ok(promoService.deactivate(id));
    }
}
