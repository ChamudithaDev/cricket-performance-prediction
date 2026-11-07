package com.cricketprediction.backend.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * One row in the recent-form batting time-series.
 * Date and runs from a single match innings.
 */
@Data
@Builder
public class RecentFormEntry {
    private String date;
    private String opposition;
    private String venue;
    private String format;
    private Integer runs;
    private Integer ballsFaced;
    private Double strikeRate;
    private Integer fours;
    private Integer sixes;
    private Boolean notOut;
}
