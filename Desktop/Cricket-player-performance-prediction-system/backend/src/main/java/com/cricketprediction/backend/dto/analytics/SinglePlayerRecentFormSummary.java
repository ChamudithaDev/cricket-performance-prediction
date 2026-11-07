package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SinglePlayerRecentFormSummary
 *
 * Recent form statistics across the requested eligible window (5, 10, or 15 appearances).
 * Contains role-appropriate summaries for Batting, Bowling, or Both.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinglePlayerRecentFormSummary {
    private int recentN;
    private String focusDiscipline; // "BATTING", "BOWLING", "BOTH"
    private String calculationBasis;

    // ── Batting window ──
    private int battingInnings;
    private int totalRuns;
    private Double meanRuns;
    private Double stdDevRuns;
    private Double coefficientOfVariation;
    private Integer highestRuns;
    private Integer lowestRuns;
    private Double windowBattingStrikeRate;
    private String runTrendDirection; // "UPWARD", "DOWNWARD", "STABLE", "INSUFFICIENT_DATA"
    private Double runTrendSlope;
    private String sampleSizeWarning;

    // ── Bowling window ──
    private int bowlingInnings;
    private int totalWickets;
    private int totalRunsConceded;
    private Double totalOvers;
    private int totalValidBalls;
    private Double meanWickets;
    private Double recentEconomyRate;
    private Double recentBowlingAverage;    // null if 0 wickets
    private Double recentBowlingStrikeRate;  // null if 0 wickets
    private int fourWickets;
    private int fiveWickets;
    private Double stdDevWickets;
    private Integer highestWickets;
    private Integer lowestWickets;
    private String wicketTrendDirection;
    private Double wicketTrendSlope;
    private String bowlingSampleSizeWarning;
}
