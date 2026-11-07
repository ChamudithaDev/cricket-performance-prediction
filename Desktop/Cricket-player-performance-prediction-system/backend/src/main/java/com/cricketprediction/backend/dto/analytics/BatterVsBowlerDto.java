package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatterVsBowlerDto {
    private String bowler;
    private String format;
    private int matches;
    private int innings;
    private int ballsFaced;
    private int runs;
    private int fours;
    private int sixes;
    private double strikeRate;
    private int dismissals;
    private int dotBalls;
    private double dotPct;
    private double boundaryPct;
    private String assessment; // DOMINANT, FAVOURABLE, CHALLENGING, VULNERABLE, NEUTRAL, LIMITED_DATA
    private String sampleSizeBadge; // SUFFICIENT, LIMITED
    private String firstDate;
    private String lastDate;
}
