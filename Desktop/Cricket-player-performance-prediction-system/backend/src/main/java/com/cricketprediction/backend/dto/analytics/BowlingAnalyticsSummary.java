package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BowlingAnalyticsSummary
 *
 * Detailed aggregate bowling metrics for a single player in a given format.
 * Rates (economy, average, strike rate) are calculated from verified underlying totals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BowlingAnalyticsSummary {
    private int innings;
    private Double totalOvers;          // decimal overs (e.g. 4.2 ov = 4.333)
    private int validBallsBowled;       // 4.2 ov = 26 balls
    private int runsConceded;
    private int totalWickets;
    private Double wicketsPerInnings;
    private Double economyRate;         // runsConceded / totalOvers
    private Double bowlingAverage;      // runsConceded / totalWickets (null if 0 wickets)
    private Double bowlingStrikeRate;   // validBallsBowled / totalWickets (null if 0 wickets)
    private int fourWickets;
    private int fiveWickets;
    private Double stdDevWickets;
    private String consistencyNote;
    private String sampleSizeWarning;
}
