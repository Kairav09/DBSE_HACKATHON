package com.smartparking.service;

import com.smartparking.dto.ReviewDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.Review;
import com.smartparking.model.Role;
import com.smartparking.model.User;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** v3 reviews: only own COMPLETED reservations, exactly once. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ReviewService reviewService;

    private User user;
    private ParkingLocation location;
    private ParkingSlot slot;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user1@example.com", "hash", "9999999999", Role.USER);
        user.setId(1L);
        location = new ParkingLocation("KL University Main Gate Parking",
                "Bachupally Campus, Main Gate", "Hyderabad", new BigDecimal("30.00"), true);
        location.setId(1L);
        slot = new ParkingSlot(location, "A-01", VehicleType.FOUR_WHEELER, true);
        slot.setId(11L);
        vehicle = new Vehicle(user, "TS09AB1234", VehicleType.FOUR_WHEELER, "Hyundai Creta", "White");
        vehicle.setId(5L);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Reservation fixture(Long id, ReservationStatus status, User owner) {
        Reservation r = new Reservation(owner, vehicle, slot,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1).plusHours(2),
                status, new BigDecimal("60.00"));
        r.setId(id);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void create_completedOwnReservation_succeeds() {
        fixture(21L, ReservationStatus.COMPLETED, user);
        when(reviewRepository.existsByReservationId(21L)).thenReturn(false);
        ReviewDtos.ReviewResponse res = reviewService.create(new ReviewDtos.ReviewRequest(21L, 5, "Great"));
        assertEquals(5, res.rating());
        assertEquals("Test User", res.userName());
        assertEquals("Great", res.comment());
    }

    @Test
    void create_nonCompletedReservation_rejected400() {
        fixture(22L, ReservationStatus.RESERVED, user);
        ApiException ex = assertThrows(ApiException.class,
                () -> reviewService.create(new ReviewDtos.ReviewRequest(22L, 4, "Too early")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    @Test
    void create_duplicateReview_rejected409() {
        fixture(23L, ReservationStatus.COMPLETED, user);
        when(reviewRepository.existsByReservationId(23L)).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> reviewService.create(new ReviewDtos.ReviewRequest(23L, 4, "Again")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void create_someoneElsesReservation_rejected403() {
        User other = new User("Other", "other@example.com", "hash", "8888888888", Role.USER);
        other.setId(2L);
        fixture(24L, ReservationStatus.COMPLETED, other);
        ApiException ex = assertThrows(ApiException.class,
                () -> reviewService.create(new ReviewDtos.ReviewRequest(24L, 4, "Not mine")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
