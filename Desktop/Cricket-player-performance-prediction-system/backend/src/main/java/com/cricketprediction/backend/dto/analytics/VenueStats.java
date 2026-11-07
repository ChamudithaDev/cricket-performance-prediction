package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated batting stats for a specific venue.
 */
@Data
@Builder
public class VenueStats {
    private String venue;
    private Integer innings;
    private Integer totalRuns;
    private Double runsPerInnings;
    private Double strikeRate;
    private Integer fifties;
    private Integer hundreds;
}
