package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated bowling stats for a specific venue.
 */
@Data
@Builder
public class BowlingVenueStats {
    private String venue;
    /** Number of bowling appearances at this venue. */
    private Integer appearances;
    private Integer totalWickets;
    private Integer totalRunsConceded;
    /** True decimal overs (4.3 → 4.5 for rate calculations). */
    private Double totalOvers;
    /** Bowling average = totalRunsConceded / totalWickets, null if wickets == 0. */
    private Double bowlingAverage;
    /** Economy rate = totalRunsConceded / totalOvers, null if overs == 0. */
    private Double economyRate;
}
