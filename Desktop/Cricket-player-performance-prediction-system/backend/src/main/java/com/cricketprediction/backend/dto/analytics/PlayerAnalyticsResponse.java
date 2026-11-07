package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Complete analytics response for a player.
 * All fields are derived from historical batting_performance.csv and
 * bowling_performance.csv — no data is invented.
 *
 * The response includes:
 *  - player role (BATSMAN / BOWLER / ALL_ROUNDER / UNKNOWN)
 *  - format-filtered analytics (selectedFormat)
 *  - data coverage metadata
 *  - role-appropriate recent form (batting or bowling or both)
 *  - format breakdown, venue and opposition breakdowns
 *  - battingSummary and bowlingSummary with consistency and boundary calculations
 *  - scoreDistribution and bowlingDistribution milestone patterns
 *  - recentFormSummary for the requested recentN window
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAnalyticsResponse {

    /** Exact player name as it appears in the CSV files. */
    private String playerName;

    /** Detected role: BATSMAN, BOWLER, ALL_ROUNDER, or UNKNOWN. */
    private String role;

    /** Format currently shown (ODI / T20I / TEST / ALL). */
    private String selectedFormat;

    // ── Coverage metadata ─────────────────────────────────────────────────────

    private DataCoverage coverage;

    // ── Totals (all formats, for header display) ──────────────────────────────

    /** Total batting innings in the dataset across all formats. */
    private int totalBattingInnings;

    /** Total bowling innings in the dataset across all formats. */
    private int totalBowlingInnings;

    // ── Batting analytics (format-filtered) ───────────────────────────────────

    /** Chronological list of the last N batting innings (format-filtered). */
    private List<RecentFormEntry> recentBattingForm;

    // ── Bowling analytics (format-filtered) ───────────────────────────────────

    /** Chronological list of the last N bowling innings (format-filtered). */
    private List<BowlingRecentFormEntry> recentBowlingForm;

    // ── Format breakdown (all formats) ────────────────────────────────────────

    /** Per-format aggregated stats. */
    private List<FormatStats> formatBreakdown;

    // ── Venue/opposition (format-filtered) ───────────────────────────────────

    /** Top batting venues — only populated for BATSMAN / ALL_ROUNDER. */
    private List<VenueStats> venueBreakdown;

    /** Per-opposition batting stats — only populated for BATSMAN / ALL_ROUNDER. */
    private List<OppositionStats> oppositionBreakdown;

    /** Top bowling venues — only populated for BOWLER / ALL_ROUNDER. */
    private List<BowlingVenueStats> bowlingVenueBreakdown;

    /** Per-opposition bowling stats — only populated for BOWLER / ALL_ROUNDER. */
    private List<BowlingOppositionStats> bowlingOppositionBreakdown;

    // ── Single Player Analytics Enhancements ─────────────────────────────────

    /** Comprehensive format-filtered batting analytics summary. */
    private BattingAnalyticsSummary battingSummary;

    /** Comprehensive format-filtered bowling analytics summary. */
    private BowlingAnalyticsSummary bowlingSummary;

    /** Format-filtered score distribution & milestone conversion pattern. */
    private ScoreDistribution scoreDistribution;

    /** Format-filtered bowling wicket distribution pattern. */
    private BowlingDistribution bowlingDistribution;

    /** Recent form window summary (mean, variability, trend) for the requested recentN. */
    private SinglePlayerRecentFormSummary recentFormSummary;
}

