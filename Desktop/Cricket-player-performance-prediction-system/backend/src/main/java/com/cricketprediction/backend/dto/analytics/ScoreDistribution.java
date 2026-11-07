package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ScoreDistribution
 *
 * Scoring distribution breakdown across recognized cricket milestone intervals
 * with empirical milestone conversion pattern interpretation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDistribution {
    private int singleDigits;    // 0 - 9
    private int starts;          // 10 - 29
    private int solidStarts;     // 30 - 49
    private int fifties;         // 50 - 99
    private int centuries;       // 100+
    private Double singleDigitsPct;
    private Double startsPct;
    private Double solidStartsPct;
    private Double fiftiesPct;
    private Double centuriesPct;
    private String patternInterpretation;
}
