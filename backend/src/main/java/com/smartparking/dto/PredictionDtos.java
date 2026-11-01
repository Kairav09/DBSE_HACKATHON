package com.smartparking.dto;

import java.time.LocalDate;
import java.util.List;

public class PredictionDtos {

    public record HourlyPrediction(int hour, double predictedOccupancyPercent) {}

    public record PredictionResponse(
            Long locationId,
            String locationName,
            LocalDate date,
            int peakHour,
            double peakOccupancyPercent,
            List<HourlyPrediction> hourlyPredictions
    ) {}
}
