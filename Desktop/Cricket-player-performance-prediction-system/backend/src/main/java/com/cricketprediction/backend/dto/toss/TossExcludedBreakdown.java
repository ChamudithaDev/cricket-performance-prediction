package com.cricketprediction.backend.dto.toss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Breakdown of matches excluded from decisive-match win-rate denominators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossExcludedBreakdown {
    private int awardedCount;
    private int noResultCount;
    private int drawCount;
    private int tieCount;
    private int otherIncompleteCount;
    private int totalExcludedCount;
}
