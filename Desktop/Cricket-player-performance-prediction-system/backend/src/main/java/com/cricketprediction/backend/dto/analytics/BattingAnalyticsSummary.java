package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BattingAnalyticsSummary
 *
 * Detailed aggregate batting metrics for a single player in a given format.
 * All rates and percentages are computed with verified cricket denominators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattingAnalyticsSummary {
    private int innings;
    private int totalRuns;
    private int ballsFaced;
    private int notOuts;
    private int dismissals;
    private Integer highestScore;
    private Boolean highestScoreNotOut;
    private Double runsPerInnings;
    private Double battingAverage;          // runs / dismissals (null if 0 dismissals)
    private Double strikeRate;              // runs / ballsFaced * 100 (null if 0 balls)
    private int fours;
    private int sixes;
    private int boundaryRuns;               // 4*fours + 6*sixes
    private Double boundaryPercentage;      // boundaryRuns / totalRuns * 100
    private Double boundaryBallPercentage;  // (fours + sixes) / ballsFaced * 100
    private int fifties;
    private int hundreds;
    private Double stdDevRuns;
    private Double coefficientOfVariation;  // (stdDev / runsPerInnings) * 100
    private String consistencyNote;
    private String sampleSizeWarning;
}
