package com.smartparking.service;

import com.smartparking.dto.DashboardDtos;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.SlotStatus;
import com.smartparking.repository.ParkingLocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DashboardService {

    private final ParkingLocationRepository locationRepository;
    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final SlotService slotService;

    public DashboardService(ParkingLocationRepository locationRepository,
                            ParkingSlotRepository slotRepository,
                            ReservationRepository reservationRepository,
                            SlotService slotService) {
        this.locationRepository = locationRepository;
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
        this.slotService = slotService;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse dashboard() {
        long totalLocations = locationRepository.count();
        List<ParkingSlot> activeSlots = slotRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .toList();
        long totalSlots = activeSlots.size();

        long occupied = 0, reserved = 0, available = 0;
        for (ParkingSlot slot : activeSlots) {
            SlotStatus status = slotService.deriveStatus(slot.getId());
            switch (status) {
                case OCCUPIED -> occupied++;
                case RESERVED -> reserved++;
                case AVAILABLE -> available++;
            }
        }
        double occupancyPercent = totalSlots == 0 ? 0.0
                : Math.round(((reserved + occupied) * 1000.0 / totalSlots)) / 10.0;

        LocalDate today = LocalDate.now();
        List<Reservation> todays = reservationRepository.findCreatedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long totalReservationsToday = todays.size();
        // Revenue today: sum fee of today's reservations excluding CANCELLED (docs/API.md §6).
        BigDecimal revenueToday = todays.stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED && r.getFeeAmount() != null)
                .map(Reservation::getFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardDtos.DashboardResponse(totalLocations, totalSlots, available, reserved, occupied,
                occupancyPercent, totalReservationsToday, revenueToday);
    }
}
