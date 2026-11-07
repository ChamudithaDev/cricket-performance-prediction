package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Player Comparison Response DTO.
 * Holds complete analytics for both players, the selected format,
 * comparison scope, domain explanation, aggregated comparison summaries,
 * recent form trends, context filters, metric advantage details, and written interpretation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerComparisonResponse {

    /** Resolved name of Player 1. */
    private String player1Name;

    /** Resolved name of Player 2. */
    private String player2Name;

    /** Format compared: ALL, ODI, T20I, or TEST. */
    private String format;

    /**
     * Comparison scope based on player roles:
     * - BOTH: Both players have batting and bowling records (e.g. all-rounders).
     * - BATTING_ONLY: Both players are primarily batsmen.
     * - BOWLING_ONLY: Both players are primarily bowlers.
     * - MIXED_DISCIPLINES: Contrasting primary roles (direct cross-discipline KPIs separated).
     * - NEITHER: Neither player has records in the selected format.
     */
    private String comparisonScope;

    /** Clear human-readable description of the comparison scope and any role contrasts. */
    private String scopeExplanation;

    /** Full analytics for Player 1 (format-filtered). */
    private PlayerAnalyticsResponse player1;

    /** Full analytics for Player 2 (format-filtered). */
    private PlayerAnalyticsResponse player2;

    /** Batting comparison summary across the selected format. */
    private BattingComparisonSummary battingComparison;

    /** Bowling comparison summary across the selected format. */
    private BowlingComparisonSummary bowlingComparison;

    /** Recent form summary (window of last 5, 10, or 15 eligible innings). */
    private RecentFormSummary recentFormSummary;

    /** Contextual information (active filters and common/available oppositions and venues). */
    private ContextInfo contextInfo;

    /**
     * Map indicating which player holds the advantage for each metric:
     * Value is "player1", "player2", "tie", or "na".
     * Maintained for backward compatibility.
     */
    private Map<String, String> metricAdvantages;

    /**
     * Detailed metric advantages including values, difference, percentage difference,
     * and cricket-specific interpretation.
     */
    private Map<String, MetricAdvantageDetail> metricAdvantagesDetailed;

    /** Short evidence-based written interpretation of the head-to-head comparison. */
    private String writtenInterpretation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BattingComparisonSummary {
        private int innings1;
        private int innings2;
        private int totalRuns1;
        private int totalRuns2;
        private int ballsFaced1;
        private int ballsFaced2;
        private int notOuts1;
        private int notOuts2;
        private int dismissals1;
        private int dismissals2;
        /** Highest score recorded in the selected format. */
        private Integer highestScore1;
        private Integer highestScore2;
        private Boolean highestScoreNotOut1;
        private Boolean highestScoreNotOut2;
        /** Runs per innings: totalRuns / innings. Clearly distinguished from official batting average. */
        private Double runsPerInnings1;
        private Double runsPerInnings2;
        /** Official batting average: totalRuns / dismissals (null if dismissals == 0). */
        private Double battingAverage1;
        private Double battingAverage2;
        /** Strike rate: (totalRuns / ballsFaced) * 100. */
        private Double strikeRate1;
        private Double strikeRate2;
        private int fours1;
        private int fours2;
        private int sixes1;
        private int sixes2;
        private int boundaryRuns1;
        private int boundaryRuns2;
        /** Boundary percentage: % of total runs scored via 4s and 6s. */
        private Double boundaryPercentage1;
        private Double boundaryPercentage2;
        /** Boundary ball percentage: % of balls faced hit to boundary. */
        private Double boundaryBallPercentage1;
        private Double boundaryBallPercentage2;
        /** Dot-ball percentage: null when ball-by-ball dot data is unavailable in the summary dataset. */
        private Double dotBallPercentage1;
        private Double dotBallPercentage2;
        private int fifties1;
        private int fifties2;
        private int hundreds1;
        private int hundreds2;
        /** Sample standard deviation of runs per innings. */
        private Double stdDevRuns1;
        private Double stdDevRuns2;
        /** Coefficient of variation (CV% = stdDev / mean * 100). */
        private Double coefficientOfVariation1;
        private Double coefficientOfVariation2;
        /** Explanatory definition and sample size note for consistency metrics. */
        private String consistencyNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BowlingComparisonSummary {
        private int innings1;
        private int innings2;
        private double totalOvers1;
        private double totalOvers2;
        /** Valid balls bowled, converted accurately from overs notation (e.g. 4.2 overs = 26 balls). */
        private int validBallsBowled1;
        private int validBallsBowled2;
        private int runsConceded1;
        private int runsConceded2;
        private int totalWickets1;
        private int totalWickets2;
        private Double wicketsPerInnings1;
        private Double wicketsPerInnings2;
        /** Economy rate: runsConceded / totalDecimalOvers. Lower is better. */
        private Double economyRate1;
        private Double economyRate2;
        /** Bowling average: runsConceded / totalWickets (null if wickets == 0). Lower is better. */
        private Double bowlingAverage1;
        private Double bowlingAverage2;
        /** Bowling strike rate: validBallsBowled / totalWickets (null if wickets == 0). Lower is better. */
        private Double bowlingStrikeRate1;
        private Double bowlingStrikeRate2;
        private int fourWickets1;
        private int fourWickets2;
        private int fiveWickets1;
        private int fiveWickets2;
        /** Maidens bowled: null when maidens are not recorded in the match summary dataset. */
        private Integer maidens1;
        private Integer maidens2;
        /** Sample standard deviation of wickets per innings. */
        private Double stdDevWickets1;
        private Double stdDevWickets2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentFormSummary {
        private int recentN;
        private String calculationBasis;
        /** "BATTING" or "BOWLING" — indicates the primary discipline focus for recent form. */
        private String focusDiscipline;

        // ── Batting Recent Form ──
        private int battingInnings1;
        private int battingInnings2;
        private Double meanRuns1;
        private Double meanRuns2;
        private Double stdDevRuns1;
        private Double stdDevRuns2;
        private Integer highestRuns1;
        private Integer highestRuns2;
        private Integer lowestRuns1;
        private Integer lowestRuns2;
        private String recentAverageRunsAdvantage; // "player1", "player2", "tie", "insufficient_data"
        private Double recentAverageRunsDifference;
        private String sampleSizeWarning1;
        private String sampleSizeWarning2;
        /** Direction of recent form: "UPWARD", "DOWNWARD", "STABLE", or "INSUFFICIENT_DATA". */
        private String runTrendDirection1;
        private String runTrendDirection2;
        private Double runTrendSlope1;
        private Double runTrendSlope2;

        // ── Bowling Recent Form ──
        private int bowlingInnings1;
        private int bowlingInnings2;
        private Integer totalWickets1;
        private Integer totalWickets2;
        private Integer totalRunsConceded1;
        private Integer totalRunsConceded2;
        /** Total decimal overs bowled in the recent window. */
        private Double totalOvers1;
        private Double totalOvers2;
        /** Total legal balls bowled in the recent window. */
        private Integer totalValidBalls1;
        private Integer totalValidBalls2;
        /** Mean wickets per match spell in the recent window. */
        private Double meanWickets1;
        private Double meanWickets2;
        /** Window economy rate: totalRunsConceded / totalDecimalOvers. */
        private Double recentEconomyRate1;
        private Double recentEconomyRate2;
        /** Window bowling average: totalRunsConceded / totalWickets (null if 0 wickets). */
        private Double recentBowlingAverage1;
        private Double recentBowlingAverage2;
        /** Window bowling strike rate: totalValidBalls / totalWickets (null if 0 wickets). */
        private Double recentBowlingStrikeRate1;
        private Double recentBowlingStrikeRate2;
        private Integer fourWickets1;
        private Integer fourWickets2;
        private Integer fiveWickets1;
        private Integer fiveWickets2;
        private Double stdDevWickets1;
        private Double stdDevWickets2;
        private Integer highestWickets1;
        private Integer highestWickets2;
        private Integer lowestWickets1;
        private Integer lowestWickets2;
        private String recentAverageWicketsAdvantage;
        private Double recentAverageWicketsDifference;
        private String bowlingSampleSizeWarning1;
        private String bowlingSampleSizeWarning2;
        private String wicketTrendDirection1;
        private String wicketTrendDirection2;
        private Double wicketTrendSlope1;
        private Double wicketTrendSlope2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextInfo {
        private String oppositionFilter;
        private String venueFilter;
        private List<String> commonOppositions;
        private List<String> commonVenues;
        private List<String> allOppositions;
        private List<String> allVenues;
        private ContextualBattingStats contextualBatting;
        private ContextualBowlingStats contextualBowling;
        private String contextualSampleSizeWarning;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextualBattingStats {
        private int innings1;
        private int innings2;
        private int totalRuns1;
        private int totalRuns2;
        private Double runsPerInnings1;
        private Double runsPerInnings2;
        private Double strikeRate1;
        private Double strikeRate2;
        private Integer highestScore1;
        private Integer highestScore2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextualBowlingStats {
        private int innings1;
        private int innings2;
        private int totalWickets1;
        private int totalWickets2;
        private Double economyRate1;
        private Double economyRate2;
        private Double bowlingAverage1;
        private Double bowlingAverage2;
        private Double bowlingStrikeRate1;
        private Double bowlingStrikeRate2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricAdvantageDetail {
        /** "player1", "player2", "tie", or "na" */
        private String advantage;
        private Double value1;
        private Double value2;
        private Double difference;
        private Double percentageDifference;
        /** "HIGHER_IS_BETTER", "LOWER_IS_BETTER", "LOWER_VARIABILITY" */
        private String criterion;
        private String interpretation;
    }
}
