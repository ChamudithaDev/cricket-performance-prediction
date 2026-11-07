package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhasePerformanceDto {
    private String discipline; // BATTING or BOWLING
    private String format;
    private String phase; // POWERPLAY, MIDDLE, DEATH
    private int matches;
    private int innings;
    private int balls; // balls faced for batting, legal balls for bowling
    private int runs; // runs scored for batting, runs conceded for bowling
    private int fours;
    private int sixes;
    private int wicketsOrDismissals;
    private int dotBalls;
    private double rateStat; // Strike Rate for batting, Economy Rate for bowling
    private Double avgStat; // Batting Average or Bowling Average (null if 0)
    private double dotPct;
    private double boundaryPct;
    private String sampleSizeBadge; // SUFFICIENT, LIMITED
}
