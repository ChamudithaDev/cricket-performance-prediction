package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.toss.TossDecisionResponse;
import com.cricketprediction.backend.dto.toss.TossVenuesResponse;
import com.cricketprediction.backend.service.TossDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Toss Decision Advisor Controller
 *
 * Exposes endpoints for venue-level toss analysis, win rates, and tactical recommendations.
 * Base path: /api/analytics/toss-decision
 *
 * Endpoints:
 *   GET /api/analytics/toss-decision?venue=...&format=ODI
 *       Calculates historical toss advantage and generates recommendation.
 *
 *   GET /api/analytics/toss-decision/venues?format=ODI
 *       Returns the sorted list of venues available for the selected format.
 *
 *   GET /api/analytics/toss-decision/formats
 *       Returns the supported cricket formats.
 *
 * Requires Bearer JWT authentication (as configured in SecurityConfig).
 */
@RestController
@RequestMapping("/api/analytics/toss-decision")
@RequiredArgsConstructor
public class TossDecisionController {

    private final TossDecisionService tossDecisionService;

    /**
     * Evaluates toss decision advantage for a specific venue and cricket format.
     */
    @GetMapping
    public ResponseEntity<?> getTossDecision(
            @RequestParam String venue,
            @RequestParam(defaultValue = "ODI") String format) {
        try {
            TossDecisionResponse response = tossDecisionService.analyzeTossDecision(venue, format);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Lists venues that have matches in the requested format (or all venues if format is omitted).
     */
    @GetMapping("/venues")
    public ResponseEntity<?> getVenues(@RequestParam(required = false) String format) {
        try {
            List<String> venues = tossDecisionService.getAvailableVenues(format);
            return ResponseEntity.ok(TossVenuesResponse.builder()
                    .format(format != null && !format.isBlank() ? format.toUpperCase() : "ALL")
                    .venues(venues)
                    .totalVenues(venues.size())
                    .build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Returns supported formats for the Toss Decision Advisor.
     */
    @GetMapping("/formats")
    public ResponseEntity<List<String>> getSupportedFormats() {
        return ResponseEntity.ok(List.of("ODI", "T20I", "TEST"));
    }
}
