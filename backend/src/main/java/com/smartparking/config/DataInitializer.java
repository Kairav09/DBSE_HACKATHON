package com.smartparking.config;

import com.smartparking.model.*;
import com.smartparking.repository.*;
import com.smartparking.service.ReservationService;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-profile seed (H2 only). Mirrors database/seed.sql:
 * 1 ADMIN, 2 demo users, 2 locations, 16 slots per location,
 * vehicles, and reservations covering RESERVED / OCCUPIED / COMPLETED
 * across today and the past 7 days at varied hours.
 */
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ParkingLocationRepository locationRepository;
    private final ParkingSlotRepository slotRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    /** Every reservation saved by this seed, so v3 payments/reviews can attach to them. */
    private final java.util.List<Reservation> seededReservations = new ArrayList<>();

    public DataInitializer(UserRepository userRepository,
                           ParkingLocationRepository locationRepository,
                           ParkingSlotRepository slotRepository,
                           VehicleRepository vehicleRepository,
                           ReservationRepository reservationRepository,
                           PromoCodeRepository promoCodeRepository,
                           PaymentRepository paymentRepository,
                           ReviewRepository reviewRepository,
                           PasswordEncoder passwordEncoder,
                           EntityManager entityManager) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.slotRepository = slotRepository;
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.paymentRepository = paymentRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        User admin = userRepository.save(new User("Parking Admin", "admin@smartparking.local",
                passwordEncoder.encode("admin123"), "9000000001", Role.ADMIN));
        User user1 = userRepository.save(new User("Aarav Sharma", "user1@example.com",
                passwordEncoder.encode("user123"), "9876543210", Role.USER));
        User user2 = userRepository.save(new User("Priya Reddy", "user2@example.com",
                passwordEncoder.encode("user123"), "9876543211", Role.USER));

        ParkingLocation loc1 = locationRepository.save(new ParkingLocation(
                "KL University Main Gate Parking", "Bachupally Campus, Main Gate", "Hyderabad",
                new BigDecimal("30.00"), true));
        ParkingLocation loc2 = locationRepository.save(new ParkingLocation(
                "City Center Mall Parking", "Road No. 12, Banjara Hills", "Hyderabad",
                new BigDecimal("50.00"), true));

        List<ParkingSlot> slots1 = createSlots(loc1, "A");
        List<ParkingSlot> slots2 = createSlots(loc2, "B");

        Vehicle v1 = vehicleRepository.save(new Vehicle(user1, "TS09AB1234", VehicleType.FOUR_WHEELER, "Hyundai Creta", "White"));
        Vehicle v2 = vehicleRepository.save(new Vehicle(user1, "TS09CD5678", VehicleType.TWO_WHEELER, "Honda Activa", "Black"));
        Vehicle v3 = vehicleRepository.save(new Vehicle(user2, "TS08EF9012", VehicleType.FOUR_WHEELER, "Maruti Swift", "Red"));
        Vehicle v4 = vehicleRepository.save(new Vehicle(user2, "TS07GH3456", VehicleType.EV, "Tata Nexon EV", "Blue"));

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // --- Today: OCCUPIED (parked right now) ---
        saveReservation(user1, v1, slots1.get(0), now.minusHours(1), now.plusHours(2),
                ReservationStatus.OCCUPIED, loc1, now.minusMinutes(50), null);
        saveReservation(user2, v3, slots2.get(0), now.minusHours(2), now.plusHours(1),
                ReservationStatus.OCCUPIED, loc2, now.minusMinutes(110), null);

        // --- Today: RESERVED (upcoming) ---
        saveReservation(user1, v2, slots1.get(4),
                today.atTime(17, 0).isAfter(now) ? today.atTime(17, 0) : now.plusHours(2),
                today.atTime(17, 0).isAfter(now) ? today.atTime(19, 0) : now.plusHours(4),
                ReservationStatus.RESERVED, loc1, null, null);
        saveReservation(user2, v4, slots2.get(5), now.plusHours(1), now.plusHours(3),
                ReservationStatus.RESERVED, loc2, null, null);
        saveReservation(user1, v1, slots1.get(8), now.plusHours(3), now.plusHours(5),
                ReservationStatus.RESERVED, loc1, null, null);

        // --- Today: COMPLETED (earlier) ---
        saveReservation(user2, v3, slots1.get(2), today.atTime(7, 0), today.atTime(9, 0),
                ReservationStatus.COMPLETED, loc1, today.atTime(7, 5), today.atTime(8, 55));
        saveReservation(user1, v1, slots2.get(3), today.atTime(8, 0), today.atTime(10, 0),
                ReservationStatus.COMPLETED, loc2, today.atTime(8, 2), today.atTime(9, 50));

        // --- Past 7 days: varied hours so dashboard history + prediction have data ---
        User[] users = {user1, user2};
        Vehicle[] vehicles = {v1, v3};
        int[] hours = {8, 9, 12, 17, 18};
        for (int day = 1; day <= 7; day++) {
            LocalDate date = today.minusDays(day);
            for (int i = 0; i < hours.length; i++) {
                int slotIdx = (day * 3 + i * 2) % slots1.size();
                LocalDateTime start = date.atTime(hours[i], 0);
                saveReservation(users[i % 2], vehicles[i % 2], slots1.get(slotIdx),
                        start, start.plusHours(2), ReservationStatus.COMPLETED, loc1,
                        start.plusMinutes(3), start.plusHours(2).minusMinutes(4));
            }
            // A couple per day at location 2 as well
            for (int i = 0; i < 2; i++) {
                int slotIdx = (day + i * 5) % slots2.size();
                LocalDateTime start = date.atTime(hours[(i + 2) % hours.length], 0);
                saveReservation(users[(i + 1) % 2], vehicles[(i + 1) % 2], slots2.get(slotIdx),
                        start, start.plusHours(2), ReservationStatus.COMPLETED, loc2,
                        start.plusMinutes(2), start.plusHours(2).minusMinutes(5));
            }
        }

        seedV3Data(loc1, loc2);
    }

    /** v3 seed: promo codes, one payment per reservation, and a few reviews. */
    private void seedV3Data(ParkingLocation loc1, ParkingLocation loc2) {
        promoCodeRepository.save(new PromoCode("WELCOME10", PromoType.PERCENT,
                new BigDecimal("10.00"), 1000, null, true));
        promoCodeRepository.save(new PromoCode("FLAT50", PromoType.FLAT,
                new BigDecimal("50.00"), 1000, null, true));

        for (Reservation r : seededReservations) {
            // Seeded bookings predate surge/promo pricing: base = fee, multiplier 1.00, discount 0.
            r.setBaseAmount(r.getFeeAmount());
            r.setPricingMultiplier(new BigDecimal("1.00"));
            r.setDiscountAmount(BigDecimal.ZERO);
            reservationRepository.save(r);

            Payment payment = new Payment(r, r.getFeeAmount(), PaymentStatus.PENDING);
            if (r.getStatus() == ReservationStatus.COMPLETED || r.getStatus() == ReservationStatus.OCCUPIED) {
                payment.setStatus(PaymentStatus.PAID);
                payment.setMethod("SIMULATED_CARD");
                payment.setTransactionRef(String.format("TXN-SEED%04d", r.getId()));
                payment.setPaidAt(r.getStatus() == ReservationStatus.COMPLETED ? r.getExitTime() : r.getEntryTime());
            }
            paymentRepository.save(payment);
        }

        // Reviews on COMPLETED reservations (one per reservation): 2 per location.
        java.util.List<Reservation> completedLoc1 = seededReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.COMPLETED
                        && r.getSlot().getLocation().getId().equals(loc1.getId())).toList();
        java.util.List<Reservation> completedLoc2 = seededReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.COMPLETED
                        && r.getSlot().getLocation().getId().equals(loc2.getId())).toList();
        if (completedLoc1.size() > 0) saveReview(completedLoc1.get(0), 5, "Smooth entry, clean and well-lit bays.");
        if (completedLoc1.size() > 1) saveReview(completedLoc1.get(1), 4, "Good experience overall, fills up fast in the evening.");
        if (completedLoc2.size() > 0) saveReview(completedLoc2.get(0), 4, "Convenient location, easy exit.");
        if (completedLoc2.size() > 1) saveReview(completedLoc2.get(1), 5, "Plenty of EV slots, will use again.");
    }

    private void saveReview(Reservation r, int rating, String comment) {
        reviewRepository.save(new Review(r, r.getUser(), r.getSlot().getLocation(), rating, comment));
    }

    private List<ParkingSlot> createSlots(ParkingLocation location, String prefix) {
        List<ParkingSlot> slots = new ArrayList<>();
        VehicleType[] types = {
                VehicleType.FOUR_WHEELER, VehicleType.FOUR_WHEELER, VehicleType.FOUR_WHEELER, VehicleType.FOUR_WHEELER,
                VehicleType.TWO_WHEELER, VehicleType.TWO_WHEELER, VehicleType.TWO_WHEELER, VehicleType.TWO_WHEELER,
                VehicleType.EV, VehicleType.EV, VehicleType.EV,
                VehicleType.ACCESSIBLE, VehicleType.ACCESSIBLE,
                VehicleType.GENERAL, VehicleType.GENERAL, VehicleType.GENERAL
        };
        for (int i = 0; i < types.length; i++) {
            String code = prefix + "-" + String.format("%02d", i + 1);
            slots.add(slotRepository.save(new ParkingSlot(location, code, types[i], true)));
        }
        return slots;
    }

    private void saveReservation(User user, Vehicle vehicle, ParkingSlot slot,
                                 LocalDateTime start, LocalDateTime end,
                                 ReservationStatus status, ParkingLocation location,
                                 LocalDateTime entry, LocalDateTime exit) {
        Reservation r = new Reservation(user, vehicle, slot, start, end, status,
                ReservationService.computeFee(start, end, location.getHourlyRate()));
        r.setEntryTime(entry);
        r.setExitTime(exit);
        reservationRepository.save(r);
        seededReservations.add(r);
        // @CreationTimestamp stamps created_at=now; backdate seeded history so the
        // dashboard's "created today" aggregates are realistic (seed.sql sets this directly).
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE reservations SET created_at = :created, updated_at = :created WHERE id = :id")
                .setParameter("created", start.minusMinutes(10))
                .setParameter("id", r.getId())
                .executeUpdate();
    }
}
