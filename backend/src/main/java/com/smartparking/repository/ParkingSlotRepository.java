package com.smartparking.repository;

import com.smartparking.model.ParkingSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findByLocationIdOrderBySlotCodeAsc(Long locationId);
    List<ParkingSlot> findByLocationIdAndIsActiveTrueOrderBySlotCodeAsc(Long locationId);
    Optional<ParkingSlot> findByLocationIdAndSlotCode(Long locationId, String slotCode);
    boolean existsByLocationIdAndSlotCode(Long locationId, String slotCode);
    long countByIsActiveTrue();
    long countByLocationId(Long locationId);
}
