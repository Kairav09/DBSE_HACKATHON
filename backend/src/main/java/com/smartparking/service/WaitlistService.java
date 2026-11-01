package com.smartparking.service;

import com.smartparking.dto.ReservationDtos;
import com.smartparking.dto.WaitlistDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.NotificationType;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Payment;
import com.smartparking.model.PaymentStatus;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import com.smartparking.model.WaitlistEntry;
import com.smartparking.model.WaitlistStatus;
import com.smartparking.repository.PaymentRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.WaitlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Waitlist (v3). Users queue for a location + window when nothing fits.
 * When a reservation is cancelled, the earliest WAITING entry for that
 * location whose window overlaps the freed window and whose vehicle fits the
 * freed slot is OFFERED the slot (15 minutes to claim — see ScheduledTasks).
 */
@Service
public class WaitlistService {

    private static final List<ReservationStatus> BLOCKING =
            List.of(ReservationStatus.RESERVED, ReservationStatus.OCCUPIED);

    private final WaitlistEntryRepository waitlistRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final LocationService locationService;
    private final VehicleService vehicleService;
    private final PricingService pricingService;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public WaitlistService(WaitlistEntryRepository waitlistRepository,
                           ReservationRepository reservationRepository,
                           PaymentRepository paymentRepository,
                           LocationService locationService,
                           VehicleService vehicleService,
                           PricingService pricingService,
                           NotificationService notificationService,
                           CurrentUserService currentUserService) {
        this.waitlistRepository = waitlistRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.locationService = locationService;
        this.vehicleService = vehicleService;
        this.pricingService = pricingService;
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    public static WaitlistDtos.WaitlistResponse toResponse(WaitlistEntry e) {
        return new WaitlistDtos.WaitlistResponse(
                e.getId(), e.getLocation().getId(), e.getLocation().getName(),
                e.getVehicle().getId(), e.getVehicle().getLicensePlate(),
                e.getStartTime(), e.getEndTime(), e.getStatus().name(),
                e.getOfferedSlot() == null ? null : e.getOfferedSlot().getId(),
                e.getOfferedSlot() == null ? null : e.getOfferedSlot().getSlotCode(),
                e.getCreatedAt());
    }

    /** A vehicle fits a slot when the types match, or the slot is a GENERAL bay. */
    static boolean vehicleFitsSlot(VehicleType vehicleType, VehicleType slotType) {
        return slotType == VehicleType.GENERAL || slotType == vehicleType;
    }

    @Transactional
    public WaitlistDtos.WaitlistResponse join(WaitlistDtos.WaitlistRequest req) {
        User current = currentUserService.getCurrentUser();
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("endTime must be after startTime");
        }
        ParkingLocation location = locationService.findOrThrow(req.locationId());
        Vehicle vehicle = vehicleService.findOrThrow(req.vehicleId());
        if (!vehicle.getUser().getId().equals(current.getId())) {
            throw ApiException.forbidden("Vehicle does not belong to the current user");
        }
        WaitlistEntry entry = new WaitlistEntry(current, location, vehicle, req.startTime(), req.endTime());
        return toResponse(waitlistRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public List<WaitlistDtos.WaitlistResponse> myEntries() {
        User current = currentUserService.getCurrentUser();
        return waitlistRepository.findByUserIdOrderByCreatedAtDesc(current.getId())
                .stream().map(WaitlistService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WaitlistDtos.WaitlistResponse> listForAdmin() {
        return waitlistRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(WaitlistService::toResponse).toList();
    }

    private WaitlistEntry findOwnedOrThrow(Long id) {
        WaitlistEntry e = waitlistRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Waitlist entry not found: " + id));
        User current = currentUserService.getCurrentUser();
        if (!e.getUser().getId().equals(current.getId())) {
            throw ApiException.forbidden("Waitlist entry does not belong to the current user");
        }
        return e;
    }

    @Transactional
    public WaitlistDtos.WaitlistResponse cancel(Long id) {
        WaitlistEntry e = findOwnedOrThrow(id);
        if (e.getStatus() != WaitlistStatus.WAITING && e.getStatus() != WaitlistStatus.OFFERED) {
            throw ApiException.conflict("Only a WAITING or OFFERED entry can be cancelled (current status: " + e.getStatus() + ")");
        }
        e.setStatus(WaitlistStatus.CANCELLED);
        return toResponse(waitlistRepository.save(e));
    }

    /**
     * Claim an OFFERED entry: creates the reservation on the offered slot for
     * the entry's window (overlap re-checked — 409 if it no longer fits).
     */
    @Transactional
    public ReservationDtos.ReservationResponse claim(Long id) {
        WaitlistEntry e = findOwnedOrThrow(id);
        if (e.getStatus() != WaitlistStatus.OFFERED || e.getOfferedSlot() == null) {
            throw ApiException.conflict("Only an OFFERED waitlist entry can be claimed (current status: " + e.getStatus() + ")");
        }
        ParkingSlot slot = e.getOfferedSlot();
        if (reservationRepository.existsOverlapping(slot.getId(), BLOCKING, e.getStartTime(), e.getEndTime())) {
            throw ApiException.conflict("Slot " + slot.getSlotCode()
                    + " is no longer available for the waitlisted window");
        }
        PricingService.PricingResult pricing =
                pricingService.compute(slot, e.getStartTime(), e.getEndTime(), null, false);
        Reservation r = new Reservation(e.getUser(), e.getVehicle(), slot,
                e.getStartTime(), e.getEndTime(), ReservationStatus.RESERVED, pricing.totalAmount());
        r.setBaseAmount(pricing.baseAmount());
        r.setPricingMultiplier(pricing.surgeMultiplier());
        r.setDiscountAmount(pricing.discountAmount());
        Reservation saved = reservationRepository.save(r);
        paymentRepository.save(new Payment(saved, pricing.totalAmount(), PaymentStatus.PENDING));
        e.setStatus(WaitlistStatus.CLAIMED);
        waitlistRepository.save(e);
        notificationService.create(e.getUser(), NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed",
                "Your waitlist claim booked slot " + slot.getSlotCode() + " at "
                        + slot.getLocation().getName() + ". Amount ₹" + pricing.totalAmount().toPlainString() + ".",
                saved.getId());
        return ReservationService.toResponse(saved, PaymentStatus.PENDING.name());
    }

    /**
     * Promotion (contract): offer the freed slot to the earliest WAITING entry
     * for the same location whose window overlaps the freed window and whose
     * vehicle fits, provided the slot is actually free for that entry's window.
     * Called on reservation cancel (and after an offer expires).
     */
    @Transactional
    public void promoteForSlot(ParkingSlot slot, java.time.LocalDateTime freedStart, java.time.LocalDateTime freedEnd) {
        List<WaitlistEntry> queue = waitlistRepository
                .findByLocationIdAndStatusOrderByCreatedAtAsc(slot.getLocation().getId(), WaitlistStatus.WAITING);
        for (WaitlistEntry e : queue) {
            boolean overlapsFreed = e.getStartTime().isBefore(freedEnd) && e.getEndTime().isAfter(freedStart);
            if (!overlapsFreed) continue;
            if (!vehicleFitsSlot(e.getVehicle().getVehicleType(), slot.getVehicleType())) continue;
            if (reservationRepository.existsOverlapping(slot.getId(), BLOCKING, e.getStartTime(), e.getEndTime())) continue;
            e.setStatus(WaitlistStatus.OFFERED);
            e.setOfferedSlot(slot);
            e.setOfferedAt(java.time.LocalDateTime.now());
            waitlistRepository.save(e);
            notificationService.create(e.getUser(), NotificationType.WAITLIST_OFFER,
                    "A parking slot opened up",
                    "Slot " + slot.getSlotCode() + " at " + slot.getLocation().getName()
                            + " is available for your waitlisted window. Claim it within 15 minutes.",
                    e.getId());
            return; // one offer per freed slot
        }
    }
}
