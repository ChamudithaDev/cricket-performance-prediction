package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Format-level aggregated stats (batting + bowling combined).
 * All values are calculated directly from the CSV data — nothing is invented.
 *
 * Bowling metrics:
 *  - bowlingAverage  = totalRunsConceded / totalWickets  (null when wickets == 0)
 *  - economyRate     = totalRunsConceded / totalDecimalOvers (null when overs == 0)
 *  - economy (avg)   = average per-spell economy from CSV column (legacy; keep for compat)
 *
 * Batting metrics:
 *  - runsPerInnings  = totalRuns / battingInnings  (labelled accurately; NOT batting average)
 *  - battingAverage  = totalRuns / dismissals       (null when dismissals data unavailable)
 */
@Data
@Builder
public class FormatStats {
    private String format;

    // ── Batting ───────────────────────────────────────────────────────────────

    private Integer battingInnings;
    private Integer totalRuns;
    /** Runs per innings = totalRuns / battingInnings. Intentionally distinct from batting average. */
    private Double  runsPerInnings;
    private Double  battingStrikeRate;
    private Integer fifties;
    private Integer hundreds;
    private Integer totalFours;
    private Integer totalSixes;

    // ── Bowling ───────────────────────────────────────────────────────────────

    private Integer bowlingInnings;
    private Integer totalWickets;
    private Integer totalRunsConceded;
    /** Sum of true decimal overs across all spells for this format. */
    private Double  totalDecimalOvers;
    /**
     * Bowling average = totalRunsConceded / totalWickets.
     * Null (not 0) when wickets == 0 to avoid misleading "0.00" display.
     */
    private Double  bowlingAverage;
    /**
     * Economy rate = totalRunsConceded / totalDecimalOvers.
     * Null when totalDecimalOvers == 0.
     */
    private Double  economyRate;
    /**
     * Average per-spell economy from CSV 'economy' column (kept for backward compat).
     * Prefer economyRate for display.
     */
    private Double  economy;
    /** Legacy alias — same as bowlingAverage. Kept so existing frontend code still compiles. */
    private Double  runsPerWicket;
    /** Bowling strike rate = totalBallsBowled / totalWickets (null when wickets == 0). */
    private Double  bowlingStrikeRate;
    /** Four-wicket hauls in a single match (wickets == 4). */
    private Integer fourWickets;
    /** Five-or-more-wicket hauls in a single match (wickets >= 5). */
    private Integer fiveWickets;
}
