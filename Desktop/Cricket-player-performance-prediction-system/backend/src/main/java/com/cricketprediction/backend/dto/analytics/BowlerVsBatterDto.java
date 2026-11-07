package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BowlerVsBatterDto {
    private String batter;
    private String format;
    private int matches;
    private int innings;
    private int legalBalls;
    private int runsConceded;
    private int wickets;
    private double economy;
    private Double bowlingAverage;
    private Double bowlingStrikeRate;
    private int dotBalls;
    private double dotPct;
    private int boundariesConceded;
    private String assessment; // DOMINANT_CONTROL, HIGH_CONTROL, EFFECTIVE, EXPENSIVE, NEUTRAL, LIMITED_DATA
    private String sampleSizeBadge; // SUFFICIENT, LIMITED
    private String firstDate;
    private String lastDate;
}
