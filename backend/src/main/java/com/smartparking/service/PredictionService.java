package com.smartparking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.dto.PredictionDtos;
import com.smartparking.model.ParkingLocation;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Prediction (docs/API.md §7). Phase 2 shape:
 *  1. Try the Python service at ${prediction.service.url} (GET /predict/occupancy).
 *  2. If it is unreachable / errors, fall back gracefully to an in-Java
 *     calculation (hourly occupancy average from historical reservations)
 *     so the endpoint works standalone. Phase 4 wires the real Python service.
 */
@Service
public class PredictionService {

    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final LocationService locationService;
    private final String predictionServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public PredictionService(ParkingSlotRepository slotRepository,
                             ReservationRepository reservationRepository,
                             LocationService locationService,
                             @Value("${prediction.service.url:http://localhost:8000}") String predictionServiceUrl) {
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
        this.locationService = locationService;
        this.predictionServiceUrl = predictionServiceUrl;
    }

    @Transactional(readOnly = true)
    public PredictionDtos.PredictionResponse predict(Long locationId, LocalDate date) {
        ParkingLocation location = locationService.findOrThrow(locationId);
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        PredictionDtos.PredictionResponse fromPython = tryPythonService(location, targetDate);
        if (fromPython != null) return fromPython;

        return javaFallback(location, targetDate);
    }

    private PredictionDtos.PredictionResponse tryPythonService(ParkingLocation location, LocalDate date) {
        try {
            String url = predictionServiceUrl.replaceAll("/$", "")
                    + "/predict/occupancy?locationId=" + location.getId() + "&date=" + date;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.has("hourlyPredictions")) return null;
            List<PredictionDtos.HourlyPrediction> hourly = new ArrayList<>();
            for (JsonNode node : root.get("hourlyPredictions")) {
                hourly.add(new PredictionDtos.HourlyPrediction(
                        node.get("hour").asInt(),
                        node.get("predictedOccupancyPercent").asDouble()));
            }
            int peakHour = root.has("peakHour") ? root.get("peakHour").asInt()
                    : hourly.stream().max((a, b) -> Double.compare(a.predictedOccupancyPercent(), b.predictedOccupancyPercent()))
                        .map(PredictionDtos.HourlyPrediction::hour).orElse(0);
            double peakPct = root.has("peakOccupancyPercent") ? root.get("peakOccupancyPercent").asDouble()
                    : hourly.stream().mapToDouble(PredictionDtos.HourlyPrediction::predictedOccupancyPercent).max().orElse(0.0);
            return new PredictionDtos.PredictionResponse(location.getId(), location.getName(), date, peakHour, peakPct, hourly);
        } catch (Exception e) {
            return null; // graceful fallback to the in-Java calculation
        }
    }

    /**
     * In-Java fallback: for each hour, average over past days of
     * (reservations active in that hour / total slots of the location) x 100.
     * With little/no history, returns a flat low profile rather than failing.
     */
    PredictionDtos.PredictionResponse javaFallback(ParkingLocation location, LocalDate date) {
        long totalSlots = slotRepository.countByLocationId(location.getId());
        List<Reservation> history = reservationRepository
                .findByLocationIdAndStatusNot(location.getId(), ReservationStatus.CANCELLED);

        List<PredictionDtos.HourlyPrediction> hourly = new ArrayList<>();
        int peakHour = 0;
        double peakPct = 0.0;

        for (int hour = 0; hour < 24; hour++) {
            double pct;
            if (totalSlots == 0 || history.isEmpty()) {
                pct = 10.0; // flat low-confidence profile
            } else {
                // Distinct past dates present in history, used as the averaging base.
                long distinctDays = history.stream()
                        .map(r -> r.getStartTime().toLocalDate())
                        .distinct().count();
                if (distinctDays == 0) distinctDays = 1;
                int finalHour = hour;
                long activeCount = history.stream().filter(r -> {
                    LocalDateTime dayHourStart = r.getStartTime().toLocalDate().atTime(finalHour, 0);
                    LocalDateTime dayHourEnd = dayHourStart.plusHours(1);
                    return r.getStartTime().isBefore(dayHourEnd) && r.getEndTime().isAfter(dayHourStart);
                }).count();
                pct = (activeCount / (double) distinctDays) / totalSlots * 100.0;
                pct = Math.min(100.0, Math.round(pct * 10.0) / 10.0);
            }
            hourly.add(new PredictionDtos.HourlyPrediction(hour, pct));
            if (pct > peakPct) {
                peakPct = pct;
                peakHour = hour;
            }
        }
        return new PredictionDtos.PredictionResponse(location.getId(), location.getName(), date, peakHour, peakPct, hourly);
    }
}
