package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BowlingDistribution
 *
 * Frequency breakdown of wickets taken per spell (0, 1, 2, 3, 4+ wickets)
 * with evidence-based pattern interpretation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BowlingDistribution {
    private int zeroWickets;     // 0 wickets
    private int oneWicket;       // 1 wicket
    private int twoWickets;      // 2 wickets
    private int threeWickets;    // 3 wickets
    private int fourPlusWickets; // 4+ wickets
    private Double zeroWicketsPct;
    private Double oneWicketPct;
    private Double twoWicketsPct;
    private Double threeWicketsPct;
    private Double fourPlusWicketsPct;
    private String patternInterpretation;
}
