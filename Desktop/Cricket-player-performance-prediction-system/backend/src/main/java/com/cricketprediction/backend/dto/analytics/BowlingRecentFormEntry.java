package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row in the recent-form bowling time-series.
 * Date and wickets/runs from a single match bowling spell.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BowlingRecentFormEntry {
    private String date;
    private String opposition;
    private String venue;
    private String format;
    /** Overs bowled, stored exactly as in CSV (e.g. 4.3 = 4 overs + 3 balls). */
    private Double overs;
    private Integer runsConceded;
    private Integer wickets;
    /** Economy rate from CSV (runs / decimal overs). */
    private Double economy;
    /** Formatted bowling figures, e.g. "3/24". */
    private String figures;
    /** Haul badge: "5W", "4W", or null. */
    private String haulBadge;
    /** Legal balls bowled in this match spell. */
    private Integer validBalls;
}
