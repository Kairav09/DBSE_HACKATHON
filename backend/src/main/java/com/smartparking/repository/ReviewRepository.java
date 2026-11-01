package com.smartparking.repository;

import com.smartparking.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByReservationId(Long reservationId);
    List<Review> findByLocationIdOrderByCreatedAtDesc(Long locationId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.location.id = :locationId")
    Double averageRatingByLocationId(@Param("locationId") Long locationId);

    long countByLocationId(Long locationId);
}
