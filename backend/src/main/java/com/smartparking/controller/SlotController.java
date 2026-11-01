package com.smartparking.controller;

import com.smartparking.dto.SlotDtos;
import com.smartparking.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) { this.slotService = slotService; }

    // NOTE: /availability must be matched before /{id}; Spring handles this because
    // "availability" does not parse as the Long id pattern for the other route's logic —
    // both are declared explicitly here and literal paths take precedence.
    @GetMapping("/availability")
    public ResponseEntity<List<SlotDtos.SlotResponse>> availability(
            @RequestParam Long locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(slotService.availability(locationId, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SlotDtos.SlotResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(slotService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlotDtos.SlotResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody SlotDtos.SlotRequest req) {
        return ResponseEntity.ok(slotService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        slotService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
