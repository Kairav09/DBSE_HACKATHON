package com.smartparking.service;

import com.smartparking.dto.LocationDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.User;
import com.smartparking.repository.ParkingLocationRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {

    private final ParkingLocationRepository locationRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentUserService currentUserService;
    private final ReviewRepository reviewRepository;

    public LocationService(ParkingLocationRepository locationRepository,
                           ReservationRepository reservationRepository,
                           CurrentUserService currentUserService,
                           ReviewRepository reviewRepository) {
        this.locationRepository = locationRepository;
        this.reservationRepository = reservationRepository;
        this.currentUserService = currentUserService;
        this.reviewRepository = reviewRepository;
    }

    /** Includes v3 review aggregates: averageRating (1dp, null when no reviews) + reviewCount. */
    public LocationDtos.LocationResponse toResponse(ParkingLocation l) {
        long reviewCount = reviewRepository.countByLocationId(l.getId());
        Double average = reviewCount == 0 ? null : reviewRepository.averageRatingByLocationId(l.getId());
        Double averageRounded = average == null ? null : Math.round(average * 10.0) / 10.0;
        return new LocationDtos.LocationResponse(l.getId(), l.getName(), l.getAddress(), l.getCity(),
                l.getHourlyRate(), l.getIsActive(), averageRounded, reviewCount,
                l.getCreatedAt(), l.getUpdatedAt());
    }

    public ParkingLocation findOrThrow(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Parking location not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<LocationDtos.LocationResponse> list() {
        User current = currentUserService.getCurrentUser();
        List<ParkingLocation> locations = currentUserService.isAdmin(current)
                ? locationRepository.findAll()
                : locationRepository.findByIsActiveTrue();
        return locations.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LocationDtos.LocationResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public LocationDtos.LocationResponse create(LocationDtos.LocationRequest req) {
        ParkingLocation l = new ParkingLocation(req.name(), req.address(), req.city(), req.hourlyRate(),
                req.isActive() == null ? Boolean.TRUE : req.isActive());
        return toResponse(locationRepository.save(l));
    }

    @Transactional
    public LocationDtos.LocationResponse update(Long id, LocationDtos.LocationRequest req) {
        ParkingLocation l = findOrThrow(id);
        l.setName(req.name());
        l.setAddress(req.address());
        l.setCity(req.city());
        l.setHourlyRate(req.hourlyRate());
        if (req.isActive() != null) l.setIsActive(req.isActive());
        return toResponse(locationRepository.save(l));
    }

    @Transactional
    public void delete(Long id) {
        ParkingLocation l = findOrThrow(id);
        if (reservationRepository.existsByLocationId(id)) {
            throw ApiException.conflict("Location " + l.getName()
                    + " has slots with reservations and cannot be deleted — deactivate it via isActive instead");
        }
        locationRepository.delete(l);
    }
}
