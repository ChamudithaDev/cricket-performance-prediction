package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated batting stats against a specific opposition team.
 */
@Data
@Builder
public class OppositionStats {
    private String opposition;
    private Integer innings;
    private Integer totalRuns;
    private Double runsPerInnings;
    private Double strikeRate;
    private Integer fifties;
    private Integer hundreds;
}
