package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated bowling stats against a specific opposition team.
 */
@Data
@Builder
public class BowlingOppositionStats {
    private String opposition;
    /** Number of bowling appearances against this opposition. */
    private Integer appearances;
    private Integer totalWickets;
    private Integer totalRunsConceded;
    /** True decimal overs. */
    private Double totalOvers;
    /** Bowling average = totalRunsConceded / totalWickets, null if wickets == 0. */
    private Double bowlingAverage;
    /** Economy rate = totalRunsConceded / totalOvers, null if overs == 0. */
    private Double economyRate;
}
