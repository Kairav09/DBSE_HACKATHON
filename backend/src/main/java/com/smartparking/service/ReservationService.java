package com.smartparking.service;

import com.smartparking.dto.ReservationDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.NotificationType;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Payment;
import com.smartparking.model.PaymentStatus;
import com.smartparking.model.PromoCode;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.repository.PaymentRepository;
import com.smartparking.repository.PromoCodeRepository;
import com.smartparking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    /** Small grace so a booking starting "right now" is not rejected for clock skew. */
    private static final long START_GRACE_MINUTES = 5;
    private static final List<ReservationStatus> BLOCKING = List.of(ReservationStatus.RESERVED, ReservationStatus.OCCUPIED);

    private final ReservationRepository reservationRepository;
    private final VehicleService vehicleService;
    private final SlotService slotService;
    private final CurrentUserService currentUserService;
    private final PricingService pricingService;
    private final PromoCodeRepository promoCodeRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final WaitlistService waitlistService;

    public ReservationService(ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              SlotService slotService,
                              CurrentUserService currentUserService,
                              PricingService pricingService,
                              PromoCodeRepository promoCodeRepository,
                              PaymentRepository paymentRepository,
                              NotificationService notificationService,
                              WaitlistService waitlistService) {
        this.reservationRepository = reservationRepository;
        this.vehicleService = vehicleService;
        this.slotService = slotService;
        this.currentUserService = currentUserService;
        this.pricingService = pricingService;
        this.promoCodeRepository = promoCodeRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.waitlistService = waitlistService;
    }

    /** Full mapping when the caller already knows the payment status (avoids an extra lookup). */
    public static ReservationDtos.ReservationResponse toResponse(Reservation r, String paymentStatus) {
        return new ReservationDtos.ReservationResponse(
                r.getId(),
                r.getUser().getId(),
                r.getVehicle().getId(),
                r.getSlot().getId(),
                r.getSlot().getLocation().getId(), // read-only convenience — no location_id column on reservations
                r.getVehicle().getLicensePlate(),
                r.getSlot().getSlotCode(),
                r.getSlot().getLocation().getName(),
                r.getStartTime(), r.getEndTime(),
                r.getStatus().name(),
                r.getFeeAmount(),
                r.getBaseAmount(), r.getPricingMultiplier(), r.getDiscountAmount(),
                r.getPromoCode() == null ? null : r.getPromoCode().getCode(),
                paymentStatus,
                r.getEntryTime(), r.getExitTime(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    /** Instance mapping that resolves paymentStatus from the payments table. */
    public ReservationDtos.ReservationResponse toResponse(Reservation r) {
        String paymentStatus = null;
        if (r.getId() != null) {
            paymentStatus = paymentRepository.findByReservationId(r.getId())
                    .map(p -> p.getStatus().name()).orElse(null);
        }
        return toResponse(r, paymentStatus);
    }

    /** Billable hours: ceil of the duration in hours, minimum 1. */
    public static long billableHours(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return Math.max(1L, (long) Math.ceil(minutes / 60.0));
    }

    /** fee = ceil hours (min 1) x hourly_rate, snapshotted at booking time. */
    public static BigDecimal computeFee(LocalDateTime start, LocalDateTime end, BigDecimal hourlyRate) {
        return hourlyRate.multiply(BigDecimal.valueOf(billableHours(start, end)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("endTime must be after startTime");
        }
        if (start.isBefore(LocalDateTime.now().minusMinutes(START_GRACE_MINUTES))) {
            throw ApiException.badRequest("startTime cannot be in the past");
        }
    }

    @Transactional
    public ReservationDtos.ReservationResponse create(ReservationDtos.ReservationRequest req) {
        User current = currentUserService.getCurrentUser();
        validateWindow(req.startTime(), req.endTime());

        Vehicle vehicle = vehicleService.findOrThrow(req.vehicleId());
        if (!vehicle.getUser().getId().equals(current.getId())) {
            throw ApiException.forbidden("Vehicle does not belong to the current user");
        }

        ParkingSlot slot = slotService.findOrThrow(req.slotId());
        if (Boolean.FALSE.equals(slot.getIsActive())) {
            throw ApiException.conflict("Slot " + slot.getSlotCode() + " is inactive");
        }

        // TRANSACTIONAL overlap check + insert (this method is @Transactional):
        // reject if a RESERVED/OCCUPIED reservation for the same slot overlaps [start, end).
        if (reservationRepository.existsOverlapping(slot.getId(), BLOCKING, req.startTime(), req.endTime())) {
            throw ApiException.conflict("Slot " + slot.getSlotCode()
                    + " is already booked for the selected time window");
        }

        // v3: server-side pricing (base x surge - promo discount). An explicitly
        // typed but invalid promo code is a 400 here (quote endpoint soft-fails).
        PricingService.PricingResult pricing =
                pricingService.compute(slot, req.startTime(), req.endTime(), req.promoCode(), true);

        Reservation r = new Reservation(current, vehicle, slot, req.startTime(), req.endTime(),
                ReservationStatus.RESERVED, pricing.totalAmount());
        r.setBaseAmount(pricing.baseAmount());
        r.setPricingMultiplier(pricing.surgeMultiplier());
        r.setDiscountAmount(pricing.discountAmount());
        if (pricing.promoApplied() && pricing.promo() != null) {
            PromoCode promo = pricing.promo();
            promo.setUsedCount(promo.getUsedCount() + 1);
            promoCodeRepository.save(promo);
            r.setPromoCode(promo);
        }
        Reservation saved = reservationRepository.save(r);

        // v3: one PENDING payment row per reservation, amount = final fee.
        paymentRepository.save(new Payment(saved, pricing.totalAmount(), PaymentStatus.PENDING));

        notificationService.create(current, NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed",
                "Slot " + slot.getSlotCode() + " at " + slot.getLocation().getName()
                        + " booked from " + req.startTime() + " to " + req.endTime()
                        + ". Amount ₹" + pricing.totalAmount().toPlainString() + ".",
                saved.getId());

        return toResponse(saved, PaymentStatus.PENDING.name());
    }

    /**
     * v3 reschedule: move a RESERVED reservation to a new window. Overlap check
     * EXCLUDES the reservation itself. Fee is recomputed (base + surge); any
     * promo on the original booking is removed (usedCount given back).
     */
    @Transactional
    public ReservationDtos.ReservationResponse reschedule(Long id, ReservationDtos.RescheduleRequest req) {
        Reservation r = findOwnedOrAdminOrThrow(id);
        if (r.getStatus() != ReservationStatus.RESERVED) {
            throw ApiException.conflict("Only a RESERVED reservation can be rescheduled (current status: " + r.getStatus() + ")");
        }
        validateWindow(req.startTime(), req.endTime());
        ParkingSlot slot = r.getSlot();
        if (reservationRepository.existsOverlappingExcluding(slot.getId(), BLOCKING,
                req.startTime(), req.endTime(), r.getId())) {
            throw ApiException.conflict("Slot " + slot.getSlotCode()
                    + " is already booked for the selected time window");
        }

        if (r.getPromoCode() != null) {
            PromoCode promo = r.getPromoCode();
            promo.setUsedCount(Math.max(0, promo.getUsedCount() - 1));
            promoCodeRepository.save(promo);
            r.setPromoCode(null);
        }

        PricingService.PricingResult pricing =
                pricingService.compute(slot, req.startTime(), req.endTime(), null, false);
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setBaseAmount(pricing.baseAmount());
        r.setPricingMultiplier(pricing.surgeMultiplier());
        r.setDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        r.setFeeAmount(pricing.totalAmount());
        Reservation saved = reservationRepository.save(r);

        // Keep a still-PENDING payment in sync with the recomputed fee.
        paymentRepository.findByReservationId(saved.getId()).ifPresent(p -> {
            if (p.getStatus() == PaymentStatus.PENDING) {
                p.setAmount(pricing.totalAmount());
                paymentRepository.save(p);
            }
        });
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationDtos.ReservationResponse> myReservations(ReservationStatus status) {
        User current = currentUserService.getCurrentUser();
        List<Reservation> list = status == null
                ? reservationRepository.findByUserIdOrderByCreatedAtDesc(current.getId())
                : reservationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(current.getId(), status);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationResponse get(Long id) {
        return toResponse(findOwnedOrAdminOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ReservationDtos.ReservationResponse> listForAdmin(Long locationId, ReservationStatus status, LocalDate date) {
        LocalDateTime dayStart = date == null ? null : date.atStartOfDay();
        LocalDateTime dayEnd = date == null ? null : date.plusDays(1).atStartOfDay();
        return reservationRepository.findForAdmin(locationId, status, dayStart, dayEnd).stream()
                .map(this::toResponse).toList();
    }

    /** Admin CSV export (v3): same filters as listForAdmin, rendered as CSV text. */
    @Transactional(readOnly = true)
    public String exportCsv(Long locationId, ReservationStatus status, LocalDate date) {
        LocalDateTime dayStart = date == null ? null : date.atStartOfDay();
        LocalDateTime dayEnd = date == null ? null : date.plusDays(1).atStartOfDay();
        List<Reservation> rows = reservationRepository.findForAdmin(locationId, status, dayStart, dayEnd);
        StringBuilder sb = new StringBuilder();
        sb.append("id,userEmail,licensePlate,locationName,slotCode,startTime,endTime,status,feeAmount,paymentStatus,createdAt\n");
        for (Reservation r : rows) {
            String paymentStatus = paymentRepository.findByReservationId(r.getId())
                    .map(p -> p.getStatus().name()).orElse("");
            sb.append(r.getId()).append(',')
              .append(csv(r.getUser().getEmail())).append(',')
              .append(csv(r.getVehicle().getLicensePlate())).append(',')
              .append(csv(r.getSlot().getLocation().getName())).append(',')
              .append(csv(r.getSlot().getSlotCode())).append(',')
              .append(r.getStartTime()).append(',')
              .append(r.getEndTime()).append(',')
              .append(r.getStatus().name()).append(',')
              .append(r.getFeeAmount() == null ? "" : r.getFeeAmount().toPlainString()).append(',')
              .append(paymentStatus).append(',')
              .append(r.getCreatedAt()).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) return "";
        return (v.contains(",") || v.contains("\"") || v.contains("\n"))
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** Shared ownership/admin lookup (used by payment + QR flows too). */
    public Reservation findOwnedOrAdminOrThrow(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reservation not found: " + id));
        User current = currentUserService.getCurrentUser();
        if (!r.getUser().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw ApiException.forbidden("Reservation does not belong to the current user");
        }
        return r;
    }

    @Transactional
    public ReservationDtos.ReservationResponse cancel(Long id) {
        Reservation r = findOwnedOrAdminOrThrow(id);
        if (r.getStatus() != ReservationStatus.RESERVED) {
            throw ApiException.conflict("Only a RESERVED reservation can be cancelled (current status: " + r.getStatus() + ")");
        }
        r.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(r);

        // v3: cancelling a PAID reservation refunds its payment.
        boolean refunded = false;
        var paymentOpt = paymentRepository.findByReservationId(saved.getId());
        if (paymentOpt.isPresent() && paymentOpt.get().getStatus() == PaymentStatus.PAID) {
            Payment p = paymentOpt.get();
            p.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(p);
            refunded = true;
        }

        notificationService.create(r.getUser(), NotificationType.RESERVATION_CANCELLED,
                "Reservation cancelled",
                "Reservation #" + saved.getId() + " for slot " + saved.getSlot().getSlotCode() + " at "
                        + saved.getSlot().getLocation().getName() + " was cancelled."
                        + (refunded ? " The payment was refunded." : ""),
                saved.getId());

        // v3: the freed slot may satisfy the waitlist.
        waitlistService.promoteForSlot(saved.getSlot(), saved.getStartTime(), saved.getEndTime());

        return toResponse(saved);
    }

    @Transactional
    public ReservationDtos.ReservationResponse entry(Long id) {
        Reservation r = findOwnedOrAdminOrThrow(id);
        if (r.getStatus() != ReservationStatus.RESERVED) {
            throw ApiException.conflict("Entry is only allowed from RESERVED (current status: " + r.getStatus() + ")");
        }
        r.setStatus(ReservationStatus.OCCUPIED);
        r.setEntryTime(LocalDateTime.now());
        return toResponse(reservationRepository.save(r));
    }

    @Transactional
    public ReservationDtos.ReservationResponse exit(Long id) {
        Reservation r = findOwnedOrAdminOrThrow(id);
        if (r.getStatus() != ReservationStatus.OCCUPIED) {
            throw ApiException.conflict("Exit is only allowed from OCCUPIED (current status: " + r.getStatus() + ")");
        }
        r.setStatus(ReservationStatus.COMPLETED);
        r.setExitTime(LocalDateTime.now());
        // Fee stays based on the booked window (finalised here, unchanged in the MVP — ARCHITECTURE.md §3).
        if (r.getFeeAmount() == null) {
            r.setFeeAmount(computeFee(r.getStartTime(), r.getEndTime(), r.getSlot().getLocation().getHourlyRate()));
        }
        return toResponse(reservationRepository.save(r));
    }
}
