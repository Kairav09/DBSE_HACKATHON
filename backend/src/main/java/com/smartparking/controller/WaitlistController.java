package com.smartparking.controller;

import com.smartparking.dto.ReservationDtos;
import com.smartparking.dto.WaitlistDtos;
import com.smartparking.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
@PreAuthorize("hasRole('USER')")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    public ResponseEntity<WaitlistDtos.WaitlistResponse> join(@Valid @RequestBody WaitlistDtos.WaitlistRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.join(req));
    }

    @GetMapping("/my")
    public ResponseEntity<List<WaitlistDtos.WaitlistResponse>> my() {
        return ResponseEntity.ok(waitlistService.myEntries());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WaitlistDtos.WaitlistResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(waitlistService.cancel(id));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ReservationDtos.ReservationResponse> claim(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.claim(id));
    }
}
