package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.external.PredictionHistoryItemDto;
import com.cricketprediction.backend.dto.external.PredictionMetadataDto;
import com.cricketprediction.backend.dto.external.PredictionRequestDto;
import com.cricketprediction.backend.dto.external.PredictionResponseDto;
import com.cricketprediction.backend.service.PredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes AI performance prediction endpoints.
 * Secured by the existing JWT filter (applies to all /api/** paths).
 */
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping
    public ResponseEntity<PredictionResponseDto> predict(@Valid @RequestBody PredictionRequestDto request) {
        PredictionResponseDto response = predictionService.predict(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<PredictionHistoryItemDto>> getHistory() {
        List<PredictionHistoryItemDto> history = predictionService.getPredictionHistory();
        return ResponseEntity.ok(history);
    }

    @GetMapping("/meta")
    public ResponseEntity<PredictionMetadataDto> getMetadata() {
        PredictionMetadataDto meta = predictionService.getPredictionMetadata();
        return ResponseEntity.ok(meta);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }
}
