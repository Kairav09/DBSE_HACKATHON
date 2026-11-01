package com.smartparking.controller;

import com.smartparking.dto.ReviewDtos;
import com.smartparking.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/reviews")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReviewDtos.ReviewResponse> create(@Valid @RequestBody ReviewDtos.ReviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(req));
    }

    @GetMapping("/api/locations/{locationId}/reviews")
    public ResponseEntity<List<ReviewDtos.ReviewResponse>> listForLocation(@PathVariable Long locationId) {
        return ResponseEntity.ok(reviewService.listForLocation(locationId));
    }
}
