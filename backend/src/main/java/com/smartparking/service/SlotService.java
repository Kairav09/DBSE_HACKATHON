package com.smartparking.service;

import com.smartparking.dto.SlotDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.SlotStatus;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SlotService {

    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final LocationService locationService;

    public SlotService(ParkingSlotRepository slotRepository,
                       ReservationRepository reservationRepository,
                       LocationService locationService) {
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
        this.locationService = locationService;
    }

    public ParkingSlot findOrThrow(Long id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Parking slot not found: " + id));
    }

    /**
     * Derived live status (docs/ARCHITECTURE.md §4):
     * OCCUPIED if any OCCUPIED reservation exists; else RESERVED if a RESERVED
     * reservation with end_time >= now exists; else AVAILABLE.
     */
    public SlotStatus deriveStatus(Long slotId) {
        return deriveStatus(slotId, LocalDateTime.now());
    }

    public SlotStatus deriveStatus(Long slotId, LocalDateTime now) {
        if (reservationRepository.existsBySlotIdAndStatus(slotId, ReservationStatus.OCCUPIED)) {
            return SlotStatus.OCCUPIED;
        }
        if (reservationRepository.existsBySlotIdAndStatusAndEndTimeAfter(slotId, ReservationStatus.RESERVED, now)) {
            return SlotStatus.RESERVED;
        }
        return SlotStatus.AVAILABLE;
    }

    public SlotDtos.SlotResponse toResponse(ParkingSlot slot) {
        return new SlotDtos.SlotResponse(slot.getId(), slot.getLocation().getId(), slot.getSlotCode(),
                slot.getVehicleType(), slot.getIsActive(), deriveStatus(slot.getId()).name());
    }

    @Transactional(readOnly = true)
    public List<SlotDtos.SlotResponse> listByLocation(Long locationId) {
        locationService.findOrThrow(locationId);
        return slotRepository.findByLocationIdOrderBySlotCodeAsc(locationId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SlotDtos.SlotResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    /** Slots of the location with NO overlapping RESERVED/OCCUPIED reservation in [start, end). */
    @Transactional(readOnly = true)
    public List<SlotDtos.SlotResponse> availability(Long locationId, LocalDateTime start, LocalDateTime end) {
        locationService.findOrThrow(locationId);
        if (start == null || end == null) {
            throw ApiException.badRequest("startTime and endTime are required");
        }
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("endTime must be after startTime");
        }
        List<ReservationStatus> blocking = List.of(ReservationStatus.RESERVED, ReservationStatus.OCCUPIED);
        return slotRepository.findByLocationIdAndIsActiveTrueOrderBySlotCodeAsc(locationId).stream()
                .filter(slot -> !reservationRepository.existsOverlapping(slot.getId(), blocking, start, end))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SlotDtos.SlotResponse create(Long locationId, SlotDtos.SlotRequest req) {
        ParkingLocation location = locationService.findOrThrow(locationId);
        if (slotRepository.existsByLocationIdAndSlotCode(locationId, req.slotCode())) {
            throw ApiException.conflict("Slot code " + req.slotCode() + " already exists in this location");
        }
        ParkingSlot slot = new ParkingSlot(location, req.slotCode(), req.vehicleType(),
                req.isActive() == null ? Boolean.TRUE : req.isActive());
        return toResponse(slotRepository.save(slot));
    }

    @Transactional
    public SlotDtos.SlotResponse update(Long id, SlotDtos.SlotRequest req) {
        ParkingSlot slot = findOrThrow(id);
        if (!slot.getSlotCode().equals(req.slotCode())
                && slotRepository.existsByLocationIdAndSlotCode(slot.getLocation().getId(), req.slotCode())) {
            throw ApiException.conflict("Slot code " + req.slotCode() + " already exists in this location");
        }
        slot.setSlotCode(req.slotCode());
        slot.setVehicleType(req.vehicleType());
        if (req.isActive() != null) slot.setIsActive(req.isActive());
        return toResponse(slotRepository.save(slot));
    }

    @Transactional
    public void delete(Long id) {
        ParkingSlot slot = findOrThrow(id);
        if (reservationRepository.existsBySlotId(id)) {
            throw ApiException.conflict("Slot " + slot.getSlotCode()
                    + " has reservations and cannot be deleted — deactivate it via isActive instead");
        }
        slotRepository.delete(slot);
    }
}
