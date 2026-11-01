package com.smartparking.controller;

import com.smartparking.dto.VehicleDtos;
import com.smartparking.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) { this.vehicleService = vehicleService; }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<VehicleDtos.VehicleResponse>> my() {
        return ResponseEntity.ok(vehicleService.myVehicles());
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VehicleDtos.VehicleResponse> create(@Valid @RequestBody VehicleDtos.VehicleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleDtos.VehicleResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VehicleDtos.VehicleResponse> update(@PathVariable Long id,
                                                              @Valid @RequestBody VehicleDtos.VehicleRequest req) {
        return ResponseEntity.ok(vehicleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
