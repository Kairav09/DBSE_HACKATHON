package com.smartparking.service;

import com.smartparking.dto.VehicleDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentUserService currentUserService;

    public VehicleService(VehicleRepository vehicleRepository,
                          ReservationRepository reservationRepository,
                          CurrentUserService currentUserService) {
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
        this.currentUserService = currentUserService;
    }

    public static VehicleDtos.VehicleResponse toResponse(Vehicle v) {
        return new VehicleDtos.VehicleResponse(v.getId(), v.getUser().getId(), v.getLicensePlate(),
                v.getVehicleType(), v.getModel(), v.getColor(), v.getCreatedAt(), v.getUpdatedAt());
    }

    public Vehicle findOrThrow(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found: " + id));
    }

    private Vehicle findOwnedOrThrow(Long id) {
        Vehicle v = findOrThrow(id);
        User current = currentUserService.getCurrentUser();
        if (!v.getUser().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw ApiException.forbidden("Vehicle does not belong to the current user");
        }
        return v;
    }

    @Transactional(readOnly = true)
    public List<VehicleDtos.VehicleResponse> myVehicles() {
        User current = currentUserService.getCurrentUser();
        return vehicleRepository.findByUserIdOrderByCreatedAtDesc(current.getId()).stream()
                .map(VehicleService::toResponse).toList();
    }

    @Transactional
    public VehicleDtos.VehicleResponse create(VehicleDtos.VehicleRequest req) {
        User current = currentUserService.getCurrentUser();
        if (vehicleRepository.existsByLicensePlate(req.licensePlate())) {
            throw ApiException.conflict("License plate already registered: " + req.licensePlate());
        }
        Vehicle v = new Vehicle(current, req.licensePlate(), req.vehicleType(), req.model(), req.color());
        return toResponse(vehicleRepository.save(v));
    }

    @Transactional(readOnly = true)
    public VehicleDtos.VehicleResponse get(Long id) {
        return toResponse(findOwnedOrThrow(id));
    }

    @Transactional
    public VehicleDtos.VehicleResponse update(Long id, VehicleDtos.VehicleRequest req) {
        Vehicle v = findOwnedOrThrow(id);
        User current = currentUserService.getCurrentUser();
        if (!v.getUser().getId().equals(current.getId())) {
            // Only the owner may update (ADMIN can read, per docs/API.md §4).
            throw ApiException.forbidden("Only the vehicle owner can update it");
        }
        if (!v.getLicensePlate().equals(req.licensePlate()) && vehicleRepository.existsByLicensePlate(req.licensePlate())) {
            throw ApiException.conflict("License plate already registered: " + req.licensePlate());
        }
        v.setLicensePlate(req.licensePlate());
        v.setVehicleType(req.vehicleType());
        v.setModel(req.model());
        v.setColor(req.color());
        return toResponse(vehicleRepository.save(v));
    }

    @Transactional
    public void delete(Long id) {
        Vehicle v = findOwnedOrThrow(id);
        User current = currentUserService.getCurrentUser();
        if (!v.getUser().getId().equals(current.getId())) {
            throw ApiException.forbidden("Only the vehicle owner can delete it");
        }
        if (reservationRepository.existsByVehicleId(id)) {
            throw ApiException.conflict("Vehicle has reservations and cannot be deleted");
        }
        vehicleRepository.delete(v);
    }
}
