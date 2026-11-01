package com.smartparking.controller;

import com.smartparking.dto.LocationDtos;
import com.smartparking.dto.SlotDtos;
import com.smartparking.service.LocationService;
import com.smartparking.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LocationController {

    private final LocationService locationService;
    private final SlotService slotService;

    public LocationController(LocationService locationService, SlotService slotService) {
        this.locationService = locationService;
        this.slotService = slotService;
    }

    @GetMapping("/api/locations")
    public ResponseEntity<List<LocationDtos.LocationResponse>> list() {
        return ResponseEntity.ok(locationService.list());
    }

    @GetMapping("/api/locations/{id}")
    public ResponseEntity<LocationDtos.LocationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.get(id));
    }

    @PostMapping("/api/locations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocationDtos.LocationResponse> create(@Valid @RequestBody LocationDtos.LocationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.create(req));
    }

    @PutMapping("/api/locations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocationDtos.LocationResponse> update(@PathVariable Long id,
                                                                  @Valid @RequestBody LocationDtos.LocationRequest req) {
        return ResponseEntity.ok(locationService.update(id, req));
    }

    @DeleteMapping("/api/locations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Slots nested under a location (docs/API.md §3) ----

    @GetMapping("/api/locations/{locationId}/slots")
    public ResponseEntity<List<SlotDtos.SlotResponse>> slotsByLocation(@PathVariable Long locationId) {
        return ResponseEntity.ok(slotService.listByLocation(locationId));
    }

    @PostMapping("/api/locations/{locationId}/slots")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlotDtos.SlotResponse> createSlot(@PathVariable Long locationId,
                                                            @Valid @RequestBody SlotDtos.SlotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotService.create(locationId, req));
    }
}
