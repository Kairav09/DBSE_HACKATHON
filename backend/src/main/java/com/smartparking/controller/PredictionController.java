package com.smartparking.controller;

import com.smartparking.dto.PredictionDtos;
import com.smartparking.service.PredictionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) { this.predictionService = predictionService; }

    @GetMapping("/occupancy")
    public ResponseEntity<PredictionDtos.PredictionResponse> occupancy(
            @RequestParam Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(predictionService.predict(locationId, date));
    }
}
