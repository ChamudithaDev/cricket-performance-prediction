package com.cricketprediction.backend.dto.toss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured response for the Toss Decision Advisor endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossDecisionResponse {
    private String venue;
    private String format;
    private String referenceDate;
    private String windowStartDate;
    private String windowEndDate;
    private int windowYears;

    private int totalEligibleMatches;
    private int decisiveMatches;
    private int batFirstWins;
    private int chaseWins;
    private double batFirstWinRate;
    private double chaseWinRate;
    private double percentagePointDifference;

    private TossRecommendation recommendation;
    private String recommendationLabel;
    private String explanation;

    private int minSampleSize;
    private double thresholdPercentagePoints;

    private TossExcludedBreakdown excludedBreakdown;
    private List<String> warnings;
    private List<TossMatchSummaryDto> recentMatches;
}
