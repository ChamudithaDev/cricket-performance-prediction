package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Historical data coverage metadata for the selected player + format.
 * All fields are derived from the actual dataset at runtime — nothing is hardcoded.
 */
@Data
@Builder
public class DataCoverage {
    /** Exact player name as resolved from the dataset. */
    private String playerName;

    /** Detected player role: BATSMAN, BOWLER, ALL_ROUNDER, or UNKNOWN. */
    private String role;

    /** Format selected for this query (e.g. ODI, T20I, TEST, ALL). */
    private String selectedFormat;

    // ── Batting coverage ──────────────────────────────────────────────────────

    /** Total batting innings available for this player (all formats). */
    private Integer totalBattingInningsAllFormats;

    /** Batting innings available for the selected format. */
    private Integer battingInningsForFormat;

    /** Number of batting innings included in recent-form analysis. */
    private Integer recentBattingFormCount;

    /** Earliest match date in the batting dataset for this player + format (ISO). */
    private String battingEarliestDate;

    /** Latest match date in the batting dataset for this player + format (ISO). */
    private String battingLatestDate;

    /** Earliest date included in the recent-form batting subset. */
    private String recentBattingFormStartDate;

    // ── Bowling coverage ──────────────────────────────────────────────────────

    /** Total bowling innings available for this player (all formats). */
    private Integer totalBowlingInningsAllFormats;

    /** Bowling innings available for the selected format. */
    private Integer bowlingInningsForFormat;

    /** Number of bowling innings included in recent-form analysis. */
    private Integer recentBowlingFormCount;

    /** Earliest match date in the bowling dataset for this player + format (ISO). */
    private String bowlingEarliestDate;

    /** Latest match date in the bowling dataset for this player + format (ISO). */
    private String bowlingLatestDate;

    /** Earliest date included in the recent-form bowling subset. */
    private String recentBowlingFormStartDate;

    /**
     * Human-readable note when coverage is incomplete
     * (e.g. "Dataset may not cover the player's full career").
     */
    private String coverageNote;
}
