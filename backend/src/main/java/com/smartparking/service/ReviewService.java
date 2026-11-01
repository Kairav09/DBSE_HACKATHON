package com.smartparking.service;

import com.smartparking.dto.ReviewDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.Review;
import com.smartparking.model.User;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reviews (v3): one per reservation, only by its owner, only once it is COMPLETED. */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentUserService currentUserService;

    public ReviewService(ReviewRepository reviewRepository,
                         ReservationRepository reservationRepository,
                         CurrentUserService currentUserService) {
        this.reviewRepository = reviewRepository;
        this.reservationRepository = reservationRepository;
        this.currentUserService = currentUserService;
    }

    public static ReviewDtos.ReviewResponse toResponse(Review r) {
        return new ReviewDtos.ReviewResponse(
                r.getId(), r.getUser().getFullName(), r.getRating(), r.getComment(), r.getCreatedAt());
    }

    @Transactional
    public ReviewDtos.ReviewResponse create(ReviewDtos.ReviewRequest req) {
        User current = currentUserService.getCurrentUser();
        Reservation reservation = reservationRepository.findById(req.reservationId())
                .orElseThrow(() -> ApiException.notFound("Reservation not found: " + req.reservationId()));
        if (!reservation.getUser().getId().equals(current.getId())) {
            throw ApiException.forbidden("You can only review your own reservations");
        }
        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw ApiException.badRequest("Only a COMPLETED reservation can be reviewed (current status: "
                    + reservation.getStatus() + ")");
        }
        if (reviewRepository.existsByReservationId(reservation.getId())) {
            throw ApiException.conflict("Reservation " + reservation.getId() + " already has a review");
        }
        Review review = new Review(reservation, current, reservation.getSlot().getLocation(),
                req.rating(), req.comment());
        return toResponse(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewDtos.ReviewResponse> listForLocation(Long locationId) {
        return reviewRepository.findByLocationIdOrderByCreatedAtDesc(locationId)
                .stream().map(ReviewService::toResponse).toList();
    }
}
