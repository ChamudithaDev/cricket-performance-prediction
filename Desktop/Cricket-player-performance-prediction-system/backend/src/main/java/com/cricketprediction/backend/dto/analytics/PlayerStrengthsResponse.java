package com.cricketprediction.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStrengthsResponse {
    private String playerName;
    private String selectedFormat;
    private String detectedRole; // BATSMAN, BOWLER, ALL_ROUNDER, UNKNOWN
    private boolean hasBattingMatchups;
    private boolean hasBowlingMatchups;
    private boolean hasPhaseData;
    private Double overallFormatStrikeRate; // format baseline for batter
    private Double overallFormatEconomy;    // format baseline for bowler
    private List<BatterVsBowlerDto> batterMatchups; // bowlers faced by this batter
    private List<BowlerVsBatterDto> bowlerMatchups; // batters bowled to by this bowler
    private List<PhasePerformanceDto> battingPhases;
    private List<PhasePerformanceDto> bowlingPhases;
    private String phaseNotice; // Note explaining phase rules (e.g. Test format notice)
    private int totalBowlersFaced;
    private int totalBattersFaced;
}
